package devandroid.moacir.Lume

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import devandroid.moacir.Lume.databinding.ItemAgendaBinding
import devandroid.moacir.Lume.model.Agenda
import java.text.SimpleDateFormat
import java.util.Locale

class AgendaAdapter(
    private val lista: List<Agenda>,
    private val onConfirmarClick: (Agenda) -> Unit,
    private val onDeleteClick: (Agenda) -> Unit, // Este deve ser o Long Click
    private val onItemClick: (Agenda) -> Unit    // Este deve ser o Click Curto (Editar)
) : RecyclerView.Adapter<AgendaAdapter.AgendaViewHolder>() {

    inner class AgendaViewHolder(val binding: ItemAgendaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AgendaViewHolder {
        val binding = ItemAgendaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AgendaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AgendaViewHolder, position: Int) {
        val item = lista[position]
        val formatador = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

        // Preenchimento dos dados
        holder.binding.txtItemAgendaDescricao.text = item.descricao
        holder.binding.txtItemAgendaValor.text = formatador.format(item.valor)

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        holder.binding.txtItemAgendaData.text = sdf.format(item.data)

        holder.binding.btnConfirmarPagamento.setOnClickListener { onConfirmarClick(item) }

        holder.itemView.setOnClickListener {
            onItemClick(item) // Chama a função de edição/detalhes
        }
        holder.itemView.setOnLongClickListener {
            onDeleteClick(item) // Chama a função de exclusão
            true // Indica que o evento foi consumido
        }
    }

    override fun getItemCount() = lista.size
}