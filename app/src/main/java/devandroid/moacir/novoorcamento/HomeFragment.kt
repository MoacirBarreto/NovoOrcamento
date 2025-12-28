package devandroid.moacir.novoorcamento

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import devandroid.moacir.novoorcamento.database.AppDatabase
import devandroid.moacir.novoorcamento.databinding.FragmentHomeBinding
import devandroid.moacir.novoorcamento.model.Lancamento
import devandroid.moacir.novoorcamento.model.TipoLancamento
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.core.content.ContextCompat

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // Propriedade válida apenas entre onCreateView e onDestroyView
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase

    // Variáveis para armazenar o período personalizado
    private var dataInicioPersonalizada: Long = 0L
    private var dataFimPersonalizada: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
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
        // Recarrega a lista baseada no Chip que estiver marcado
        val chipId = binding.chipGroupFiltros.checkedChipId
        // Se nenhum estiver marcado (caso raro), força o Mês Atual
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
        binding.chipGroupFiltros.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val chipId = checkedIds[0]

            if (chipId == R.id.chipPorPeriodo) {
                // Se o usuário clicar em "Por Período", abrimos o calendário
                abrirSeletorDeData()
            } else {
                // Se for outro filtro (Mês Atual ou 30 Dias), carrega direto
                carregarLista(chipId)
            }
        }
    }

    private fun abrirSeletorDeData() {
        // Cria o DatePicker para selecionar um intervalo (Range)
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Selecione o Período")

        // Ajuste visual para o tema do seu app
        //builder.setTheme(R.style.ThemeOverlay_MaterialComponents_MaterialCalendar)

        val picker = builder.build()

        picker.addOnPositiveButtonClickListener { selection ->
            // selection é um Pair<Long, Long> contendo inicio e fim em UTC
            val dataInicio = selection.first
            val dataFim = selection.second

            if (dataInicio != null && dataFim != null) {
                // Ajuste de fuso horário simples (opcional, mas recomendado para exibir corretamente)
                val offset = TimeZone.getDefault().getOffset(Date().time)
                dataInicioPersonalizada = dataInicio
                dataFimPersonalizada = dataFim

                // Atualiza o texto do Chip para mostrar as datas escolhidas (ex: "01/01 - 15/01")
                val formato = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
                // Adicionamos o offset apenas para visualização correta da string se necessário
                // (Para o banco de dados, usaremos o timestamp bruto ou ajustado conforme sua lógica de salvamento)

                binding.chipPorPeriodo.text =
                    "${formato.format(Date(dataInicio + offset))} - ${formato.format(Date(dataFim + offset))}"

                // Carrega a lista com esse período
                carregarLista(R.id.chipPorPeriodo)
            }
        }

        // Se cancelar, volta para o filtro padrão (Mês Atual) para não ficar inconsistente
        picker.addOnNegativeButtonClickListener {
            binding.chipMesAtual.isChecked = true
        }

        picker.addOnCancelListener {
            binding.chipMesAtual.isChecked = true
        }

        picker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun carregarLista(chipId: Int) {
        val listaFiltrada: List<Lancamento>
        val calendario = Calendar.getInstance()

        // Zera horário para consistência nos cálculos de início do dia
        calendario.set(Calendar.HOUR_OF_DAY, 0)
        calendario.set(Calendar.MINUTE, 0)
        calendario.set(Calendar.SECOND, 0)
        calendario.set(Calendar.MILLISECOND, 0)

        when (chipId) {
            R.id.chipMesAtual -> {
                // Reseta o texto do botão de período
                binding.chipPorPeriodo.text = "Por Período"

                calendario.set(Calendar.DAY_OF_MONTH, 1)
                val dataInicio = calendario.timeInMillis
                listaFiltrada =
                    db.orcamentoDao().listarLancamentosPorPeriodo(dataInicio, Long.MAX_VALUE)
            }

            R.id.chip30Dias -> {
                binding.chipPorPeriodo.text = "Por Período"

                calendario.add(Calendar.DAY_OF_YEAR, -30)
                val dataInicio = calendario.timeInMillis
                listaFiltrada =
                    db.orcamentoDao().listarLancamentosPorPeriodo(dataInicio, Long.MAX_VALUE)
            }

            R.id.chipPorPeriodo -> {
                // Usa as datas que foram salvas no DatePicker
                // Ajustamos o offset aqui também para garantir que pegue o dia todo no fuso local
                val offset = TimeZone.getDefault().getOffset(Date().time)

                // O DatePicker retorna o início do dia em UTC.
                // Para buscar no banco, queremos garantir que pegue o dia local correto.
                // Se você salvou no banco usando System.currentTimeMillis(), essas datas funcionarão bem.

                listaFiltrada = db.orcamentoDao().listarLancamentosPorPeriodo(
                    dataInicioPersonalizada,
                    dataFimPersonalizada + 86400000
                )
                // + 86400000 (1 dia em ms) para garantir que pegue o último dia inteiro selecionado
            }

            else -> {
                listaFiltrada = db.orcamentoDao().listarLancamentos()
            }
        }

        atualizarRecyclerView(listaFiltrada)
        atualizarResumo(listaFiltrada)
    }

    private fun atualizarRecyclerView(lista: List<Lancamento>) {
        val adapter = LancamentoAdapter(
            lista,
            onItemClick = { lancamento -> abrirTelaDeEdicao(lancamento) },
            onItemLongClick = { lancamento -> exibirDialogoDeExclusao(lancamento) }
        )
        binding.rvLancamentos.adapter = adapter
    }

    private fun atualizarResumo(lista: List<Lancamento>) {
        val totalReceitas = lista.filter { it.tipo == TipoLancamento.RECEITA }.sumOf { it.valor }
        val totalDespesas = lista.filter { it.tipo == TipoLancamento.DESPESA }.sumOf { it.valor }
        val saldo = totalReceitas - totalDespesas

        val formato = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

        binding.txtTotalReceitas.text = formato.format(totalReceitas)
        binding.txtTotalDespesas.text = formato.format(totalDespesas)
        binding.txtSaldoFinal.text = formato.format(saldo)

        val context = requireContext()
        if (saldo >= 0) {
            binding.txtSaldoFinal.setTextColor(ContextCompat.getColor(context, R.color.green))
        } else {
            binding.txtSaldoFinal.setTextColor(ContextCompat.getColor(context, R.color.red))
        }
    }

    private fun abrirTelaDeEdicao(lancamento: Lancamento) {
        val intent = Intent(requireContext(), NovoLancamentoActivity::class.java)
        intent.putExtra("LANCAMENTO_ID", lancamento.id)
        startActivity(intent)
    }

    private fun exibirDialogoDeExclusao(lancamento: Lancamento) {
        AlertDialog.Builder(requireContext())
            .setTitle("Excluir Lançamento")
            .setMessage("Tem certeza que deseja excluir '${lancamento.descricao}'?")
            .setPositiveButton("Sim") { _, _ ->
                db.orcamentoDao().deletarLancamento(lancamento)
                // Recarrega mantendo o filtro atual
                carregarLista(binding.chipGroupFiltros.checkedChipId)
            }
            .setNegativeButton("Não", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
