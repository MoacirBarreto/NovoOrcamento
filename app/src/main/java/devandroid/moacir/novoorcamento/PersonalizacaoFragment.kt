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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class PersonalizacaoFragment : Fragment() {

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

            val categorias = withContext(Dispatchers.IO) {
                db.orcamentoDao().listarCategorias()
            }
            if (categorias.isNotEmpty()) {
                binding.editCat1.setText(categorias.getOrNull(0)?.nome)
                binding.editCat2.setText(categorias.getOrNull(1)?.nome)
                binding.editCat3.setText(categorias.getOrNull(2)?.nome)
                binding.editCat4.setText(categorias.getOrNull(3)?.nome)
                binding.editCat5.setText(categorias.getOrNull(4)?.nome)
            }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao carregar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun salvarCategorias() {
        val nomes = listOf(
            binding.editCat1.text.toString(),
            binding.editCat2.text.toString(),
            binding.editCat3.text.toString(),
            binding.editCat4.text.toString(),
            binding.editCat5.text.toString(),
        )

        if (nomes.any { it.isBlank() }) {
            Toast.makeText(requireContext(), "Preencha as 5 categorias", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.orcamentoDao().atualizarCategoriasFixas(nomes)
            }
            Toast.makeText(requireContext(), "Categorias atualizadas!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}