package devandroid.moacir.novoorcamento

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import devandroid.moacir.novoorcamento.databinding.ItemAgendaBinding
import devandroid.moacir.novoorcamento.model.Agenda
import java.text.SimpleDateFormat
import java.util.Locale

class AgendaAdapter(
    private val lista: List<Agenda>,
    private val onConfirmarClick: (Agenda) -> Unit,
    private val onDeleteClick: (Agenda) -> Unit,
    private val onItemClick: (Agenda) -> Unit // <--- ADD THIS LINE
) : RecyclerView.Adapter<AgendaAdapter.AgendaViewHolder>() {

    inner class AgendaViewHolder(val binding: ItemAgendaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AgendaViewHolder {
        val binding = ItemAgendaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AgendaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AgendaViewHolder, position: Int) {
        val item = lista[position]
        val context = holder.itemView.context
        val formatador = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))

        holder.binding.txtItemAgendaDescricao.text = item.descricao
        holder.binding.txtItemAgendaValor.text = formatador.format(item.valor)

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        holder.binding.txtItemAgendaData.text = sdf.format(item.data)

        // Set up the click listeners
        holder.binding.btnConfirmarPagamento.setOnClickListener { onConfirmarClick(item) }
        holder.binding.btnExcluir.setOnClickListener { onDeleteClick(item) }

        // This makes the whole row clickable for editing
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = lista.size
}