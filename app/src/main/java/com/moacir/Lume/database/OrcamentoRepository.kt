package com.moacir.Lume.database

import com.moacir.Lume.model.Agenda
import com.moacir.Lume.model.Categoria
import com.moacir.Lume.model.CategoriaResumo
import com.moacir.Lume.model.Lancamento
import com.moacir.Lume.model.SaldoMensal
import kotlinx.coroutines.flow.Flow

class OrcamentoRepository(private val db: AppDatabase) {

    private val orcamentoDao = db.orcamentoDao()
    private val agendaDao = db.agendaDao()

    // Lançamentos
    suspend fun upsertLancamento(lancamento: Lancamento) = orcamentoDao.upsertLancamento(lancamento)
    
    suspend fun deletarLancamento(lancamento: Lancamento) = orcamentoDao.deletarLancamento(lancamento)
    
    suspend fun listarLancamentosPorPeriodo(inicio: Long, fim: Long): List<Lancamento> = 
        orcamentoDao.listarLancamentosPorPeriodo(inicio, fim)
    
    suspend fun listarLancamentosSemFlow(): List<Lancamento> = orcamentoDao.listarLancamentosSemFlow()
    
    suspend fun buscarLancamentoPorId(id: Int) = orcamentoDao.buscarPorId(id)

    // Categorias
    suspend fun listarCategorias(): List<Categoria> = orcamentoDao.listarCategorias()
    
    suspend fun upsertCategoria(categoria: Categoria) = orcamentoDao.upsertCategoria(categoria)

    // Resumos e Gráficos
    suspend fun obterResumoFinanceiro(inicio: Long, fim: Long) = orcamentoDao.obterResumoFinanceiro(inicio, fim)
    
    fun obterEvolucaoSaldo(): Flow<List<SaldoMensal>> = orcamentoDao.obterEvolucaoSaldo()
    
    suspend fun obterDespesasPorCategoria(inicio: Long, fim: Long): List<CategoriaResumo> = 
        orcamentoDao.obterDespesasPorCategoria(inicio, fim)

    // Agenda
    suspend fun upsertAgenda(agenda: Agenda) = agendaDao.upsertAgenda(agenda)
    
    suspend fun deletarAgenda(agenda: Agenda) = agendaDao.deletarAgenda(agenda)
    
    fun listarAgenda(): Flow<List<Agenda>> = agendaDao.listarAgenda()
    
    suspend fun buscarAgendaPorId(id: Int) = agendaDao.buscarPorId(id)
    
    fun listarVencimentosProximos(hoje: Long, proximaSemana: Long) = 
        orcamentoDao.listarVencimentosProximos(hoje, proximaSemana)
}
