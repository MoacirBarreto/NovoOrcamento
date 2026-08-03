package com.moacir.Lume

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.moacir.Lume.database.AppDatabase
import com.moacir.Lume.database.OrcamentoRepository
import com.moacir.Lume.databinding.FragmentHomeBinding
import com.moacir.Lume.model.Lancamento
import com.moacir.Lume.model.TipoLancamento
import com.moacir.Lume.util.FormatUtils
import com.moacir.Lume.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory(OrcamentoRepository(AppDatabase.getDatabase(requireContext())))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarRecyclerView()
        configurarFab()
        configurarFiltros()
        observarDados()
    }

    private fun observarDados() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.lancamentos.collectLatest { lista ->
                atualizarRecyclerView(lista)
                atualizarResumo(lista)
            }
        }
    }

    private fun restaurarEstadoFiltros() {
        // 1. Marca o chip correto baseado no que foi selecionado em qualquer tela
        binding.chipGroupFiltros.check(ConfiguracoesApp.ultimoChipHome)

        // 2. Atualiza o texto do botão de período
        if (ConfiguracoesApp.temPeriodoPersonalizado()) {
            binding.chipPorPeriodo.text = FormatUtils.formatarPeriodo(
                ConfiguracoesApp.dataInicioGlobal,
                ConfiguracoesApp.dataFimGlobal
            )
        } else {
            binding.chipPorPeriodo.text = "Por Período" // Texto Default
        }
    }

    override fun onResume() {
        super.onResume()
        // Sincroniza o estado visual caso tenha mudado na outra tela
        restaurarEstadoFiltros()

        // Carrega a lista baseada no chip salvo
        viewModel.carregarLancamentos(ConfiguracoesApp.ultimoChipHome)
    }

    private fun configurarFiltros() {
        binding.chipGroupFiltros.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val chipId = checkedIds[0]
            val anterior = ConfiguracoesApp.ultimoChipHome
            ConfiguracoesApp.ultimoChipHome = chipId

            if (chipId == R.id.chipPorPeriodo) {
                // Se mudou para este chip e não tem data definida, abre o seletor
                if (!ConfiguracoesApp.temPeriodoPersonalizado()) {
                    abrirSeletorDeData()
                } else if (anterior != R.id.chipPorPeriodo) {
                    // Se já tem data e veio de outro chip, apenas carrega os dados
                    viewModel.carregarLancamentos(chipId)
                }
            } else {
                viewModel.carregarLancamentos(chipId)
            }
        }

        binding.chipPorPeriodo.setOnClickListener {
            // Se o usuário clicar no chip que JÁ está selecionado, ele quer trocar a data.
            // Isso evita que o OnCheckedStateChangeListener e o Click disparem juntos na primeira seleção.
            if (ConfiguracoesApp.ultimoChipHome == R.id.chipPorPeriodo && ConfiguracoesApp.temPeriodoPersonalizado()) {
                abrirSeletorDeData()
            }
        }
    }

    private fun abrirSeletorDeData() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Selecione o Período")

        if (ConfiguracoesApp.temPeriodoPersonalizado()) {
            val offset = TimeZone.getDefault().getOffset(Date().time).toLong()
            builder.setSelection(
                androidx.core.util.Pair(
                    ConfiguracoesApp.dataInicioGlobal - offset,
                    ConfiguracoesApp.dataFimGlobal - offset
                )
            )
        }

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            val dataInicio = selection.first
            val dataFim = selection.second

            if (dataInicio != null && dataFim != null) {
                val offset = TimeZone.getDefault().getOffset(Date().time).toLong()

                // Salva no Singleton global (compartilhado com GraficosFragment)
                ConfiguracoesApp.dataInicioGlobal = dataInicio + offset
                ConfiguracoesApp.dataFimGlobal = dataFim + offset + 86399999

                binding.chipPorPeriodo.text = FormatUtils.formatarPeriodo(
                    ConfiguracoesApp.dataInicioGlobal,
                    ConfiguracoesApp.dataFimGlobal
                )

                viewModel.carregarLancamentos(R.id.chipPorPeriodo)
            }
        }

        picker.addOnNegativeButtonClickListener {
            // Se cancelou e não tem data salva, volta para o chip padrão "Mês Atual"
            if (!ConfiguracoesApp.temPeriodoPersonalizado()) {
                binding.chipMesAtual.isChecked = true
                ConfiguracoesApp.ultimoChipHome = R.id.chipMesAtual
            }
        }

        picker.show(parentFragmentManager, "DATE_PICKER_HOME")
    }



    private fun configurarRecyclerView() {
        binding.rvLancamentos.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun configurarFab() {
        binding.fabAdicionar.setOnClickListener {
            startActivity(Intent(requireContext(), NovoLancamentoActivity::class.java))
        }
    }

    private fun atualizarRecyclerView(lista: List<Lancamento>) {
        binding.rvLancamentos.adapter = LancamentoAdapter(
            lista,
            onItemClick = { abrirTelaDeEdicao(it) },
            onItemLongClick = { exibirDialogoDeExclusao(it) }
        )
    }

    private fun atualizarResumo(lista: List<Lancamento>) {
        val totalReceitas = lista.filter { it.tipo == TipoLancamento.RECEITA }.sumOf { it.valor }
        val totalDespesas = lista.filter { it.tipo == TipoLancamento.DESPESA }.sumOf { it.valor }
        val saldo = totalReceitas - totalDespesas

        binding.txtTotalReceitas.text = FormatUtils.formatarMoeda(totalReceitas)
        binding.txtTotalDespesas.text = FormatUtils.formatarMoeda(totalDespesas)
        binding.txtSaldoFinal.text = FormatUtils.formatarMoeda(saldo)

        val cor = if (saldo >= 0) {
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_positive)
        } else {
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_negative)
        }
        binding.txtSaldoFinal.setTextColor(cor)
    }

    private fun abrirTelaDeEdicao(lancamento: Lancamento) {
        val intent = Intent(requireContext(), NovoLancamentoActivity::class.java)
        intent.putExtra("LANCAMENTO_ID", lancamento.id)
        startActivity(intent)
    }

    private fun exibirDialogoDeExclusao(lancamento: Lancamento) {
        AlertDialog.Builder(requireContext())
            .setTitle("Excluir")
            .setMessage("Deseja excluir este lançamento?")
            .setPositiveButton("Sim") { _, _ ->
                viewModel.excluirLancamento(lancamento)
            }
            .setNegativeButton("Não", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}