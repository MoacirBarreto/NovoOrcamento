package devandroid.moacir.novoorcamento

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import devandroid.moacir.novoorcamento.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuração do Navigation Component (Navegação entre Home, Gráficos, etc)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragmentContainer) as NavHostFragment
        val navController = navHostFragment.navController

        // Vincula a BottomNavigation com o NavController
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController)

        // CHAMA A TELA DE BOAS-VINDAS
        verificarBoasVindas()
    }

    /**
     * Verifica se deve exibir as instruções de uso.
     * A tela só aparece na primeira vez ou se o usuário resetar nas configurações.
     */
    private fun verificarBoasVindas() {
        val prefs = getSharedPreferences("config_prefs", MODE_PRIVATE)
        val exibirBoasVindas = prefs.getBoolean("exibir_boas_vindas", true)

        if (exibirBoasVindas) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_welcome, null)
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false) // Impede fechar clicando fora
                .create()

            // Referências dos componentes do layout XML
            val chk = dialogView.findViewById<CheckBox>(R.id.chkNaoMostrarNovamente)
            val btn = dialogView.findViewById<Button>(R.id.btnEntendido)

            btn.setOnClickListener {
                // Se o usuário marcou o checkbox, salvamos que ele não quer mais ver a tela
                if (chk.isChecked) {
                    prefs.edit().putBoolean("exibir_boas_vindas", false).apply()
                }
                dialog.dismiss()
            }

            dialog.show()
        }
    }
}