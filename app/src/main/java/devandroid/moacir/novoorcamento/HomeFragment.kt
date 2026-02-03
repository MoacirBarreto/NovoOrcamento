package devandroid.moacir.novoorcamento

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
import devandroid.moacir.novoorcamento.database.AppDatabase
import devandroid.moacir.novoorcamento.databinding.FragmentHomeBinding
import devandroid.moacir.novoorcamento.model.Lancamento
import devandroid.moacir.novoorcamento.model.TipoLancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

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
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Selecione o Período")
        val picker = builder.build()

        picker.addOnPositiveButtonClickListener { selection ->
            val dataInicio = selection.first
            val dataFim = selection.second

            if (dataInicio != null && dataFim != null) {
                dataInicioPersonalizada = dataInicio
                dataFimPersonalizada = dataFim

                val formato = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
                val offset = TimeZone.getDefault().getOffset(Date().time).toLong()

                binding.chipPorPeriodo.text = "${formato.format(Date(dataInicio + offset))} - ${formato.format(Date(dataFim + offset))}"
                carregarLista(R.id.chipPorPeriodo)
            }
        }
        picker.addOnNegativeButtonClickListener { binding.chipMesAtual.isChecked = true }
        picker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun carregarLista(chipId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {val calendario = Calendar.getInstance()
            calendario.set(Calendar.HOUR_OF_DAY, 0)
            calendario.set(Calendar.MINUTE, 0)
            calendario.set(Calendar.SECOND, 0)
            calendario.set(Calendar.MILLISECOND, 0)

            // Explicitly cast the result to List<Lancamento>
            val listaFiltrada = withContext(Dispatchers.IO) {
                when (chipId) {
                    R.id.chipMesAtual -> {
                        calendario.set(Calendar.DAY_OF_MONTH, 1)
                        db.orcamentoDao().listarLancamentosPorPeriodo(calendario.timeInMillis, Long.MAX_VALUE)
                    }
                    R.id.chip30Dias -> {
                        calendario.add(Calendar.DAY_OF_YEAR, -30)
                        db.orcamentoDao().listarLancamentosPorPeriodo(calendario.timeInMillis, Long.MAX_VALUE)
                    }
                    R.id.chipPorPeriodo -> {
                        db.orcamentoDao().listarLancamentosPorPeriodo(dataInicioPersonalizada, dataFimPersonalizada + 86400000)
                    }
                    else -> db.orcamentoDao().listarLancamentos()
                }
            } as List<Lancamento> // <--- ADD THIS CAST HERE

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
                // Exclusão assíncrona
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.orcamentoDao().deletarLancamento(lancamento)
                    }
                    Toast.makeText(requireContext(), "Excluído!", Toast.LENGTH_SHORT).show()
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