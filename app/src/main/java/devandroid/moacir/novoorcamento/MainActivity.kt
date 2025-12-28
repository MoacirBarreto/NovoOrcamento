package devandroid.moacir.novoorcamento

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import devandroid.moacir.novoorcamento.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Carrega o Fragmento inicial (Home)
        if (savedInstanceState == null) {
            trocarFragment(HomeFragment())
        }

        // Configura o clique na barra
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    trocarFragment(HomeFragment())
                    true
                }
                R.id.nav_graficos -> {
                    trocarFragment(GraficosFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun trocarFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
