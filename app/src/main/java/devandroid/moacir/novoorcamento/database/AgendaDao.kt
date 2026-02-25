package devandroid.moacir.novoorcamento.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import devandroid.moacir.novoorcamento.model.Agenda
import kotlinx.coroutines.flow.Flow

@Dao
interface AgendaDao {

    // REMOVA o @Insert antigo que estava aqui com o mesmo nome

    @Upsert
    suspend fun upsertAgenda(agenda: Agenda)

    @Query("SELECT * FROM agenda")
    fun listarAgendasSemFlow(): List<Agenda>

    @Delete
    suspend fun deletarAgenda(agenda: Agenda)

    @Query("SELECT * FROM agenda ORDER BY data ASC")
    fun listarAgenda(): Flow<List<Agenda>>

    @Query("SELECT * FROM agenda WHERE id = :id")
    suspend fun buscarPorId(id: Int): Agenda?
}