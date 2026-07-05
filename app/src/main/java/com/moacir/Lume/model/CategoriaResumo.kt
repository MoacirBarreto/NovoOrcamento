package com.moacir.Lume.model

/**
 * Esta classe NÃO é uma @Entity.
 * Ela serve apenas para carregar os dados somados para o gráfico.
 */
data class CategoriaResumo(
    val nome: String,
    val valor: Double
)