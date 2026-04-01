package devandroid.moacir.Lume

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
                        db.orcamentoDao()
                            .listarLancamentosPorPeriodo(cal.timeInMillis, Long.MAX_VALUE)
                    }

                    R.id.chip30DiasGrafico -> {
                        cal.add(Calendar.DAY_OF_YEAR, -30)
                        db.orcamentoDao()
                            .listarLancamentosPorPeriodo(cal.timeInMillis, Long.MAX_VALUE)
                    }

                    R.id.chipPorPeriodoGrafico -> {
                        db.orcamentoDao().listarLancamentosPorPeriodo(
                            dataInicioPersonalizada,
                            dataFimPersonalizada
                        )
                    }

                    else -> db.orcamentoDao().listarLancamentosSemFlow()
                }
                Triple(
                    resultado,
                    db.orcamentoDao().listarCategorias(),
                    db.orcamentoDao().obterEvolucaoSaldo().first()
                )
            }

            atualizarResumo(lista)
            atualizarGraficoBarras(lista)
            atualizarGraficoPizza(lista, categorias)
            atualizarGraficoLinha(evolucao)
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
                binding.chipPorPeriodoGrafico.text =
                    "${formato.format(Date(dataInicioPersonalizada))} - ${
                        formato.format(
                            Date(dataFimPersonalizada)
                        )
                    }"
                carregarDadosGrafico(R.id.chipPorPeriodoGrafico)
            }
        }
        picker.addOnNegativeButtonClickListener { binding.chipMesAtualGrafico.isChecked = true }
        picker.show(parentFragmentManager, "DATE_PICKER_GRAFICOS")
    }

    private fun atualizarGraficoLinha(dados: List<SaldoMensal>) {
        if (dados.isEmpty()) {
            binding.lineChart.clear(); return
        }
        val corTexto = obterCorTextoBase()

        val formatoMesAno = SimpleDateFormat("yyyy-MM", Locale.US)
        val cal = Calendar.getInstance()
        val mesesPermitidos = mutableListOf<String>()
        for (i in 0..11) {
            mesesPermitidos.add(formatoMesAno.format(cal.time))
            cal.add(Calendar.MONTH, -1)
        }

        val dadosFiltrados = dados
            .filter { it.mesAno in mesesPermitidos }
            .sortedBy { it.mesAno }

        if (dadosFiltrados.isEmpty()) {
            binding.lineChart.clear(); return
        }

        val entries = mutableListOf<Entry>()
        val labelsX = mutableListOf<String>()
        val coresCirculos = mutableListOf<Int>()
        val corLume = ContextCompat.getColor(requireContext(), R.color.lume_primary)
        val corErro = ContextCompat.getColor(requireContext(), R.color.lume_error)

        dadosFiltrados.forEachIndexed { index, item ->
            entries.add(Entry(index.toFloat(), item.saldo.toFloat()))
            labelsX.add(formatarMesAno(item.mesAno))
            coresCirculos.add(if (item.saldo >= 0) corLume else corErro)
        }

        val dataSet = LineDataSet(entries, "Evolução do Saldo").apply {
            color = corLume
            circleColors = coresCirculos
            lineWidth = 1.5f
            circleRadius = 4f
            valueTextColor = corTexto
            setDrawValues(true)

            setDrawFilled(true)

            fillColor = corLume
            fillAlpha = 50
            mode = LineDataSet.Mode.LINEAR
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            xAxis.apply {
                textColor = corTexto
                isGranularityEnabled = true
                granularity = 1f
                setLabelCount(labelsX.size, false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < labelsX.size && value == index.toFloat()) labelsX[index] else ""
                    }
                }
            }
            axisLeft.apply {
                textColor = corTexto
                setDrawGridLines(true)
            }
            setDrawBorders(false)
            xAxis.setAvoidFirstLastClipping(true)
            invalidate()
        }
    }

    private fun atualizarGraficoBarras(lista: List<Lancamento>) {
        val receitas =
            lista.filter { it.tipo == TipoLancamento.RECEITA }.sumOf { it.valor }.toFloat()
        val despesas =
            lista.filter { it.tipo == TipoLancamento.DESPESA }.sumOf { it.valor }.toFloat()
        val corTexto = obterCorTextoBase()

        val dataSet = BarDataSet(listOf(BarEntry(0f, receitas), BarEntry(1f, despesas)), "").apply {
            colors = listOf(Color.rgb(76, 175, 80), Color.rgb(244, 67, 54))
            valueFormatter = CurrencyFormatter()
            valueTextSize = 10f
            valueTextColor = corTexto
        }

        binding.barChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.5f }
            xAxis.textColor = corTexto
            axisLeft.textColor = corTexto
            animateY(800)
            invalidate()
        }
    }

    private fun atualizarGraficoPizza(lista: List<Lancamento>, categorias: List<Categoria>) {
        val despesas = lista.filter { it.tipo == TipoLancamento.DESPESA }
        if (despesas.isEmpty()) {
            binding.pieChart.clear(); return
        }
        val corTexto = obterCorTextoBase()

        val mapaCats = categorias.associateBy({ it.id }, { it.nome })
        val entries = despesas.groupBy { it.categoriaID }
            .map { (catId, itens) ->
                PieEntry(
                    itens.sumOf { it.valor }.toFloat(),
                    mapaCats[catId] ?: "Outras"
                )
            }

        val dataSet = PieDataSet(entries, "").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            valueTextSize = 11f
            sliceSpace = 3f
            valueTextColor = corTexto
            valueLineColor = corTexto
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    String.format("%.1f%%", value)
            }
        }

        binding.pieChart.apply {
            data = PieData(dataSet)
            setUsePercentValues(true)
            setEntryLabelColor(corTexto)
            legend.textColor = corTexto
            animateXY(800, 800)
            invalidate()
        }
    }

    private fun configurarGraficoLinhaInicial() {
        val cor = obterCorTextoBase()
        binding.lineChart.apply {
            description.isEnabled = false
            setNoDataText("Iluminando dados...")
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = cor
                setDrawGridLines(false)
            }
            axisLeft.textColor = cor
            axisRight.isEnabled = false
            legend.textColor = cor
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
                    override fun getFormattedValue(value: Float): String =
                        labels.getOrNull(value.toInt()) ?: ""
                }
            }
            axisLeft.textColor = cor
            axisRight.isEnabled = false
            legend.textColor = cor
        }
    }

    private fun configurarGraficoPizzaInicial() {
        val cor = obterCorTextoBase()
        binding.pieChart.apply {
            description.isEnabled = false
            holeRadius = 40f
            transparentCircleRadius = 45f
            setHoleColor(Color.TRANSPARENT)
            legend.textColor = cor
            setNoDataText("Adicione despesas para ver o gráfico")
        }
    }

    private fun atualizarResumo(lista: List<Lancamento>) {
        val receitas = lista.filter { it.tipo == TipoLancamento.RECEITA }.sumOf { it.valor }
        val despesas = lista.filter { it.tipo == TipoLancamento.DESPESA }.sumOf { it.valor }
        val saldo = receitas - despesas
        val fmt = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

        binding.txtTotalReceitasGrafico.text = fmt.format(receitas)
        binding.txtTotalDespesasGrafico.text = fmt.format(despesas)
        binding.txtSaldoFinalGrafico.apply {
            text = fmt.format(saldo)
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (saldo >= 0) R.color.lume_primary else R.color.lume_error
                )
            )
        }
    }

    private fun formatarMesAno(mesAno: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM", Locale.US).parse(mesAno)
            // Força PT-BR para aparecer Abr/24 em vez de Apr/24
            SimpleDateFormat("MMM/yy", Locale("pt", "BR")).format(date!!)
        } catch (e: Exception) {
            mesAno
        }
    }

    private fun obterCorTextoBase(): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnSurface,
            typedValue,
            true
        )
        return typedValue.data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class CurrencyFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
        }
    }
}