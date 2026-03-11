package devandroid.moacir.Lume

import android.content.Context
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

        // Botão Salvar com Confirmação
        binding.btnSalvarCategorias.setOnClickListener {
            salvarCategorias()
        }

        // Botão Reset (Já tinha confirmação)
        binding.btnResetCategorias.setOnClickListener {
            exibirDialogoReset()
        }

        // NOVO: Botão Reativar Boas-Vindas (Adicione este ID no seu XML)
        binding.btnResetBoasVindas?.setOnClickListener {
            exibirDialogoReativarBoasVindas()
        }
    }

    private fun carregarCategoriasAtuais() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val categorias = withContext(Dispatchers.IO) {
                    db.orcamentoDao().listarCategorias()
                }
                val apenasDespesas = categorias.filter { it.id != 1 }

                if (apenasDespesas.isNotEmpty()) {
                    binding.editCat1.setText(categorias.find { it.id == 2 }?.nome ?: "")
                    binding.editCat2.setText(categorias.find { it.id == 3 }?.nome ?: "")
                    binding.editCat3.setText(categorias.find { it.id == 4 }?.nome ?: "")
                    binding.editCat4.setText(categorias.find { it.id == 5 }?.nome ?: "")
                    binding.editCat5.setText(categorias.find { it.id == 6 }?.nome ?: "")
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao carregar dados", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- DIÁLOGOS DE CONFIRMAÇÃO ---

    private fun exibirDialogoConfirmarSalvar() {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Alteração")
            .setMessage("Deseja alterar os nomes das categorias? Isso mudará como elas aparecem em todos os seus lançamentos.")
            .setPositiveButton("Salvar") { _, _ ->
                salvarCategorias()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun exibirDialogoReset() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reiniciar Categorias")
            .setMessage("Deseja voltar as categorias para os nomes padrão? Isso não apagará seus lançamentos.")
            .setPositiveButton("Reiniciar") { _, _ ->
                ReiniciarParaPadrao()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun exibirDialogoReativarBoasVindas() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reativar Introdução")
            .setMessage("Deseja que a tela de boas-vindas apareça novamente na próxima vez que abrir o app?")
            .setPositiveButton("Sim, Reativar") { _, _ ->
                reativarBoasVindas()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- LÓGICAS DE AÇÃO ---

    private var nomesAntigosParaBackup: List<String> = emptyList()
    private fun salvarCategorias() {
        val novosNomes = listOf(
            binding.editCat1.text.toString().trim(),
            binding.editCat2.text.toString().trim(),
            binding.editCat3.text.toString().trim(),
            binding.editCat4.text.toString().trim(),
            binding.editCat5.text.toString().trim()
        )

        if (novosNomes.any { it.isEmpty() }) {
            Toast.makeText(requireContext(), "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 2. Antes de salvar, buscamos os nomes que ESTAVAM no banco (Backup)
                val categoriasAntesDeMudar = withContext(Dispatchers.IO) {
                    db.orcamentoDao().listarCategorias().filter { it.id != 1 }
                }
                nomesAntigosParaBackup = categoriasAntesDeMudar.map { it.nome }

                // 3. Salva os novos nomes no Banco de Dados
                withContext(Dispatchers.IO) {
                    val listaMapeada = novosNomes.mapIndexed { index, nome ->
                        Categoria(id = index + 2, nome = nome)
                    }
                    listaMapeada.forEach { db.orcamentoDao().upsertCategoria(it) }
                }

                // 4. Limpa o foco e esconde o teclado
                finalizarEdicao()

                // 5. EXIBE O SNACKBAR COM OPÇÃO DE DESFAZER
                exibirSnackBarDesfazer()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao salvar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exibirSnackBarDesfazer() {
        // Use com.google.android.material.snackbar.Snackbar
        Snackbar.make(binding.root, "Categorias atualizadas!", Snackbar.LENGTH_LONG)
            .setAction("DESFAZER") {
                desfazerAlteracao()
            }
            // If R.color.blue_primary doesn't exist, use a standard color or your defined color
            .setActionTextColor(ContextCompat.getColor(requireContext(), R.color.blue_primary))
            .show()
    }

    private fun desfazerAlteracao() {
        if (nomesAntigosParaBackup.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Reverte para os nomes que estavam no backup
                    val listaReversa = nomesAntigosParaBackup.mapIndexed { index, nome ->
                        Categoria(id = index + 2, nome = nome)
                    }
                    listaReversa.forEach { db.orcamentoDao().upsertCategoria(it) }
                }

                // Recarrega a tela com os nomes antigos
                carregarCategoriasAtuais()
                Toast.makeText(requireContext(), "Alteração desfeita!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao desfazer", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun reativarBoasVindas() {
        val prefs = requireActivity().getSharedPreferences("config_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("exibir_boas_vindas", true).apply()
        Toast.makeText(requireContext(), "A introdução aparecerá no próximo início.", Toast.LENGTH_SHORT).show()
    }

    private fun ReiniciarParaPadrao() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val categoriasPadrao = listOf(
                        Categoria(id = 1, nome = "Receita"),
                        Categoria(id = 2, nome = "Alimentação"),
                        Categoria(id = 3, nome = "Casa"),
                        Categoria(id = 4, nome = "Lazer"),
                        Categoria(id = 5, nome = "Transporte"),
                        Categoria(id = 6, nome = "Outros")
                    )
                    categoriasPadrao.forEach { db.orcamentoDao().upsertCategoria(it) }
                }
                finalizarEdicao()
                Toast.makeText(requireContext(), "Categorias restauradas!", Toast.LENGTH_SHORT).show()
                carregarCategoriasAtuais()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao Reiniciar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finalizarEdicao() {
        binding.editCat1.clearFocus()
        binding.editCat2.clearFocus()
        binding.editCat3.clearFocus()
        binding.editCat4.clearFocus()
        binding.editCat5.clearFocus()

        val view = activity?.currentFocus ?: view
        view?.let { v ->
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}