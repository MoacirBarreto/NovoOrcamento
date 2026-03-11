package devandroid.moacir.Lume

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.datepicker.MaterialDatePicker
import devandroid.moacir.Lume.database.AppDatabase
import devandroid.moacir.Lume.databinding.FragmentGraficosBinding
import devandroid.moacir.Lume.model.Categoria
import devandroid.moacir.Lume.model.Lancamento
import devandroid.moacir.Lume.model.SaldoMensal
import devandroid.moacir.Lume.model.TipoLancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GraficosFragment : Fragment() {

    private var _binding: FragmentGraficosBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private var dataInicioPersonalizada = 0L
    private var dataFimPersonalizada = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGraficosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        inicializarGraficos()
        configurarFiltros()
    }

    private fun inicializarGraficos() {
        configurarGraficoBarrasInicial()
        configurarGraficoLinhaInicial()
        configurarGraficoPizzaInicial()
    }

    override fun onResume() {
        super.onResume()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val chipId = binding.chipGroupFiltrosGrafico.checkedChipId
        carregarDadosGrafico(if (chipId != View.NO_ID) chipId else R.id.chipMesAtualGrafico)
    }

    private fun configurarFiltros() {
        binding.chipGroupFiltrosGrafico.setOnCheckedStateChangeListener { _, checkedIds ->
            val chipId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            if (chipId == R.id.chipPorPeriodoGrafico) abrirSeletorDeData()
            else carregarDadosGrafico(chipId)
        }
    }

    private fun carregarDadosGrafico(chipId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val (lista, categorias, evolucao) = withContext(Dispatchers.IO) {
                val cal = Calendar.getInstance()
                val resultado = when (chipId) {
                    R.id.chipMesAtualGrafico -> {
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        db.orcamentoDao().listarLancamentosPorPeriodo(cal.timeInMillis, Long.MAX_VALUE)
                    }
                    R.id.chip30DiasGrafico -> {
                        cal.add(Calendar.DAY_OF_YEAR, -30)
                        db.orcamentoDao().listarLancamentosPorPeriodo(cal.timeInMillis, Long.MAX_VALUE)
                    }
                    R.id.chipPorPeriodoGrafico -> {
                        // Filtra exatamente entre as duas datas selecionadas
                        db.orcamentoDao().listarLancamentosPorPeriodo(dataInicioPersonalizada, dataFimPersonalizada)
                    }
                    else -> db.orcamentoDao().listarLancamentosSemFlow()
                }
                Triple(resultado, db.orcamentoDao().listarCategorias(), db.orcamentoDao().obterEvolucaoSaldo().first())
            }

            _binding?.let {
                atualizarResumo(lista)
                atualizarGraficoBarras(lista)
                atualizarGraficoPizza(lista, categorias)
                atualizarGraficoLinha(evolucao)
            }
        }
    }

    private fun abrirSeletorDeData() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Filtrar Gráficos")
        val picker = builder.build()

        picker.addOnPositiveButtonClickListener { selection ->
            val dataInicio = selection.first
            val dataFim = selection.second

            if (dataInicio != null && dataFim != null) {
                val offset = TimeZone.getDefault().getOffset(Date().time).toLong()


                dataInicioPersonalizada = dataInicio + offset
                dataFimPersonalizada = dataFim + offset + 86399999

                val formato = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
                binding.chipPorPeriodoGrafico.text = "${formato.format(Date(dataInicioPersonalizada))} - ${formato.format(Date(dataFimPersonalizada))}"

                carregarDadosGrafico(R.id.chipPorPeriodoGrafico)
            }
        }

        picker.addOnNegativeButtonClickListener { binding.chipMesAtualGrafico.isChecked = true }
        picker.show(parentFragmentManager, "DATE_PICKER_GRAFICOS")
    }

    // --- LOGICA DE ATUALIZAÇÃO DOS GRÁFICOS ---

    private fun atualizarGraficoLinha(dados: List<SaldoMensal>) {
        if (dados.isEmpty()) { binding.lineChart.clear(); return }

        val entries = mutableListOf<Entry>()
        val labelsX = mutableListOf<String>()
        val coresCirculos = mutableListOf<Int>()

        val corAzul = ContextCompat.getColor(requireContext(), R.color.blue_primary)
        val corVermelha = ContextCompat.getColor(requireContext(), R.color.red)

        val dadosOrdenados = dados.sortedBy { it.mesAno }

        dadosOrdenados.forEachIndexed { index, item ->
            entries.add(Entry(index.toFloat(), item.saldo.toFloat()))
            labelsX.add(formatarMesAno(item.mesAno))
            coresCirculos.add(if (item.saldo >= 0) corAzul else corVermelha)
        }

        val dataSet = LineDataSet(entries, "Saldo Mensal").apply {
            color = corAzul
            circleColors = coresCirculos
            lineWidth = 2.5f
            circleRadius = 5f
            setDrawValues(false)
            setDrawFilled(true)
            fillDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.fade_blue)
            mode = LineDataSet.Mode.LINEAR
        }

        binding.lineChart.apply {
            marker = CustomMarkerView(requireContext(), R.layout.layout_marker_view).also { it.chartView = this }
            xAxis.apply {
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < labelsX.size) labelsX[index] else ""
                    }
                }
                granularity = 1f
                isGranularityEnabled = true
                labelCount = if (labelsX.size > 5) 5 else labelsX.size
            }
            data = LineData(dataSet)
            axisLeft.apply {
                removeAllLimitLines()
                addLimitLine(LimitLine(0f).apply {
                    lineColor = Color.GRAY
                    lineWidth = 1f
                    enableDashedLine(10f, 10f, 0f)
                })
            }
            animateX(800)
            invalidate()
        }
    }

    private fun atualizarGraficoBarras(lista: List<Lancamento>) {
        val receitas = lista.filter { it.tipo == TipoLancamento.RECEITA }.sumOf { it.valor }.toFloat()
        val despesas = lista.filter { it.tipo == TipoLancamento.DESPESA }.sumOf { it.valor }.toFloat()

        val dataSet = BarDataSet(listOf(BarEntry(0f, receitas), BarEntry(1f, despesas)), "").apply {
            colors = listOf(ContextCompat.getColor(requireContext(), R.color.green), ContextCompat.getColor(requireContext(), R.color.red))
            valueFormatter = CurrencyFormatter()
            valueTextColor = obterCorTextoBase()
            valueTextSize = 10f
        }

        binding.barChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.5f }
            animateY(800)
            invalidate()
        }
    }

    private fun atualizarGraficoPizza(lista: List<Lancamento>, categorias: List<Categoria>) {
        val despesas = lista.filter { it.tipo == TipoLancamento.DESPESA }
        if (despesas.isEmpty()) { binding.pieChart.clear(); return }

        val mapaCats = categorias.associateBy({ it.id }, { it.nome })

        val entries = despesas.groupBy { it.categoriaID }
            .map { (catId, itens) ->
                PieEntry(itens.sumOf { it.valor }.toFloat(), mapaCats[catId] ?: "Outras")
            }

        val dataSet = PieDataSet(entries, "").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueLineColor = obterCorTextoBase()
            valueTextColor = obterCorTextoBase()
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            valueTextSize = 11f
            sliceSpace = 3f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.1f%%", value)
                }
            }
        }

        binding.pieChart.apply {
            data = PieData(dataSet)
            setUsePercentValues(true)
            animateXY(800, 800)
            invalidate()
        }
    }

    private fun configurarGraficoLinhaInicial() {
        val cor = obterCorTextoBase()
        binding.lineChart.apply {
            description.isEnabled = false
            setNoDataText("Aguardando dados...")
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = cor
                setDrawGridLines(false)
            }
            axisLeft.textColor = cor
            axisRight.isEnabled = false
            legend.textColor = cor
            setScaleEnabled(true)
            setPinchZoom(true)
        }
    }

    private fun configurarGraficoBarrasInicial() {
        val cor = obterCorTextoBase()
        binding.barChart.apply {
            description.isEnabled = false
            xAxis.apply {
                textColor = cor
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = object : ValueFormatter() {
                    private val labels = listOf("Receitas", "Despesas")
                    override fun getFormattedValue(value: Float): String {
                        return labels.getOrNull(value.toInt()) ?: ""
                    }
                }
                setDrawGridLines(false)
                granularity = 1f
            }
            axisLeft.textColor = cor
            axisRight.isEnabled = false
            legend.isEnabled = false
        }
    }

    private fun configurarGraficoPizzaInicial() {
        binding.pieChart.apply {
            description.isEnabled = false
            setHoleColor(Color.TRANSPARENT)
            setCenterTextSize(14f)
            legend.apply {
                textColor = obterCorTextoBase()
                isWordWrapEnabled = true
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
            }
            setEntryLabelColor(obterCorTextoBase())
        }
    }

    private fun atualizarResumo(lista: List<Lancamento>) {
        var receitas = 0.0
        var despesas = 0.0
        lista.forEach {
            if (it.tipo == TipoLancamento.RECEITA) receitas += it.valor
            else despesas += it.valor
        }
        val saldo = receitas - despesas

        val fmt = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        with(binding) {
            txtTotalReceitasGrafico.text = fmt.format(receitas)
            txtTotalDespesasGrafico.text = fmt.format(despesas)
            txtSaldoFinalGrafico.apply {
                text = fmt.format(saldo)
                setTextColor(ContextCompat.getColor(requireContext(), if (saldo >= 0) R.color.green else R.color.red))
            }
        }
    }

    private fun formatarMesAno(mesAno: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM", Locale.US).parse(mesAno)
            SimpleDateFormat("MMM/yy", Locale("pt", "BR")).format(date!!).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) { mesAno }
    }

    private fun obterCorTextoBase() = if (isDarkTheme()) Color.WHITE else Color.BLACK
    private fun isDarkTheme() = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    private class CurrencyFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float) = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value.toDouble())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}