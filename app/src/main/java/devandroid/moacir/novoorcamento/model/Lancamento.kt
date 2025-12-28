package devandroid.moacir.novoorcamento.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "lancamentos",// Configura a chave estrangeira (relação com Categoria)
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],     // ID na tabela Categoria
            childColumns = ["categoriaID"], // Coluna nesta tabela
            onDelete = ForeignKey.CASCADE   // Se apagar a categoria, apaga os lançamentos dela
        )
    ]
)
data class Lancamento(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val descricao: String,
    val valor: Double,
    val data: Long, // Guardaremos data como milissegundos (timestamp) para simplificar

    val categoriaID: Int, // Chave estrangeira
    val tipo: TipoLancamento // Nosso Enum (RECEITA ou DESPESA)
)
