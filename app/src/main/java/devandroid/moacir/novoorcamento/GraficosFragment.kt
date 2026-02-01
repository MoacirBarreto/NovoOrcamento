package devandroid.moacir.novoorcamento

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.renderer.PieChartRenderer
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.utils.ViewPortHandler
import com.google.android.material.datepicker.MaterialDatePicker
import devandroid.moacir.novoorcamento.database.AppDatabase
import devandroid.moacir.novoorcamento.databinding.FragmentGraficosBinding
import devandroid.moacir.novoorcamento.model.Lancamento
import devandroid.moacir.novoorcamento.model.TipoLancamento
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class GraficosFragment : Fragment() {

    private var _binding: FragmentGraficosBinding? = null
    // Alterado para usar .root explicitamente para evitar conflitos de geração de código
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
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

    // --- TRAVAR ORIENTAÇÃO ---
    override fun onStart() {
        super.onStart()
        // Força a tela a ficar em pé apenas neste fragment
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onStop() {
        super.onStop()
        // Devolve o controle de rotação para o sistema ao sair
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
                val offset = TimeZone.getDefault().getOffset(Date().time)
                dataInicioPersonalizada = dataInicio
                dataFimPersonalizada = dataFim

                val formato = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
                binding.chipPorPeriodoGrafico.text = "${formato.format(Date(dataInicio + offset))} - ${formato.format(Date(dataFim + offset))}"
                carregarDadosGrafico(R.id.chipPorPeriodoGrafico)
            }
        }

        picker.addOnNegativeButtonClickListener { binding.chipMesAtualGrafico.isChecked = true }
        picker.addOnCancelListener { binding.chipMesAtualGrafico.isChecked = true }
        picker.show(parentFragmentManager, "DATE_PICKER_GRAFICOS")
    }

    private fun carregarDadosGrafico(chipId: Int) {
        val calendario = Calendar.getInstance()
        val listaFiltrada: List<Lancamento>

        calendario.set(Calendar.HOUR_OF_DAY, 0)
        calendario.set(Calendar.MINUTE, 0)
        calendario.set(Calendar.SECOND, 0)
        calendario.set(Calendar.MILLISECOND, 0)

        when (chipId) {
            R.id.chipMesAtualGrafico -> {
                binding.chipPorPeriodoGrafico.text = "Por Período"
                calendario.set(Calendar.DAY_OF_MONTH, 1)
                listaFiltrada = db.orcamentoDao().listarLancamentosPorPeriodo(calendario.timeInMillis, Long.MAX_VALUE)
            }
            R.id.chip30DiasGrafico -> {
                binding.chipPorPeriodoGrafico.text = "Por Período"
                calendario.add(Calendar.DAY_OF_YEAR, -30)
                listaFiltrada = db.orcamentoDao().listarLancamentosPorPeriodo(calendario.timeInMillis, Long.MAX_VALUE)
            }
            R.id.chipPorPeriodoGrafico -> {
                listaFiltrada = db.orcamentoDao().listarLancamentosPorPeriodo(dataInicioPersonalizada, dataFimPersonalizada + 86400000)
            }
            else -> listaFiltrada = db.orcamentoDao().listarLancamentos()
        }

        atualizarResumo(listaFiltrada)
        atualizarGraficoBarras(listaFiltrada)
        atualizarGraficoPizza(listaFiltrada)
    }

    private fun atualizarResumo(lista: List<Lancamento>) {
        val totalReceitas = lista.filter { it.tipo == TipoLancamento.RECEITA }.sumOf { it.valor }
        val totalDespesas = lista.filter { it.tipo == TipoLancamento.DESPESA }.sumOf { it.valor }
        val saldo = totalReceitas - totalDespesas
        val formato = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

        binding.txtTotalReceitasGrafico.text = formato.format(totalReceitas)
        binding.txtTotalDespesasGrafico.text = formato.format(totalDespesas)
        binding.txtSaldoFinalGrafico.text = formato.format(saldo)

        val corSaldo = if (saldo >= 0) R.color.green else R.color.red
        binding.txtSaldoFinalGrafico.setTextColor(ContextCompat.getColor(requireContext(), corSaldo))
    }

    private fun configurarGraficoBarrasInicial() {
        val corTexto = obterCorTextoBase()
        binding.barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.textColor = corTexto
            axisLeft.textColor = corTexto

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Receitas", "Despesas"))
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
        }
    }

    private fun atualizarGraficoBarras(lista: List<Lancamento>) {
        val totalReceitas = lista.filter { it.tipo == TipoLancamento.RECEITA }.sumOf { it.valor }
        val totalDespesas = lista.filter { it.tipo == TipoLancamento.DESPESA }.sumOf { it.valor }

        val entries = arrayListOf(
            BarEntry(0f, totalReceitas.toFloat()),
            BarEntry(1f, totalDespesas.toFloat())
        )

        val dataSet = BarDataSet(entries, "Resumo").apply {
            colors = listOf(ContextCompat.getColor(requireContext(), R.color.green), ContextCompat.getColor(requireContext(), R.color.red))
            valueTextSize = 12f
            valueTextColor = obterCorTextoBase()
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value.toDouble())
            }
        }

        binding.barChart.data = BarData(dataSet).apply { barWidth = 0.5f }
        binding.barChart.invalidate()
        binding.barChart.animateY(1000)
    }

    private fun configurarGraficoPizzaInicial() {
        val corTexto = obterCorTextoBase()
        binding.pieChart.apply {
            description.isEnabled = false
            holeRadius = 45f
            setHoleColor(Color.TRANSPARENT)
            setDrawEntryLabels(false)

            setExtraOffsets(20f, 0f, 20f, 0f)
            // Aplica o Renderer customizado para evitar o crash de NullPointerException
            renderer = SafePieChartRenderer(this, animator, viewPortHandler)

            legend.apply {
                textColor = corTexto
                isEnabled = true
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                isWordWrapEnabled = true
                yEntrySpace = 5f
                textSize = 12f
            }
        }
    }

    private fun atualizarGraficoPizza(lista: List<Lancamento>) {
        val apenasDespesas = lista.filter { it.tipo == TipoLancamento.DESPESA }

        if (apenasDespesas.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.centerText = "Sem Despesas"
            return
        } else {
            binding.pieChart.centerText = ""
        }

        val mapaCategorias = db.orcamentoDao().listarCategorias().associate { it.id to it.nome }
        val gastosPorCategoria = apenasDespesas
            .groupBy { it.categoriaID }
            .map { (catId, lancamentos) -> (mapaCategorias[catId] ?: "Outros") to lancamentos.sumOf { it.valor } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        val entries = gastosPorCategoria.map { PieEntry(it.second.toFloat(), it.first) }

        val dataSet = PieDataSet(entries, "").apply {
            val cores = arrayListOf<Int>()
            cores.addAll(ColorTemplate.MATERIAL_COLORS.toList())
            cores.addAll(ColorTemplate.COLORFUL_COLORS.toList())
            cores.addAll(ColorTemplate.JOYFUL_COLORS.toList())
            colors = cores

            sliceSpace = 3f
            valueTextSize = 12f
            valueTextColor = obterCorTextoBase()

            // Valores externos com linhas guia
            xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            valueLinePart1OffsetPercentage = 80f
            valueLinePart1Length = 0.4f
            valueLinePart2Length = 0.4f
            valueLineColor = obterCorTextoBase()

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value.toDouble())
            }
        }

        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.invalidate()
        binding.pieChart.animateY(1400)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun obterCorTextoBase(): Int {
        val mode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (mode == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK
    }
}

// Renderizador customizado movido para fora da classe para melhor organização
class SafePieChartRenderer(chart: PieChart, animator: ChartAnimator, viewPortHandler: ViewPortHandler)
    : PieChartRenderer(chart, animator, viewPortHandler) {

    override fun drawExtras(c: android.graphics.Canvas) {
        try {
            super.drawExtras(c)
        } catch (e: Exception) {
            // Evita o crash se o bitmap interno da biblioteca for nulo durante o desenho
        }
    }
}