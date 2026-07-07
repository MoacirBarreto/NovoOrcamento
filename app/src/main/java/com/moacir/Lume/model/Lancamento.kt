package com.moacir.Lume.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index
import com.moacir.Lume.model.Categoria
import com.moacir.Lume.model.TipoLancamento


@Entity(
    tableName = "lancamentos",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"], // Ensure this matches the ID column in Categoria entity
            childColumns = ["categoriaID"], // Changed from "categoria_id" to "categoriaID"
            onDelete = ForeignKey.CASCADE
        )
    ],
            indices = [Index(value = ["categoriaID"])]
)
data class Lancamento(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val descricao: String,
    val valor: Double,
    val data: Long,
    val categoriaID: Int, // This is the name Room is using for the column
    val tipo: TipoLancamento
)