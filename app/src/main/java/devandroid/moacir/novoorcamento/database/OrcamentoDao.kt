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
import kotlinx.coroutines.flow.Flow

@Dao
interface OrcamentoDao {

    @Upsert
    suspend fun upsertLancamento(lancamento: Lancamento)

    @Insert
    fun inserirCategoria(categoria: Categoria)

    @Query("SELECT * FROM categorias WHERE id >1 ORDER BY nome ASC")
    suspend fun listarCategorias(): List<Categoria>

    @Insert
    fun inserirLancamento(lancamento: Lancamento)

    @Query("SELECT * FROM lancamentos ORDER BY data DESC")
    fun listarLancamentos(): Flow<List<Lancamento>>

    // 2. Adicionar metodo para ATUALIZAR
    @Update
    fun atualizarLancamento(lancamento: Lancamento)

    // 3. Adicionar metodo para DELETAR
    @Delete
    suspend fun deletarLancamento(lancamento: Lancamento)

    // Busca lançamentos entre duas datas (Data Inicial e Data Final)
    @Query("SELECT * FROM lancamentos WHERE data BETWEEN :inicio AND :fim ORDER BY data DESC")
    suspend fun listarLancamentosPorPeriodo(inicio: Long, fim: Long): List<Lancamento>

    @Transaction
    @Upsert
    suspend fun upsertCategoria(categoria: Categoria)

    @Transaction
    suspend fun atualizarCategoriasFixas(nomes: List<String>) {
        for (i in nomes.indices) {
            val id = i + 1
            val categoria = Categoria(id = id, nome = nomes[i])
            upsertCategoria(categoria) // Now this will work
        }
    }

    @Query("SELECT * FROM lancamentos") // Substitua 'lancamentos' pelo nome da sua tabela
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

    // Classe de suporte para o retorno acima (pode colocar no mesmo arquivo do DAO)
    data class ResumoFinanceiro(val receitas: Double, val despesas: Double)

    @Query("SELECT * FROM lancamentos WHERE id = :id")
    suspend fun buscarPorId(id: Int): Lancamento?

}

