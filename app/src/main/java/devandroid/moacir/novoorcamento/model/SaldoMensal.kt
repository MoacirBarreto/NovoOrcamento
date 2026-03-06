package devandroid.moacir.novoorcamento.model

/**
 * Usamos 'data class' e colocamos as variáveis dentro dos parênteses ().
 * Isso cria automaticamente os getters e permite que o Room/KSP
 * instancie a classe corretamente.
 */
data class SaldoMensal(
    val mesAno: String,
    val saldo: Double
)