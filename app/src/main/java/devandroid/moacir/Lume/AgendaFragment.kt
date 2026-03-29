package devandroid.moacir.Lume

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
import devandroid.moacir.Lume.database.AppDatabase
import devandroid.moacir.Lume.databinding.FragmentAgendaBinding
import devandroid.moacir.Lume.model.Agenda
import devandroid.moacir.Lume.model.Lancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class AgendaFragment : Fragment() {

    private var _binding: FragmentAgendaBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAgendaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        binding.rvAgenda.layoutManager = LinearLayoutManager(requireContext())

        configurarAlertaVencimentos()

        viewLifecycleOwner.lifecycleScope.launch {
            db.agendaDao().listarAgenda().collect { lista ->
                if (lista.isEmpty()) {
                    binding.txtAvisoVazio.visibility = View.VISIBLE
                    binding.rvAgenda.visibility = View.GONE
                } else {
                    binding.txtAvisoVazio.visibility = View.GONE
                    binding.rvAgenda.visibility = View.VISIBLE

                    binding.rvAgenda.adapter = AgendaAdapter(
                        lista = lista,
                        onConfirmarClick = { item -> confirmarLancamento(item) },
                        onDeleteClick = { item -> excluirDaAgenda(item) },
                        onItemClick = { item ->
                            val intent =
                                Intent(requireContext(), NovoLancamentoActivity::class.java)
                            intent.putExtra("IS_AGENDA", true)
                            intent.putExtra("LANCAMENTO_ID", item.id)
                            startActivity(intent)
                        }
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

    private fun configurarAlertaVencimentos() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        val hoje = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, 7)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        val proximaSemana = cal.timeInMillis

        viewLifecycleOwner.lifecycleScope.launch {
            db.orcamentoDao().listarVencimentosProximos(hoje, proximaSemana).collect { lista ->
                if (lista.isNotEmpty()) {
                    val totalValor = lista.sumOf { it.valor }
                    val qtd = lista.size

                    binding.cardAlertaVencimento.visibility = View.VISIBLE
                    binding.txtMensagemAlerta.text = if (qtd == 1) {
                        "Existe 1 conta próxima ao vencimento."
                    } else {
                        "Existem $qtd contas próximas totalizando ${formatarMoeda(totalValor)}"
                    }
                } else {
                    binding.cardAlertaVencimento.visibility = View.GONE
                }
            }
        }

        binding.btnFecharAlerta.setOnClickListener {
            binding.cardAlertaVencimento.visibility = View.GONE
        }
    }

    private fun confirmarLancamento(agenda: Agenda) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Lançamento")
            .setMessage("Deseja realizar o lançamento real de '${agenda.descricao}' hoje?")
            .setPositiveButton("Sim") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val novoLancamento = Lancamento(
                            descricao = agenda.descricao,
                            valor = agenda.valor,
                            data = System.currentTimeMillis(),
                            categoriaID = agenda.categoriaID,
                            tipo = agenda.tipo
                        )
                        db.orcamentoDao().upsertLancamento(novoLancamento)
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

    private fun formatarMoeda(valor: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(valor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}