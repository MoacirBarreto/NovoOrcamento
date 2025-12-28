package devandroid.moacir.novoorcamento.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update // 1. Importar Update e Delete
import devandroid.moacir.novoorcamento.model.Categoria
import devandroid.moacir.novoorcamento.model.Lancamento

@Dao
interface OrcamentoDao {
    @Insert
    fun inserirCategoria(categoria: Categoria)

    @Query("SELECT * FROM categorias ORDER BY nome ASC")
    fun listarCategorias(): List<Categoria>

    @Insert
    fun inserirLancamento(lancamento: Lancamento)

    @Query("SELECT * FROM lancamentos ORDER BY data DESC")
    fun listarLancamentos(): List<Lancamento>

    // 2. Adicionar método para ATUALIZAR
    @Update
    fun atualizarLancamento(lancamento: Lancamento)

    // 3. Adicionar metodo para DELETAR
    @Delete
    fun deletarLancamento(lancamento: Lancamento)

    // Busca lançamentos entre duas datas (Data Inicial e Data Final)
    @Query("SELECT * FROM lancamentos WHERE data BETWEEN :inicio AND :fim ORDER BY data DESC")
    fun listarLancamentosPorPeriodo(inicio: Long, fim: Long): List<Lancamento>


}
