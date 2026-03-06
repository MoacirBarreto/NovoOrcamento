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
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
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
import devandroid.moacir.novoorcamento.model.SaldoMensal
import devandroid.moacir.novoorcamento.model.TipoLancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.first

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
        configurarGraficoLinhaInicial()
        configurarGraficoPizzaInicial()
        configurarFiltros()
    }

    override fun onResume() {
        super.onResume()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val chipId = binding.chipGroupFiltrosGrafico.checkedChipId
        carregarDadosGrafico(if (chipId != View.NO_ID) chipId else R.id.chipMesAtualGrafico)
    }

    private fun configurarFiltros() {
        binding.chipGroupFiltrosGrafico.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val chipId = checkedIds[0]
            if (chipId == R.id.chipPorPeriodoGrafico) abrirSeletorDeData()
            else carregarDadosGrafico(chipId)
        }
    }

    private fun abrirSeletorDeData() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Selecione o Período")
        val picker = builder.build()

        picker.addOnPositiveButtonClickListener { selection ->
            dataInicioPersonalizada = selection.first ?: 0L
            dataFimPersonalizada = selection.second ?: 0L
            val formato = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
            binding.chipPorPeriodoGrafico.text = "${formato.format(Date(dataInicioPersonalizada))} - ${formato.format(Date(dataFimPersonalizada))}"
            carregarDadosGrafico(R.id.chipPorPeriodoGrafico)
        }
        picker.show(parentFragmentManager, "DATE_PICKER_GRAFICOS")
    }

    private fun carregarDadosGrafico(chipId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val (listaFiltrada, categorias, evolucaoSaldo) = withContext(Dispatchers.IO) {
                val cal = Calendar.getInstance()
                val lista = when (chipId) {
                    R.id.chipMesAtualGrafico -> {
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        db.orcamentoDao().listarLancamentosPorPeriodo(cal.timeInMillis, Long.MAX_VALUE)
                    }
                    R.id.chip30DiasGrafico -> {
                        cal.add(Calendar.DAY_OF_YEAR, -30)
                        db.orcamentoDao().listarLancamentosPorPeriodo(cal.timeInMillis, Long.MAX_VALUE)
                    }
                    R.id.chipPorPeriodoGrafico -> {
                        db.orcamentoDao().listarLancamentosPorPeriodo(dataInicioPersonalizada, dataFimPersonalizada + 86400000)
                    }
                    else -> db.orcamentoDao().listarLancamentosSemFlow()

                }
                val evolucao = db.orcamentoDao().obterEvolucaoSaldo().first()
                Triple(lista, db.orcamentoDao().listarCategorias(), evolucao)
            }

            if (_binding != null) {
                atualizarResumo(listaFiltrada)
                atualizarGraficoBarras(listaFiltrada)
                atualizarGraficoPizza(listaFiltrada, categorias)
                atualizarGraficoLinha(evolucaoSaldo)
            }
        }
    }

    // --- GRÁFICO DE LINHA (EVOLUÇÃO MENSAL) ---

    private fun configurarGraficoLinhaInicial() {
        val corTexto = obterCorTextoBase()
        binding.lineChart.apply {            description.isEnabled = false
            setDrawGridBackground(false)

            // Habilita interação para que o usuário veja os detalhes ao tocar
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = corTexto
                setDrawGridLines(false)
                granularity = 1f
            }
            axisLeft.apply {
                textColor = corTexto
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
            }
            axisRight.isEnabled = false
            legend.textColor = corTexto
        }
    }
    private fun atualizarGraficoLinha(dados: List<SaldoMensal>) {
        if (dados.isEmpty()) {
            binding.lineChart.clear()
            return
        }

        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        val coresDosCirculos = mutableListOf<Int>()

        val corAzul = ContextCompat.getColor(requireContext(), R.color.blue_primary)
        val corVermelha = ContextCompat.getColor(requireContext(), R.color.red)

        // Ordenar por data caso venha desordenado do banco
        val dadosOrdenados = dados.sortedBy { it.mesAno }

        dadosOrdenados.forEachIndexed { index, saldoMensal ->
            val valor = saldoMensal.saldo.toFloat()

            // 1. Criar a entrada do gráfico (X é o índice, Y é o valor)
            entries.add(Entry(index.toFloat(), valor))

            // 2. Criar o rótulo formatado (Ex: Jan/24)
            labels.add(formatarMesAno(saldoMensal.mesAno))

            // 3. Definir a cor do ponto (Azul para positivo, Vermelho para negativo)
            coresDosCirculos.add(if (valor >= 0) corAzul else corVermelha)
        }

        val dataSet = LineDataSet(entries, "Saldo Mensal (R$)").apply {
            color = corAzul
            lineWidth = 3f

            // UX Limpo: Esconde valores fixos (o MarkerView mostrará ao tocar)
            setDrawValues(false)

            setCircleColors(coresDosCirculos)
            circleRadius = 5f
            setDrawCircleHole(true)
            circleHoleColor = Color.WHITE

            setDrawFilled(true)
            // Certifique-se que o drawable 'fade_blue' existe em res/drawable
            fillDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.fade_blue)
            fillAlpha = 50
        }

        binding.lineChart.apply {
            // Configura o marcador para exibir detalhes ao clicar no ponto
            val mv = CustomMarkerView(requireContext(), R.layout.layout_marker_view)
            mv.chartView = this
            marker = mv

            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            data = LineData(dataSet)

            // Linha de limite no Zero para facilitar visualização de dívidas
            axisLeft.removeAllLimitLines()
            val limitLine = LimitLine(0f, "")
            limitLine.lineColor = Color.GRAY
            limitLine.lineWidth = 1f
            limitLine.enableDashedLine(10f, 10f, 0f)
            axisLeft.addLimitLine(limitLine)

            animateX(1000)
            invalidate()
        }
    }

    private fun formatarMesAno(mesAno: String): String {
        return try {
            val original = SimpleDateFormat("yyyy-MM", Locale.US).parse(mesAno)
            val formatador = SimpleDateFormat("MMM/yy", Locale("pt", "BR"))
            formatador.format(original!!).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            mesAno
        }
    }

    // --- GRÁFICOS EXISTENTES (BARRAS E PIZZA) ---

    private fun configurarGraficoBarrasInicial() {
        val corTexto = obterCorTextoBase()
        binding.barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.textColor = corTexto
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Receitas", "Despesas"))
            axisRight.isEnabled = false
            axisLeft.textColor = corTexto
            axisLeft.axisMinimum = 0f
        }
    }

    private fun atualizarGraficoBarras(lista: List<Lancamento>) {
        val receitas = lista.filter { it.tipo == TipoLancamento.RECEITA }.sumOf { it.valor }.toFloat()
        val despesas = lista.filter { it.tipo == TipoLancamento.DESPESA }.sumOf { it.valor }.toFloat()

        val dataSet = BarDataSet(listOf(BarEntry(0f, receitas), BarEntry(1f, despesas)), "Resumo").apply {
            colors = listOf(ContextCompat.getColor(requireContext(), R.color.green), ContextCompat.getColor(requireContext(), R.color.red))
            valueTextColor = obterCorTextoBase()
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value.toDouble())
            }
        }
        binding.barChart.data = BarData(dataSet).apply { barWidth = 0.5f }
        binding.barChart.invalidate()
    }

    private fun atualizarGraficoPizza(lista: List<Lancamento>, categorias: List<Categoria>) {
        val apenasDespesas = lista.filter { it.tipo == TipoLancamento.DESPESA }
        if (apenasDespesas.isEmpty()) { binding.pieChart.clear(); return }

        val mapaCats = categorias.associate { it.id to it.nome }
        val entries = apenasDespesas.groupBy { it.categoriaID }
            .map { PieEntry(it.value.sumOf { l -> l.valor }.toFloat(), mapaCats[it.key] ?: "Outros") }

        val dataSet = PieDataSet(entries, "").apply {
            colors = ColorTemplate.COLORFUL_COLORS.toList()
            valueTextColor = obterCorTextoBase()
            xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            valueLineColor = obterCorTextoBase()
        }
        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.invalidate()
    }

    private fun configurarGraficoPizzaInicial() {
        binding.pieChart.apply {
            description.isEnabled = false
            setHoleColor(Color.TRANSPARENT)
            legend.textColor = obterCorTextoBase()
            setEntryLabelColor(obterCorTextoBase())
        }
    }

    private fun atualizarResumo(lista: List<Lancamento>) {
        val r = lista.filter { it.tipo == TipoLancamento.RECEITA }.sumOf { it.valor }
        val d = lista.filter { it.tipo == TipoLancamento.DESPESA }.sumOf { it.valor }
        val s = r - d
        val fmt = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        binding.txtTotalReceitasGrafico.text = fmt.format(r)
        binding.txtTotalDespesasGrafico.text = fmt.format(d)
        binding.txtSaldoFinalGrafico.text = fmt.format(s)
        binding.txtSaldoFinalGrafico.setTextColor(ContextCompat.getColor(requireContext(), if (s >= 0) R.color.green else R.color.red))
    }

    private fun obterCorTextoBase() = if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}