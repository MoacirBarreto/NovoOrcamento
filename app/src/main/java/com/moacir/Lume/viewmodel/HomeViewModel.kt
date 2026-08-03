package com.moacir.Lume.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moacir.Lume.ConfiguracoesApp
import com.moacir.Lume.R
import com.moacir.Lume.database.OrcamentoRepository
import com.moacir.Lume.model.Lancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class HomeViewModel(private val repository: OrcamentoRepository) : ViewModel() {

    private val _lancamentos = MutableStateFlow<List<Lancamento>>(emptyList())
    val lancamentos: StateFlow<List<Lancamento>> = _lancamentos

    fun carregarLancamentos(chipId: Int) {
        viewModelScope.launch {
            val calendario = Calendar.getInstance()
            calendario.set(Calendar.HOUR_OF_DAY, 0)
            calendario.set(Calendar.MINUTE, 0)
            calendario.set(Calendar.SECOND, 0)
            calendario.set(Calendar.MILLISECOND, 0)

            val lista = withContext(Dispatchers.IO) {
                when (chipId) {
                    R.id.chipMesAtual -> {
                        calendario.set(Calendar.DAY_OF_MONTH, 1)
                        repository.listarLancamentosPorPeriodo(calendario.timeInMillis, Long.MAX_VALUE)
                    }
                    R.id.chip30Dias -> {
                        calendario.add(Calendar.DAY_OF_YEAR, -30)
                        repository.listarLancamentosPorPeriodo(calendario.timeInMillis, Long.MAX_VALUE)
                    }
                    R.id.chipPorPeriodo -> {
                        if (ConfiguracoesApp.temPeriodoPersonalizado()) {
                            repository.listarLancamentosPorPeriodo(
                                ConfiguracoesApp.dataInicioGlobal,
                                ConfiguracoesApp.dataFimGlobal
                            )
                        } else {
                            calendario.set(Calendar.DAY_OF_MONTH, 1)
                            repository.listarLancamentosPorPeriodo(
                                calendario.timeInMillis,
                                Long.MAX_VALUE
                            )
                        }
                    }
                    else -> repository.listarLancamentosSemFlow()
                }
            }
            _lancamentos.value = lista
        }
    }

    fun excluirLancamento(lancamento: Lancamento) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletarLancamento(lancamento)
            carregarLancamentos(ConfiguracoesApp.ultimoChipHome)
        }
    }

    class Factory(private val repository: OrcamentoRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
