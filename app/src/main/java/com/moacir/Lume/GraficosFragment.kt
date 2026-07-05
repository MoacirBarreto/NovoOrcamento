package com.moacir.Lume

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.animation.Easing
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
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.android.material.datepicker.MaterialDatePicker
import com.moacir.Lume.database.AppDatabase
import com.moacir.Lume.databinding.FragmentGraficosBinding
import com.moacir.Lume.model.CategoriaResumo
import com.moacir.Lume.model.SaldoMensal
import kotlinx.coroutines.Dispatchers
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
    private lateinit var database: AppDatabase
    private var dataInicioPersonalizada: Long = 0L
    private var dataFimPersonalizada: Long = 0L
    private val corLaranjaLume = Color.parseColor("#EF6C00")
    private val corMarromLume = Color.parseColor("#5D4037")
    private val corMarromEscuro = Color.parseColor("#3E2723")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGraficosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())

        configurarEstilosIniciais()
        configurarFiltros()

        // Seleciona o mês atual por padrão na inicialização
        binding.chipMesAtualGrafico.isChecked = true
        processarFiltro(R.id.chipMesAtualGrafico)
    }

    private fun configurarEstilosIniciais() {
        val corTexto = obterCorTextoTema()
        val msgVazio = "Sem dados para o período"

        with(binding.chartBar) {
            description.isEnabled = false
            setNoDataText(msgVazio)
            setNoDataTextColor(corTexto)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = corTexto
            xAxis.setDrawGridLines(false)
            axisLeft.textColor = corTexto
            axisRight.isEnabled = false
            legend.textColor = corTexto
        }

        with(binding.chartLine) {
            description.isEnabled = false
            setNoDataText(msgVazio)
            setNoDataTextColor(corTexto)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = corTexto
            xAxis.setDrawGridLines(false)
            axisLeft.textColor = corTexto
            axisRight.isEnabled = false
            legend.textColor = corTexto
            marker = CustomMarkerView(requireContext(), R.layout.marker_view)
        }

        with(binding.chartPie) {
            description.isEnabled = false
            setNoDataText(msgVazio)
            setNoDataTextColor(corTexto)
            setHoleColor(Color.TRANSPARENT)
            setCenterTextColor(corTexto)
            legend.textColor = corTexto
            setUsePercentValues(true)
        }
    }

    private fun configurarFiltros() {
        binding.chipGroupFiltrosGrafico.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val chipId = checkedIds[0]
            if (chipId == R.id.chipPorPeriodoGrafico) {
                abrirSeletorDeData()
            } else {
                processarFiltro(chipId)
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
                dataInicioPersonalizada = dataInicio + offset
                dataFimPersonalizada = dataFim + offset + 86399999

                val formato = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
                binding.chipPorPeriodoGrafico.text =
                    "${formato.format(Date(dataInicioPersonalizada))} - ${
                        formato.format(Date(dataFimPersonalizada))
                    }"

                observarDadosReais(dataInicioPersonalizada, dataFimPersonalizada)
            }
        }
        picker.addOnNegativeButtonClickListener { binding.chipMesAtualGrafico.isChecked = true }
        picker.show(parentFragmentManager, "DATE_PICKER_GRAFICOS")
    }

    private fun processarFiltro(chipId: Int) {
        val calendario = Calendar.getInstance()
        calendario.set(Calendar.HOUR_OF_DAY, 0)
        calendario.set(Calendar.MINUTE, 0)
        calendario.set(Calendar.SECOND, 0)
        calendario.set(Calendar.MILLISECOND, 0)

        val agora = Calendar.getInstance().timeInMillis
        var inicio: Long = 0L
        var fim: Long = agora

        when (chipId) {
            R.id.chipMesAtualGrafico -> {
                calendario.set(Calendar.DAY_OF_MONTH, 1)
                inicio = calendario.timeInMillis
                binding.chipPorPeriodoGrafico.text = "Período"
            }

            R.id.chip30DiasGrafico -> {
                calendario.add(Calendar.DAY_OF_YEAR, -30)
                inicio = calendario.timeInMillis
                binding.chipPorPeriodoGrafico.text = "Período"
            }
        }
        observarDadosReais(inicio, fim)
    }

    private fun observarDadosReais(inicio: Long, fim: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val resumo = database.orcamentoDao().obterResumoFinanceiro(inicio, fim)

            val despesasPorCategoria =
                database.orcamentoDao().obterDespesasPorCategoria(inicio, fim)

            database.orcamentoDao().obterEvolucaoSaldo().collect { listaSaldo ->
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        atualizarCardResumo(resumo.receitas, resumo.despesas)
                        atualizarBarras(resumo.receitas.toFloat(), resumo.despesas.toFloat())
                        atualizarLinhas(listaSaldo)
                        atualizarPizzaReal(despesasPorCategoria)
                    }
                }
            }
        }
    }

    private fun atualizarPizzaReal(dados: List<CategoriaResumo>) {
        if (dados.isEmpty()) {
            binding.chartPie.clear()
            binding.chartPie.setNoDataText("Sem despesas no período")
            return
        }

        val entries = dados.map { PieEntry(it.valor.toFloat(), it.nome) }

        val dataSet = PieDataSet(entries, "").apply {
            colors =
                listOf(corLaranjaLume, corMarromLume, corMarromEscuro, Color.LTGRAY, Color.GRAY)
            valueTextColor = Color.WHITE
            valueTextSize = 12f
            sliceSpace = 3f
        }

        binding.chartPie.apply {
            data = PieData(dataSet).apply {
                setValueFormatter(PercentFormatter(binding.chartPie))
            }
            animateY(1000, Easing.EaseInOutQuad)
            invalidate()
        }
    }

    private fun atualizarCardResumo(receitas: Double, despesas: Double) {
        val formato = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        val saldo = receitas - despesas

        binding.txtTotalReceitasGrafico.text = formato.format(receitas)
        binding.txtTotalDespesasGrafico.text = formato.format(despesas)
        binding.txtSaldoFinalGrafico.text = formato.format(saldo)
    }

    private fun atualizarBarras(receitas: Float, despesas: Float) {
        if (receitas == 0f && despesas == 0f) {
            binding.chartBar.clear()
            binding.chartBar.setNoDataText("Sem lançamentos neste período")
            return
        }

        val entries = listOf(BarEntry(0f, receitas), BarEntry(1f, despesas))
        val dataSet = BarDataSet(entries, "Receitas vs Despesas").apply {
            colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"))
            valueTextColor = obterCorTextoTema()
            valueTextSize = 10f
        }

        binding.chartBar.apply {
            data = BarData(dataSet).apply { barWidth = 0.5f }
            xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Receita", "Despesa"))
            xAxis.granularity = 1f
            animateY(1000)
            invalidate()
        }
    }

    private fun atualizarLinhas(dados: List<SaldoMensal>) {
        if (dados.isEmpty()) {
            binding.chartLine.clear()
            return
        }

        val entries = dados.mapIndexed { i, s -> Entry(i.toFloat(), s.saldo.toFloat()) }
        val labels = gerarLabelsMeses(dados.size)

        val dataSet = LineDataSet(entries, "Saldo Acumulado").apply {
            color = corLaranjaLume
            setCircleColor(corMarromLume)
            lineWidth = 3f
            circleRadius = 5f
            setDrawCircleHole(true)
            circleHoleColor = Color.WHITE
            mode = LineDataSet.Mode.LINEAR
            setDrawFilled(true)
            fillColor = corLaranjaLume
            fillAlpha = 50
            valueTextColor = obterCorTextoTema()
        }

        binding.chartLine.apply {
            data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelRotationAngle = -45f
            xAxis.granularity = 1f
            animateX(1000)
            invalidate()
        }
    }

    private fun gerarLabelsMeses(quantidade: Int): List<String> {
        val lista = mutableListOf<String>()
        val sdf = SimpleDateFormat("MMM-yy", Locale("pt", "BR"))
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -(quantidade - 1))

        for (i in 0 until quantidade) {
            lista.add(sdf.format(cal.time).uppercase())
            cal.add(Calendar.MONTH, 1)
        }
        return lista
    }

    private fun obterCorTextoTema(): Int {
        val uiMode =
            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else corMarromEscuro
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}