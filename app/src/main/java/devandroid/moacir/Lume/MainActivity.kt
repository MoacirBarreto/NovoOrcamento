package devandroid.moacir.Lume

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import devandroid.moacir.Lume.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inicializa o ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Configura a Toolbar como a ActionBar do sistema
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayShowHomeEnabled(true) // Garante exibição do ícone/logo
        supportActionBar?.setIcon(R.drawable.ic_toolbar_logo) // Reforça o uso do logo personalizado

        // 3. Configura o NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 4. Define os destinos principais (onde não aparece a seta de voltar)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.agendaFragment,
                R.id.graficosFragment,
                R.id.personFragment
            )
        )

        // 5. Vincula a Toolbar ao Navigation
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)

        // 6. Otimização: Garante que o título "LUME" seja fixo em todas as telas
        navController.addOnDestinationChangedListener { _, _, _ ->
            supportActionBar?.title = "LUME"
            // Se quiser garantir também na view direta da toolbar:
            binding.topAppBar.title = "LUME"
        }

        // 7. Verificação de boas-vindas
        verificarBoasVindas()
    }

    /**
     * Faz com que o botão de voltar (seta) na Toolbar funcione corretamente
     */
    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    /**
     * Lógica para exibir o diálogo de boas-vindas apenas na primeira vez
     */
    private fun verificarBoasVindas() {
        val prefs = getSharedPreferences("config_prefs", MODE_PRIVATE)
        val exibirBoasVindas = prefs.getBoolean("exibir_boas_vindas", true)

        if (exibirBoasVindas) {
            exibirDialogoBoasVindas(prefs)
        }
    }

    private fun exibirDialogoBoasVindas(prefs: android.content.SharedPreferences) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_welcome, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Deixa o fundo do diálogo transparente para respeitar os cantos arredondados do seu layout
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val chkNaoMostrar = dialogView.findViewById<CheckBox>(R.id.chkNaoMostrarNovamente)
        val btnEntendido = dialogView.findViewById<Button>(R.id.btnEntendido)

        btnEntendido.setOnClickListener {
            if (chkNaoMostrar.isChecked) {
                prefs.edit().putBoolean("exibir_boas_vindas", false).apply()
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}