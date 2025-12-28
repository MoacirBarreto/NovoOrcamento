package devandroid.moacir.novoorcamento

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import devandroid.moacir.novoorcamento.databinding.ItemLancamentoBinding
import devandroid.moacir.novoorcamento.model.Lancamento
import devandroid.moacir.novoorcamento.model.TipoLancamento
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LancamentoAdapter(
    private val listaLancamentos: List<Lancamento>,
    val onItemClick: (Lancamento) -> Unit,
    val onItemLongClick: (Lancamento) -> Unit
) : RecyclerView.Adapter<LancamentoAdapter.LancamentoViewHolder>() {

    inner class LancamentoViewHolder(val binding: ItemLancamentoBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LancamentoViewHolder {
        val binding = ItemLancamentoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LancamentoViewHolder(binding)
    }

    override fun getItemCount(): Int = listaLancamentos.size

    override fun onBindViewHolder(holder: LancamentoViewHolder, position: Int) {
        val lancamento = listaLancamentos[position]
        val context = holder.itemView.context

        // Configura os cliques
        holder.itemView.setOnClickListener {
            onItemClick(lancamento)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(lancamento)
            true
        }

        // Preenche os dados
        holder.binding.tvDescricao.text = lancamento.descricao

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        holder.binding.tvData.text = sdf.format(Date(lancamento.data))

        val formatoMoeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        holder.binding.tvValor.text = formatoMoeda.format(lancamento.valor)

        // Lógica de Cores (Apenas Texto agora)
        if (lancamento.tipo == TipoLancamento.RECEITA) {
            holder.binding.tvValor.setTextColor(ContextCompat.getColor(context, R.color.green))
        } else { // DESPESA
            holder.binding.tvValor.setTextColor(ContextCompat.getColor(context, R.color.red))
        }
    }
}
