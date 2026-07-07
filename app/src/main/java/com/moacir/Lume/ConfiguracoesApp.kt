package com.moacir.Lume

/**
 * Objeto central para compartilhar o período selecionado entre todas as telas do App.
 */
object ConfiguracoesApp {
    var dataInicioGlobal: Long = 0L
    var dataFimGlobal: Long = 0L

    // IDs dos chips para cada tela lembrar onde parou individualmente
    var ultimoChipHome: Int = R.id.chipMesAtual
    var ultimoChipGraficos: Int = R.id.chipMesAtualGrafico

    // Função utilitária para verificar se existe uma data selecionada
    fun temPeriodoPersonalizado(): Boolean = dataInicioGlobal != 0L
}