package devandroid.moacir.novoorcamento

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import devandroid.moacir.novoorcamento.database.AppDatabase
import devandroid.moacir.novoorcamento.databinding.FragmentAgendaBinding
import devandroid.moacir.novoorcamento.model.Agenda
import devandroid.moacir.novoorcamento.model.Lancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgendaFragment : Fragment() {

    private var _binding: FragmentAgendaBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAgendaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        binding.rvAgenda.layoutManager = LinearLayoutManager(requireContext())

        // Observar a agenda (Flow)
        viewLifecycleOwner.lifecycleScope.launch {
            db.agendaDao().listarAgenda().collect { lista ->
                // A lógica de verificar se está vazio deve ficar AQUI dentro
                if (lista.isEmpty()) {
                    binding.txtAvisoVazio.visibility = View.VISIBLE
                    binding.rvAgenda.visibility = View.GONE
                } else {
                    binding.txtAvisoVazio.visibility = View.GONE
                    binding.rvAgenda.visibility = View.VISIBLE

                    // Configura ou atualiza o adapter com a lista que veio do banco
                    binding.rvAgenda.adapter = AgendaAdapter(
                        lista,
                        onConfirmarClick = { item -> confirmarLancamento(item) },
                        onDeleteClick = { item -> excluirDaAgenda(item) }
                    )
                }
            }
        }

        binding.fabAdicionarAgenda.setOnClickListener {
            val intent = Intent(requireContext(), NovoLancamentoActivity::class.java)
            intent.putExtra("IS_AGENDA", true)
            startActivity(intent)
        }
    }

    private fun confirmarLancamento(agenda: Agenda) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Lançamento")
            .setMessage("Deseja realizar o lançamento real de '${agenda.descricao}' hoje?")
            .setPositiveButton("Sim") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        // 1. Criar lançamento real
                        val novoLancamento = Lancamento(
                            descricao = agenda.descricao,
                            valor = agenda.valor,
                            data = System.currentTimeMillis(), // Data de hoje
                            categoriaID = agenda.categoriaID,
                            tipo = agenda.tipo
                        )
                        db.orcamentoDao().upsertLancamento(novoLancamento)

                        // 2. Remover da agenda
                        db.agendaDao().deletarAgenda(agenda)
                    }
                    Toast.makeText(context, "Lançado com sucesso!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun excluirDaAgenda(agenda: Agenda) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remover")
            .setMessage("Excluir este item da agenda?")
            .setPositiveButton("Sim") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.agendaDao().deletarAgenda(agenda) }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}