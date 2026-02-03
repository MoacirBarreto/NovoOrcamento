package devandroid.moacir.novoorcamento.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lancamentos",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaID"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // ADICIONE ESTA LINHA ABAIXO PARA CORRIGIR O ERRO
    indices = [Index(value = ["categoriaID"])]
)
data class Lancamento(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val descricao: String,
    val valor: Double,
    val data: Long, // Guardaremos data como milissegundos (timestamp)

    val categoriaID: Int, // Chave estrangeira indexada
    val tipo: TipoLancamento // Enum (RECEITA ou DESPESA)
)