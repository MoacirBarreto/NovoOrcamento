package devandroid.moacir.novoorcamento

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import devandroid.moacir.novoorcamento.model.Categoria
import devandroid.moacir.novoorcamento.model.Lancamento
import devandroid.moacir.novoorcamento.model.TipoLancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*


class GraficosFragment : Fragment() {

    private var _binding: FragmentGraficosBinding? = null
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

    override fun onStart() {
        super.onStart()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onStop() {
        super.onStop()
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
                val offset = TimeZone.getDefault().getOffset(Date().time).toLong()
                dataInicioPersonalizada = dataInicio
                dataFimPersonalizada = dataFim

                val formato = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
                binding.chipPorPeriodoGrafico.text = "${formato.format(Date(dataInicio + offset))} - ${formato.format(Date(dataFim + offset))}"
                carregarDadosGrafico(R.id.chipPorPeriodoGrafico)
            }
        }

        picker.addOnNegativeButtonClickListener { binding.chipMesAtualGrafico.isChecked = true }
        picker.show(parentFragmentManager, "DATE_PICKER_GRAFICOS")
    }

    private fun carregarDadosGrafico(chipId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val calendario = Calendar.getInstance()
            calendario.set(Calendar.HOUR_OF_DAY, 0)
            calendario.set(Calendar.MINUTE, 0)
            calendario.set(Calendar.SECOND, 0)
            calendario.set(Calendar.MILLISECOND, 0)

            // Buscando dados em segundo plano (IO Thread)
            val (listaFiltrada, categorias) = withContext(Dispatchers.IO) {
                val lista = when (chipId) {
                    R.id.chipMesAtualGrafico -> {
                        calendario.set(Calendar.DAY_OF_MONTH, 1)
                        db.orcamentoDao().listarLancamentosPorPeriodo(calendario.timeInMillis, Long.MAX_VALUE)
                    }
                    R.id.chip30DiasGrafico -> {
                        calendario.add(Calendar.DAY_OF_YEAR, -30)
                        db.orcamentoDao().listarLancamentosPorPeriodo(calendario.timeInMillis, Long.MAX_VALUE)
                    }
                    R.id.chipPorPeriodoGrafico -> {
                        db.orcamentoDao().listarLancamentosPorPeriodo(dataInicioPersonalizada, dataFimPersonalizada + 86400000)
                    }
                    else -> db.orcamentoDao().listarLancamentosSemFlow() // Metodo suspend normal
                }
                val cats = db.orcamentoDao().listarCategorias()
                Pair(lista, cats)
            }

            // Atualizando UI na Main Thread
            if (_binding != null) {
                if (chipId != R.id.chipPorPeriodoGrafico) binding.chipPorPeriodoGrafico.text = "Por Período"

                atualizarResumo(listaFiltrada)
                atualizarGraficoBarras(listaFiltrada)
                atualizarGraficoPizza(listaFiltrada, categorias)
            }
        }
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
            renderer = SafePieChartRenderer(this, animator, viewPortHandler)

            legend.apply {
                textColor = corTexto
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                isWordWrapEnabled = true
                textSize = 12f
            }
        }
    }

    private fun atualizarGraficoPizza(lista: List<Lancamento>, categorias: List<Categoria>) {
        val apenasDespesas = lista.filter { it.tipo == TipoLancamento.DESPESA }

        if (apenasDespesas.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.centerText = "Sem Despesas"
            return
        } else {
            binding.pieChart.centerText = ""
        }

        val mapaCategorias = categorias.associate { it.id to it.nome }
        val gastosPorCategoria = apenasDespesas
            .groupBy { it.categoriaID }
            .map { (catId, lancamentos) -> (mapaCategorias[catId] ?: "Outros") to lancamentos.sumOf { it.valor } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        val entries = gastosPorCategoria.map { PieEntry(it.second.toFloat(), it.first) }

        val dataSet = PieDataSet(entries, "").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList() + ColorTemplate.COLORFUL_COLORS.toList()
            sliceSpace = 3f
            valueTextSize = 12f
            valueTextColor = obterCorTextoBase()
            xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            valueLinePart1Length = 0.4f
            valueLineColor = obterCorTextoBase()

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value.toDouble())
            }
        }

        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.invalidate()
        binding.pieChart.animateY(1400)
    }

    private fun obterCorTextoBase(): Int {
        val mode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (mode == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SafePieChartRenderer(chart: PieChart, animator: ChartAnimator, viewPortHandler: ViewPortHandler)
    : PieChartRenderer(chart, animator, viewPortHandler)