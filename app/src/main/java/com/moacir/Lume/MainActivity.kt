package com.moacir.Lume

import android.content.SharedPreferences
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
import com.moacir.Lume.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    // Flag para controlar se o diálogo já foi exibido nesta sessão (viva) da Activity
    private var dialogoJaExibidoNestaSessao = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Recupera o estado se a tela foi girada
        if (savedInstanceState != null) {
            dialogoJaExibidoNestaSessao = savedInstanceState.getBoolean("dialogo_exibido", false)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Configuração da Toolbar e Navigation
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(R.drawable.ic_toolbar_logo)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.agendaFragment,
                R.id.graficosFragment,
                R.id.personFragment
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, _, _ ->
            supportActionBar?.title = "LUME"
            binding.topAppBar.title = "LUME"
        }

        // 3. Só verifica o Boas Vindas se ainda não foi exibido NESTA sessão
        if (!dialogoJaExibidoNestaSessao) {
            verificarBoasVindas()
        }
    }

    // 4. Salva o estado da flag antes de destruir a Activity (rotação)
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("dialogo_exibido", dialogoJaExibidoNestaSessao)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun verificarBoasVindas() {
        val prefs = getSharedPreferences("config_prefs", MODE_PRIVATE)
        val exibirBoasVindas = prefs.getBoolean("exibir_boas_vindas", true)

        if (exibirBoasVindas) {
            exibirDialogoBoasVindas(prefs)
        }
    }

    private fun exibirDialogoBoasVindas(prefs: SharedPreferences) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_welcome, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val chkNaoMostrar = dialogView.findViewById<CheckBox>(R.id.chkNaoMostrarNovamente)
        val btnEntendido = dialogView.findViewById<Button>(R.id.btnEntendido)

        btnEntendido.setOnClickListener {
            if (chkNaoMostrar.isChecked) {
                prefs.edit().putBoolean("exibir_boas_vindas", false).apply()
            }
            // Importante: Marcar como exibido para que o giro de tela não o traga de volta
            dialogoJaExibidoNestaSessao = true
            dialog.dismiss()
        }

        dialog.show()
    }
}