package com.moacir.Lume

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.moacir.Lume.database.AppDatabase
import com.moacir.Lume.databinding.FragmentHomeBinding
import com.moacir.Lume.model.Lancamento
import com.moacir.Lume.model.TipoLancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var dataInicioPersonalizada: Long = 0L
    private var dataFimPersonalizada: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        configurarRecyclerView()
        configurarFab()
        configurarFiltros()
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar para a tela, verifica qual filtro está ativo e recarrega
        val chipId = binding.chipGroupFiltros.checkedChipId
        if (chipId == View.NO_ID) {
            binding.chipMesAtual.isChecked = true
        } else {
            carregarLista(chipId)
        }
    }

    private fun configurarRecyclerView() {
        binding.rvLancamentos.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun configurarFab() {
        binding.fabAdicionar.setOnClickListener {
            val intent = Intent(requireContext(), NovoLancamentoActivity::class.java)
            startActivity(intent)
        }
    }

    private fun configurarFiltros() {
        binding.chipGroupFiltros.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val chipId = checkedIds[0]

            if (chipId == R.id.chipPorPeriodo) {
                abrirSeletorDeData()
            } else {
                carregarLista(chipId)
            }
        }
    }

    private fun abrirSeletorDeData() {
        // Cria o seletor de intervalo (Range) profissional
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Selecione o Período")
        val picker = builder.build()

        picker.addOnPositiveButtonClickListener { selection ->
            val dataInicio = selection.first
            val dataFim = selection.second

            if (dataInicio != null && dataFim != null) {
                val offset = TimeZone.getDefault().getOffset(Date().time).toLong()

                dataInicioPersonalizada = dataInicio + offset
                // O fim do período deve ir até o último segundo do dia escolhido
                dataFimPersonalizada = dataFim + offset + 86399999

                val formato = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
                binding.chipPorPeriodo.text = "${formato.format(Date(dataInicioPersonalizada))} - ${
                    formato.format(
                        Date(dataFimPersonalizada)
                    )
                }"

                carregarLista(R.id.chipPorPeriodo)
            }
        }

        picker.addOnNegativeButtonClickListener { binding.chipMesAtual.isChecked = true }
        picker.show(parentFragmentManager, "DATE_PICKER_HOME")
    }

    private fun carregarLista(chipId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val calendario = Calendar.getInstance()
            calendario.set(Calendar.HOUR_OF_DAY, 0)
            calendario.set(Calendar.MINUTE, 0)
            calendario.set(Calendar.SECOND, 0)
            calendario.set(Calendar.MILLISECOND, 0)

            val listaFiltrada = withContext(Dispatchers.IO) {
                val agora = Calendar.getInstance().timeInMillis

                when (chipId) {
                    R.id.chipMesAtual -> {
                        calendario.set(Calendar.DAY_OF_MONTH, 1)
                        db.orcamentoDao()
                            .listarLancamentosPorPeriodo(calendario.timeInMillis, agora)
                    }

                    R.id.chip30Dias -> {
                        val cal30 = Calendar.getInstance()
                        cal30.add(Calendar.DAY_OF_YEAR, -30)
                        cal30.set(Calendar.HOUR_OF_DAY, 0)
                        cal30.set(Calendar.MINUTE, 0)
                        cal30.set(Calendar.SECOND, 0)
                        db.orcamentoDao().listarLancamentosPorPeriodo(cal30.timeInMillis, agora)
                    }

                    R.id.chipPorPeriodo -> {
                        // Soma 86399999ms para pegar até o último milissegundo do dia final
                        db.orcamentoDao().listarLancamentosPorPeriodo(
                            dataInicioPersonalizada,
                            dataFimPersonalizada
                        )
                    }

                    else -> db.orcamentoDao().listarLancamentosSemFlow()
                }
            }

            if (chipId != R.id.chipPorPeriodo) binding.chipPorPeriodo.text = "Por Período"

            atualizarRecyclerView(listaFiltrada)
            atualizarResumo(listaFiltrada)
        }
    }

    private fun atualizarRecyclerView(lista: List<Lancamento>) {
        binding.rvLancamentos.adapter = LancamentoAdapter(
            lista,
            onItemClick = { lancamento -> abrirTelaDeEdicao(lancamento) },
            onItemLongClick = { lancamento -> exibirDialogoDeExclusao(lancamento) }
        )
    }

    private fun atualizarResumo(lista: List<Lancamento>) {
        val totalReceitas = lista.filter { it.tipo == TipoLancamento.RECEITA }.sumOf { it.valor }
        val totalDespesas = lista.filter { it.tipo == TipoLancamento.DESPESA }.sumOf { it.valor }
        val saldo = totalReceitas - totalDespesas

        val formato = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

        binding.txtTotalReceitas.text = formato.format(totalReceitas)
        binding.txtTotalDespesas.text = formato.format(totalDespesas)
        binding.txtSaldoFinal.text = formato.format(saldo)

        // Define a cor do saldo (Verde para positivo, Vermelho para negativo)
        val cor = if (saldo >= 0) R.color.green else R.color.red
        binding.txtSaldoFinal.setTextColor(ContextCompat.getColor(requireContext(), cor))
    }

    private fun abrirTelaDeEdicao(lancamento: Lancamento) {
        val intent = Intent(requireContext(), NovoLancamentoActivity::class.java)
        intent.putExtra("LANCAMENTO_ID", lancamento.id)
        startActivity(intent)
    }

    private fun exibirDialogoDeExclusao(lancamento: Lancamento) {
        AlertDialog.Builder(requireContext())
            .setTitle("Excluir Lançamento")
            .setMessage("Deseja excluir '${lancamento.descricao}'?")
            .setPositiveButton("Sim") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.orcamentoDao().deletarLancamento(lancamento)
                    }
                    Toast.makeText(requireContext(), "Excluído!", Toast.LENGTH_SHORT).show()
                    // Recarrega a lista mantendo o filtro atual
                    carregarLista(binding.chipGroupFiltros.checkedChipId)
                }
            }
            .setNegativeButton("Não", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}