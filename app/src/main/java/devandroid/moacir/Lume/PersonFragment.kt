package devandroid.moacir.Lume

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import devandroid.moacir.Lume.database.AppDatabase
import devandroid.moacir.Lume.databinding.FragmentPersonalizacaoBinding
import devandroid.moacir.Lume.model.Categoria
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PersonFragment : Fragment() {

    private var _binding: FragmentPersonalizacaoBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var nomesAntigosParaBackup: List<String> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPersonalizacaoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())
        carregarCategoriasAtuais()
        configurarCliques()
    }

    private fun configurarCliques() {
        binding.btnSalvarCategorias.setOnClickListener { salvarCategorias() }
        binding.btnResetCategorias.setOnClickListener { exibirDialogoReset() }
        binding.btnResetBoasVindas.setOnClickListener { exibirDialogoReativarBoasVindas() }
        binding.btnEnviarFeedback.setOnClickListener { enviarEmailFeedback() }
    }

    private fun carregarCategoriasAtuais() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val categorias = withContext(Dispatchers.IO) { db.orcamentoDao().listarCategorias() }
                val edits = listOf(binding.editCat1, binding.editCat2, binding.editCat3, binding.editCat4, binding.editCat5)
                edits.forEachIndexed { index, editText ->
                    editText.setText(categorias.find { it.id == index + 2 }?.nome ?: "")
                }
            } catch (e: Exception) {
                mostrarToast("Erro ao carregar dados")
            }
        }
    }

    private fun salvarCategorias() {
        val novosNomes = listOf(
            binding.editCat1.text.toString().trim(),
            binding.editCat2.text.toString().trim(),
            binding.editCat3.text.toString().trim(),
            binding.editCat4.text.toString().trim(),
            binding.editCat5.text.toString().trim()
        )

        if (novosNomes.any { it.isEmpty() }) {
            mostrarToast("Preencha todos os campos")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val categoriasAtuais = withContext(Dispatchers.IO) {
                    db.orcamentoDao().listarCategorias().filter { it.id in 2..6 }
                }
                nomesAntigosParaBackup = categoriasAtuais.map { it.nome }

                withContext(Dispatchers.IO) {
                    novosNomes.forEachIndexed { index, nome ->
                        db.orcamentoDao().upsertCategoria(Categoria(id = index + 2, nome = nome))
                    }
                }

                finalizarEdicao()
                exibirSnackBarDesfazer()
            } catch (e: Exception) {
                mostrarToast("Erro ao salvar")
            }
        }
    }

    private fun enviarEmailFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("seu-email@exemplo.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Sugestão - App Lume")
            putExtra(Intent.EXTRA_TEXT, "Olá,\n\nMinha sugestão para o Lume é: ")
        }
        try {
            startActivity(Intent.createChooser(intent, "Enviar sugestão por:"))
        } catch (e: Exception) {
            mostrarToast("Nenhum app de e-mail encontrado")
        }
    }

    private fun reiniciarParaPadrao() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val padrao = listOf("Alimentação", "Casa", "Lazer", "Transporte", "Outros")
                    padrao.forEachIndexed { index, nome ->
                        db.orcamentoDao().upsertCategoria(Categoria(id = index + 2, nome = nome))
                    }
                }
                carregarCategoriasAtuais()
                finalizarEdicao()
                mostrarToast("Categorias restauradas!")
            } catch (e: Exception) {
                mostrarToast("Erro ao reiniciar")
            }
        }
    }

    private fun exibirSnackBarDesfazer() {
        Snackbar.make(binding.root, "Categorias atualizadas!", Snackbar.LENGTH_LONG)
            .setAction("DESFAZER") {
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        nomesAntigosParaBackup.forEachIndexed { index, nome ->
                            db.orcamentoDao().upsertCategoria(Categoria(id = index + 2, nome = nome))
                        }
                    }
                    carregarCategoriasAtuais()
                }
            }
            .setActionTextColor(ContextCompat.getColor(requireContext(), R.color.lume_primary))
            .show()
    }

    private fun exibirDialogoReset() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reiniciar Categorias")
            .setMessage("Deseja voltar aos nomes padrão?")
            .setPositiveButton("Reiniciar") { _, _ -> reiniciarParaPadrao() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun exibirDialogoReativarBoasVindas() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reativar Introdução")
            .setMessage("Mostrar a tela de boas-vindas na próxima inicialização?")
            .setPositiveButton("Sim") { _, _ ->
                val prefs = requireActivity().getSharedPreferences("config_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("exibir_boas_vindas", true).apply()
                mostrarToast("Introdução reativada!")
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun finalizarEdicao() {
        val edits = listOf(binding.editCat1, binding.editCat2, binding.editCat3, binding.editCat4, binding.editCat5)
        edits.forEach { it.clearFocus() }
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun mostrarToast(mensagem: String) {
        Toast.makeText(requireContext(), mensagem, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}