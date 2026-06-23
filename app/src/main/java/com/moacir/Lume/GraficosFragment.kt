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
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.moacir.Lume.database.AppDatabase
import com.moacir.Lume.databinding.FragmentGraficosBinding
import com.moacir.Lume.model.SaldoMensal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class GraficosFragment : Fragment() {

    private var _binding: FragmentGraficosBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: AppDatabase

    // Paleta de Cores Oficial LUME
    private val corLaranjaLume = Color.parseColor("#EF6C00")
    private val corMarromLume = Color.parseColor("#5D4037")
    private val corMarromEscuro = Color.parseColor("#3E2723")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGraficosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())

        configurarEstilosIniciais()
        observarDadosReais()
    }

    private fun configurarEstilosIniciais() {
        val corTexto = obterCorTextoTema()
        val msgVazio = "Aguardando dados..."

        // --- Estilo Barras ---
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

        // --- Estilo Linhas ---
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

            // Ativa o balão de valor personalizado que você criou
            marker = CustomMarkerView(requireContext(), R.layout.marker_view)
        }

        // --- Estilo Pizza ---
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

    private fun observarDadosReais() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Datas do mês atual para as Barras
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val inicio = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val fim = cal.timeInMillis

            val resumo = database.orcamentoDao().obterResumoFinanceiro(inicio, fim)

            // Observa a evolução do saldo (Flow)
            database.orcamentoDao().obterEvolucaoSaldo().collect { listaSaldo ->
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        atualizarBarras(resumo.receitas.toFloat(), resumo.despesas.toFloat())
                        atualizarLinhas(listaSaldo)
                        atualizarPizzaSimulada() // Implementar query de categorias no futuro
                    }
                }
            }
        }
    }

    private fun atualizarBarras(receitas: Float, despesas: Float) {
        if (receitas == 0f && despesas == 0f) {
            binding.chartBar.clear()
            return
        }

        val entries = listOf(BarEntry(0f, receitas), BarEntry(1f, despesas))
        val dataSet = BarDataSet(entries, "Resumo Mensal").apply {
            colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"))
            valueTextColor = obterCorTextoTema()
            valueTextSize = 10f
        }

        binding.chartBar.apply {
            data = BarData(dataSet).apply { barWidth = 0.5f }
            xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Receita", "Despesa"))
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

        val dataSet = LineDataSet(entries, "Evolução do Saldo").apply {
            color = corLaranjaLume
            setCircleColor(corMarromLume)
            lineWidth = 3f
            circleRadius = 5f
            setDrawCircleHole(true)
            circleHoleColor = Color.WHITE
            mode = LineDataSet.Mode.LINEAR
            setDrawFilled(true)
            fillColor = corLaranjaLume
            fillAlpha = 50 // Transparência para o fundo da linha
            valueTextColor = obterCorTextoTema()
        }

        binding.chartLine.apply {
            data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelRotationAngle = -45f
            animateX(1000)
            invalidate()
        }
    }

    private fun atualizarPizzaSimulada() {
        val entries = ArrayList<PieEntry>().apply {
            add(PieEntry(40f, "Essencial"))
            add(PieEntry(30f, "Lazer"))
            add(PieEntry(30f, "Investimento"))
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(corLaranjaLume, corMarromLume, corMarromEscuro)
            valueTextColor = Color.WHITE
            valueTextSize = 12f
            sliceSpace = 3f
        }

        binding.chartPie.apply {
            // 'this' here refers to binding.chartPie
            // Alternatively, the most robust way:
            data = PieData(dataSet).apply { setValueFormatter(PercentFormatter(binding.chartPie)) }

            animateY(1000, Easing.EaseInOutQuad)
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
        val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else corMarromEscuro
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}