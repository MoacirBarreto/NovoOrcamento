package devandroid.moacir.novoorcamento

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import devandroid.moacir.novoorcamento.databinding.ItemAgendaBinding
import devandroid.moacir.novoorcamento.model.Agenda
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class AgendaAdapter(
    private val lista: List<Agenda>,
    private val onConfirmarClick: (Agenda) -> Unit,
    private val onDeleteClick: (Agenda) -> Unit,
    private val onItemClick: (Agenda) -> Unit
) : RecyclerView.Adapter<AgendaAdapter.AgendaViewHolder>() {

    inner class AgendaViewHolder(val binding: ItemAgendaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AgendaViewHolder {
        val binding = ItemAgendaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AgendaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AgendaViewHolder, position: Int) {
        val item = lista[position]
        val context = holder.itemView.context

        with(holder.binding) {
            txtItemAgendaDescricao.text = item.descricao

            // Formatação de Moeda
            val formatoMoeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
            txtItemAgendaValor.text = formatoMoeda.format(item.valor)

            // Formatação de Data
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            txtItemAgendaData.text = sdf.format(Date(item.data))

            // Lógica de Cor para Atrasados (Data menor que hoje)
            val hoje = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            if (item.data < hoje) {
                txtItemAgendaData.setTextColor(ContextCompat.getColor(context, R.color.red))
            } else {
                txtItemAgendaData.setTextColor(ContextCompat.getColor(context, android.R.color.tab_indicator_text))
            }

            // --- AS TRÊS OPÇÕES DE CLIQUE ---

            // 1. LANÇAR (E APAGAR): Botão de Check
            btnConfirmarPagamento.setOnClickListener { onConfirmarClick(item) }

            // 2. EDITAR: Clique curto no card
            root.setOnClickListener { onItemClick(item) }

            // 3. EXCLUIR (SEM LANÇAR): Clique longo no card
            root.setOnLongClickListener {
                onDeleteClick(item)
                true
            }
        }
    }

    override fun getItemCount() = lista.size
}