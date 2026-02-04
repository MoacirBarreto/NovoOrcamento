package devandroid.moacir.novoorcamento.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agenda")
data class Agenda(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val data: Long,
    val valor: Double,
    val descricao: String,
    val categoriaID: Int,
    val tipo: TipoLancamento // Reutilizando o Enum que você já tem
)