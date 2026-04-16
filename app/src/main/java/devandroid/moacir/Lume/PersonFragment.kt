package com.moacir.Lume

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.moacir.Lume.database.AppDatabase
import com.moacir.Lume.databinding.FragmentPersonalizacaoBinding
import com.moacir.Lume.model.Categoria
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PersonFragment : Fragment() {

    private var _binding: FragmentPersonalizacaoBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var nomesAntigosParaBackup: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalizacaoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())
        configurarCliques()
    }

    private fun configurarCliques() {
        // 1. Clique para Gerenciar Categorias (Abre um diálogo ou lógica de edição)
        binding.btnCategorias.setOnClickListener {
            exibirDialogoGerenciarCategorias()
        }

        // 2. Clique para Resetar Tutorial de Boas-Vindas
        binding.btnResetBoasVindas.setOnClickListener {
            exibirDialogoReativarBoasVindas()
        }

        // 3. Clique para Avaliar na Play Store
        binding.btnAvaliar.setOnClickListener {
            abrirPlayStoreParaAvaliar()
        }

        // 4. Cliques de Monetização / Premium (Cards e Botões Pro)
        binding.cardPremium.setOnClickListener { exibirDialogoPremium() }
        binding.btnSejaPro.setOnClickListener { exibirDialogoPremium() }
        binding.btnExportarPDF.setOnClickListener { exibirDialogoPremium() }
    }

    private fun exibirDialogoGerenciarCategorias() {
        val inflater = LayoutInflater.from(requireContext())
        val viewDialog = inflater.inflate(R.layout.dialog_edit_categorias, null)

        val edit1 =
            viewDialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editCat1)
        val edit2 =
            viewDialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editCat2)
        val edit3 =
            viewDialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editCat3)
        val edit4 =
            viewDialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editCat4)
        val edit5 =
            viewDialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editCat5)

        viewLifecycleOwner.lifecycleScope.launch {
            val cats = withContext(Dispatchers.IO) { db.orcamentoDao().listarCategorias() }
            val editaveis = cats.filter { it.id >= 2 }
            edit1.setText(editaveis.getOrNull(0)?.nome ?: "")
            edit2.setText(editaveis.getOrNull(1)?.nome ?: "")
            edit3.setText(editaveis.getOrNull(2)?.nome ?: "")
            edit4.setText(editaveis.getOrNull(3)?.nome ?: "")
            edit5.setText(editaveis.getOrNull(4)?.nome ?: "")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Gerenciar Categorias")
            .setView(viewDialog)
            .setNeutralButton("Restaurar Padrão") { _, _ -> reiniciarParaPadrao() }
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val novosNomes = listOf(
                    edit1.text.toString(),
                    edit2.text.toString(),
                    edit3.text.toString(),
                    edit4.text.toString(),
                    edit5.text.toString()
                )
                salvarNovosNomesCategorias(novosNomes)
            }
            .show()
    }

    private fun salvarNovosNomesCategorias(nomes: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    nomes.forEachIndexed { index, nome ->
                        if (nome.isNotBlank()) {
                            db.orcamentoDao()
                                .upsertCategoria(Categoria(id = index + 2, nome = nome))
                        }
                    }
                }
                mostrarToast("Categorias atualizadas!")
            } catch (e: Exception) {
                mostrarToast("Erro ao salvar categorias")
            }
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
                mostrarToast("Categorias restauradas com sucesso!")
            } catch (e: Exception) {
                mostrarToast("Erro ao reiniciar categorias")
            }
        }
    }

    private fun exibirDialogoReativarBoasVindas() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reativar Introdução")
            .setMessage("Mostrar a tela de boas-vindas na próxima inicialização?")
            .setPositiveButton("Sim") { _, _ ->
                val prefs =
                    requireActivity().getSharedPreferences("config_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("exibir_boas_vindas", true).apply()
                mostrarToast("Introdução reativada!")
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun exibirDialogoPremium() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Lume Pro 🔥")
            .setMessage("A versão Pro está em desenvolvimento!\n\nEm breve você poderá exportar relatórios em PDF, ter backup automático e temas exclusivos.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun abrirPlayStoreParaAvaliar() {
        val packageName = requireContext().packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }

    private fun mostrarToast(mensagem: String) {
        Toast.makeText(requireContext(), mensagem, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}