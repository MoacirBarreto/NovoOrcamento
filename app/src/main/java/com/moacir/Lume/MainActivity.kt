package com.moacir.Lume

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.moacir.Lume.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private var dialogoJaExibidoNestaSessao = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Ativa o modo de ponta a ponta (Obrigatório SDK 35)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            dialogoJaExibidoNestaSessao = savedInstanceState.getBoolean("dialogo_exibido", false)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Ajuste de Recuos (Insets)
        // Aplicamos o recuo no root para que o conteúdo não fique atrás da barra de status ou de navegação
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Aplicamos padding no topo para a StatusBar e embaixo para a NavigationBar
            v.updatePadding(
                top = insets.top,
                bottom = insets.bottom
            )
            windowInsets
        }

        // 3. Configuração da Toolbar e Navigation
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

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.graficosFragment) {
                // Trava em Vertical (Portrait) quando entrar nos Gráficos
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                // Libera para rotacionar (ou volta ao padrão) nas outras telas
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            // Mantém o título padrão da Toolbar
            supportActionBar?.title = "LUME"
            binding.topAppBar.title = "LUME"
        }

        if (!dialogoJaExibidoNestaSessao) {
            verificarBoasVindas()
        }
    }

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

        // Garante que o fundo do diálogo seja transparente para não conflitar com o Edge-to-Edge
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val chkNaoMostrar = dialogView.findViewById<CheckBox>(R.id.chkNaoMostrarNovamente)
        val btnEntendido = dialogView.findViewById<Button>(R.id.btnEntendido)

        btnEntendido.setOnClickListener {
            if (chkNaoMostrar.isChecked) {
                prefs.edit().putBoolean("exibir_boas_vindas", false).apply()
            }
            dialogoJaExibidoNestaSessao = true
            dialog.dismiss()
        }

        dialog.show()
    }
}
