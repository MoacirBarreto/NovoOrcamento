package devandroid.moacir.Lume.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import devandroid.moacir.Lume.model.Categoria
import devandroid.moacir.Lume.model.Lancamento
import devandroid.moacir.Lume.model.SaldoMensal
import devandroid.moacir.Lume.model.Agenda
import kotlinx.coroutines.flow.Flow

@Dao
interface OrcamentoDao {

    @Upsert
    suspend fun upsertLancamento(lancamento: Lancamento)

    @Insert
    fun inserirCategoria(categoria: Categoria)

    @Query("SELECT * FROM categorias WHERE id > 1 ORDER BY nome ASC")
    suspend fun listarCategorias(): List<Categoria>

    @Insert
    fun inserirLancamento(lancamento: Lancamento)

    @Query("SELECT * FROM lancamentos ORDER BY data DESC")
    fun listarLancamentos(): Flow<List<Lancamento>>

    @Update
    fun atualizarLancamento(lancamento: Lancamento)

    @Delete
    suspend fun deletarLancamento(lancamento: Lancamento)

    @Query("SELECT * FROM lancamentos WHERE data BETWEEN :inicio AND :fim ORDER BY data DESC")
    suspend fun listarLancamentosPorPeriodo(inicio: Long, fim: Long): List<Lancamento>

    @Transaction
    @Upsert
    suspend fun upsertCategoria(categoria: Categoria)

    @Transaction
    suspend fun atualizarCategoriasFixas(nomes: List<String>) {
        for (i in nomes.indices) {
            val id = i + 2 // Começando do 2 conforme sua lógica de fragment_personalizacao
            val categoria = Categoria(id = id, nome = nomes[i])
            upsertCategoria(categoria)
        }
    }

    @Query("SELECT * FROM lancamentos")
    suspend fun listarLancamentosSemFlow(): List<Lancamento>

    @Query("""
        SELECT 
            strftime('%Y-%m', datetime(data/1000, 'unixepoch')) as mesAno,
            SUM(CASE WHEN tipo = 'RECEITA' THEN valor ELSE -valor END) as saldo
        FROM lancamentos 
        GROUP BY mesAno 
        ORDER BY mesAno ASC
    """)
    fun obterEvolucaoSaldo(): Flow<List<SaldoMensal>>

    @Query("""
        SELECT 
            SUM(CASE WHEN tipo = 'RECEITA' THEN valor ELSE 0 END) as receitas,
            SUM(CASE WHEN tipo = 'DESPESA' THEN valor ELSE 0 END) as despesas
        FROM lancamentos 
        WHERE data BETWEEN :inicio AND :fim
    """)
    suspend fun obterResumoFinanceiro(inicio: Long, fim: Long): ResumoFinanceiro

    data class ResumoFinanceiro(val receitas: Double, val despesas: Double)

    @Query("SELECT * FROM lancamentos WHERE id = :id")
    suspend fun buscarPorId(id: Int): Lancamento?

    // Busca vencimentos na tabela de Agenda para o alerta de 7 dias
    @Query("SELECT * FROM agenda WHERE data BETWEEN :hoje AND :proximaSemana ORDER BY data ASC")
    fun listarVencimentosProximos(hoje: Long, proximaSemana: Long): Flow<List<Agenda>>
}