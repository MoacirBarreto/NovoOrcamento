package devandroid.moacir.Lume

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import devandroid.moacir.Lume.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa o ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuração do Navigation Component
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragmentContainer) as NavHostFragment
        val navController = navHostFragment.navController

        // Vincula a BottomNavigation com o NavController (Gerencia as trocas de tela)
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController)

        // Executa a verificação das boas-vindas após a interface carregar
        verificarBoasVindas()
    }

    /**
     * Verifica no SharedPreferences se a tela de introdução deve ser exibida.
     */
    private fun verificarBoasVindas() {
        val prefs = getSharedPreferences("config_prefs", MODE_PRIVATE)
        val exibirBoasVindas = prefs.getBoolean("exibir_boas_vindas", true)

        if (exibirBoasVindas) {
            exibirDialogoBoasVindas(prefs)
        }
    }

    /**
     * Infla e configura o diálogo customizado com o tema Lume.
     */
    private fun exibirDialogoBoasVindas(prefs: android.content.SharedPreferences) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_welcome, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false) // Força o usuário a clicar no botão para entrar
            .create()

        // IMPORTANTE: Remove o fundo padrão do AlertDialog para permitir que os
        // cantos arredondados e o gradiente do seu XML apareçam corretamente.
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Referências dos componentes internos do layout XML
        val chkNaoMostrar = dialogView.findViewById<CheckBox>(R.id.chkNaoMostrarNovamente)
        val btnEntendido = dialogView.findViewById<Button>(R.id.btnEntendido)

        btnEntendido.setOnClickListener {
            // Se o CheckBox estiver marcado, salva a preferência para não mostrar mais
            if (chkNaoMostrar.isChecked) {
                prefs.edit().putBoolean("exibir_boas_vindas", false).apply()
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}