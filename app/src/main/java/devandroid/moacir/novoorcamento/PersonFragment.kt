package devandroid.moacir.novoorcamento

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import devandroid.moacir.novoorcamento.database.AppDatabase
import devandroid.moacir.novoorcamento.databinding.FragmentPersonalizacaoBinding
import devandroid.moacir.novoorcamento.model.Categoria
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PersonFragment : Fragment() {

    private var _binding: FragmentPersonalizacaoBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalizacaoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        carregarCategoriasAtuais()

        binding.btnSalvarCategorias.setOnClickListener {
            salvarCategorias()
        }
    }

    private fun carregarCategoriasAtuais() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Buscamos todas as categorias do banco
                val categorias = withContext(Dispatchers.IO) {
                    db.orcamentoDao().listarCategorias()
                }

                // O DAO já filtra id > 1. Vamos mapear os resultados para os campos.
                // IMPORTANTE: categorias[0] será o ID 2, categorias[1] o ID 3, etc.
                if (categorias.isNotEmpty()) {
                    binding.editCat1.setText(categorias.find { it.id == 2 }?.nome ?: "")
                    binding.editCat2.setText(categorias.find { it.id == 3 }?.nome ?: "")
                    binding.editCat3.setText(categorias.find { it.id == 4 }?.nome ?: "")
                    binding.editCat4.setText(categorias.find { it.id == 5 }?.nome ?: "")
                    binding.editCat5.setText(categorias.find { it.id == 6 }?.nome ?: "")
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao carregar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun salvarCategorias() {
        // Captura os textos e valida se estão vazios
        val n1 = binding.editCat1.text.toString().trim()
        val n2 = binding.editCat2.text.toString().trim()
        val n3 = binding.editCat3.text.toString().trim()
        val n4 = binding.editCat4.text.toString().trim()
        val n5 = binding.editCat5.text.toString().trim()

        if (n1.isEmpty() || n2.isEmpty() || n3.isEmpty() || n4.isEmpty() || n5.isEmpty()) {
            Toast.makeText(requireContext(), "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Criamos a lista forçando os IDs de despesa (2 a 6)
                    val listaParaSalvar = listOf(
                        Categoria(id = 2, nome = n1),
                        Categoria(id = 3, nome = n2),
                        Categoria(id = 4, nome = n3),
                        Categoria(id = 5, nome = n4),
                        Categoria(id = 6, nome = n5)
                    )

                    // O upsertCategoria no seu DAO já resolve se insere ou atualiza
                    listaParaSalvar.forEach { categoria ->
                        db.orcamentoDao().upsertCategoria(categoria)
                    }
                }
                Toast.makeText(requireContext(), "Categorias salvas!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao salvar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}