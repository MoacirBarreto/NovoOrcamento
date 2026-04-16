package com.moacir.Lume

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        verificarBoasVindas()
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

    private fun exibirDialogoBoasVindas(prefs: android.content.SharedPreferences) {
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
            dialog.dismiss()
        }

        dialog.show()
    }
}