package devandroid.moacir.novoorcamento.database

import androidx.room.*
import devandroid.moacir.novoorcamento.model.Agenda
import kotlinx.coroutines.flow.Flow

@Dao
interface AgendaDao {
    @Upsert
    suspend fun upsertAgenda(agenda: Agenda)

    @Delete
    suspend fun deletarAgenda(agenda: Agenda)

    @Query("SELECT * FROM agenda ORDER BY data ASC")
    fun listarAgenda(): Flow<List<Agenda>>

    @Query("SELECT * FROM agenda WHERE id = :id")
    suspend fun buscarPorId(id: Int): Agenda?
}