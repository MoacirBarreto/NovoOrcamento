package com.moacir.Lume.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moacir.Lume.database.OrcamentoRepository
import com.moacir.Lume.model.CategoriaResumo
import com.moacir.Lume.model.SaldoMensal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GraficosViewModel(private val repository: OrcamentoRepository) : ViewModel() {

    private val _resumo = MutableStateFlow<ResumoData>(ResumoData())
    val resumo: StateFlow<ResumoData> = _resumo

    data class ResumoData(
        val receitas: Double = 0.0,
        val despesas: Double = 0.0,
        val despesasPorCategoria: List<CategoriaResumo> = emptyList(),
        val evolucaoSaldo: List<SaldoMensal> = emptyList()
    )

    fun carregarDados(inicio: Long, fim: Long) {
        viewModelScope.launch {
            val resumoFinanceiro = repository.obterResumoFinanceiro(inicio, fim)
            val despesasPorCat = repository.obterDespesasPorCategoria(inicio, fim)
            
            repository.obterEvolucaoSaldo().collectLatest { evolucao ->
                _resumo.value = ResumoData(
                    receitas = resumoFinanceiro.receitas,
                    despesas = resumoFinanceiro.despesas,
                    despesasPorCategoria = despesasPorCat,
                    evolucaoSaldo = evolucao
                )
            }
        }
    }

    class Factory(private val repository: OrcamentoRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GraficosViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return GraficosViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
