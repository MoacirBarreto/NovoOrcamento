package devandroid.moacir.novoorcamento

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.datepicker.MaterialDatePicker
import devandroid.moacir.novoorcamento.database.AppDatabase
import devandroid.moacir.novoorcamento.databinding.FragmentGraficosBinding
import devandroid.moacir.novoorcamento.model.Lancamento
import devandroid.moacir.novoorcamento.model.TipoLancamento
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GraficosFragment : Fragment() {

    private var _binding: FragmentGraficosBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase

    // Variáveis para armazenar o período personalizado
    private var dataInicioPersonalizada: Long = 0L
    private var dataFimPersonalizada: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGraficosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

        configurarGraficoBarrasInicial()
        configurarGraficoPizzaInicial()
        configurarFiltros()
    }

    override fun onResume() {
        super.onResume()
        val chipId = binding.chipGroupFiltrosGrafico.checkedChipId
        if (chipId != View.NO_ID) {
            carregarDadosGrafico(chipId)
        } else {
            binding.chipMesAtualGrafico.isChecked = true
            carregarDadosGrafico(R.id.chipMesAtualGrafico)
        }
    }

    private fun configurarFiltros() {
        binding.chipGroupFiltrosGrafico.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val chipId = checkedIds[0]

            if (chipId == R.id.chipPorPeriodoGrafico) {
                abrirSeletorDeData()
            } else {
                carregarDadosGrafico(chipId)
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
                // Ajuste visual de fuso horário (opcional, igual ao Home)
                val offset = TimeZone.getDefault().getOffset(Date().time)

                dataInicioPersonalizada = dataInicio
                dataFimPersonalizada = dataFim

                // Atualiza texto do botão
                val formato = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
                binding.chipPorPeriodoGrafico.text = "${formato.format(Date(dataInicio + offset))} - ${formato.format(Date(dataFim + offset))}"

                carregarDadosGrafico(R.id.chipPorPeriodoGrafico)
            }
        }

        picker.addOnNegativeButtonClickListener {
            binding.chipMesAtualGrafico.isChecked = true
        }

        picker.addOnCancelListener {
            binding.chipMesAtualGrafico.isChecked = true
        }

        picker.show(parentFragmentManager, "DATE_PICKER_GRAFICOS")
    }

    private fun carregarDadosGrafico(chipId: Int) {
        val calendario = Calendar.getInstance()
        val listaFiltrada: List<Lancamento>

        // Zera horário
        calendario.set(Calendar.HOUR_OF_DAY, 0)
        calendario.set(Calendar.MINUTE, 0)
        calendario.set(Calendar.SECOND, 0)
        calendario.set(Calendar.MILLISECOND, 0)

        when (chipId) {
            R.id.chipMesAtualGrafico -> {
                binding.chipPorPeriodoGrafico.text = "Por Período"
                calendario.set(Calendar.DAY_OF_MONTH, 1)
                val dataInicio = calendario.timeInMillis
                listaFiltrada = db.orcamentoDao().listarLancamentosPorPeriodo(dataInicio, Long.MAX_VALUE)
            }
            R.id.chip30DiasGrafico -> {
                binding.chipPorPeriodoGrafico.text = "Por Período"
                calendario.add(Calendar.DAY_OF_YEAR, -30)
                val dataInicio = calendario.timeInMillis
                listaFiltrada = db.orcamentoDao().listarLancamentosPorPeriodo(dataInicio, Long.MAX_VALUE)
            }
            R.id.chipPorPeriodoGrafico -> {
                // + 1 dia em ms (86400000) no final para pegar o dia completo
                listaFiltrada = db.orcamentoDao().listarLancamentosPorPeriodo(dataInicioPersonalizada, dataFimPersonalizada + 86400000)
            }
            else -> {
                listaFiltrada = db.orcamentoDao().listarLancamentos()
            }
        }

        atualizarGraficoBarras(listaFiltrada)
        atualizarGraficoPizza(listaFiltrada)
    }

    // --- GRÁFICO DE BARRAS (SEM ALTERAÇÕES) ---
    private fun configurarGraficoBarrasInicial() {
        binding.barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Receitas", "Despesas"))
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.setDrawGridLines(true)
        }
    }

    private fun atualizarGraficoBarras(lista: List<Lancamento>) {
        val totalReceitas = lista.filter { it.tipo == TipoLancamento.RECEITA }.sumOf { it.valor }
        val totalDespesas = lista.filter { it.tipo == TipoLancamento.DESPESA }.sumOf { it.valor }

        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(0f, totalReceitas.toFloat()))
        entries.add(BarEntry(1f, totalDespesas.toFloat()))

        val dataSet = BarDataSet(entries, "Resumo").apply {
            val corReceita = ContextCompat.getColor(requireContext(), R.color.green)
            val corDespesa = ContextCompat.getColor(requireContext(), R.color.red)
            colors = listOf(corReceita, corDespesa)
            valueTextSize = 14f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val formato = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
                    return formato.format(value.toDouble())
                }
            }
        }

        val data = BarData(dataSet)
        data.barWidth = 0.5f
        binding.barChart.data = data
        binding.barChart.invalidate()
        binding.barChart.animateY(1000)
    }

    // --- GRÁFICO DE PIZZA (APENAS DESPESAS) ---
    private fun configurarGraficoPizzaInicial() {
        binding.pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 45f
            transparentCircleRadius = 50f
            setHoleColor(Color.TRANSPARENT)
            setDrawCenterText(true)
            centerText = "Despesas"
            setCenterTextSize(16f)
            legend.isEnabled = false
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(11f)
        }
    }

    private fun atualizarGraficoPizza(lista: List<Lancamento>) {
        val despesas = lista.filter { it.tipo == TipoLancamento.DESPESA }

        if (despesas.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.centerText = "Sem Despesas"
            return
        } else {
            binding.pieChart.centerText = "Despesas"
        }

        val mapaCategorias = db.orcamentoDao().listarCategorias().associate { it.id to it.nome }

        val gastosPorCategoria = despesas
            .groupBy { it.categoriaID }
            .map { (catId, lancamentos) ->
                val nomeCategoria = mapaCategorias[catId] ?: "Outros"
                val total = lancamentos.sumOf { it.valor }
                nomeCategoria to total
            }
            .sortedByDescending { it.second }

        val entries = ArrayList<PieEntry>()
        gastosPorCategoria.forEach { (nome, valor) ->
            entries.add(PieEntry(valor.toFloat(), nome))
        }

        val dataSet = PieDataSet(entries, "Categorias").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            sliceSpace = 3f
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val formato = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
                    return formato.format(value.toDouble())
                }
            }
        }

        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.invalidate()
        binding.pieChart.animateY(1400)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
