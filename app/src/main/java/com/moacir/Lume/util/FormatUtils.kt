package com.moacir.Lume.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    private val localeBR = Locale("pt", "BR")
    private val moedaFormat = NumberFormat.getCurrencyInstance(localeBR)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", localeBR)
    private val dateShortFormat = SimpleDateFormat("dd/MM", localeBR)

    fun formatarMoeda(valor: Double): String {
        return moedaFormat.format(valor)
    }

    fun formatarData(millis: Long): String {
        return dateFormat.format(Date(millis))
    }

    fun formatarDataCurta(millis: Long): String {
        return dateShortFormat.format(Date(millis))
    }

    fun formatarPeriodo(inicio: Long, fim: Long): String {
        return "${formatarDataCurta(inicio)} - ${formatarDataCurta(fim)}"
    }
}
