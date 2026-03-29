package devandroid.moacir.Lume

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.NumberFormat
import java.util.Locale

class CustomMarkerView(context: Context, layoutResource: Int) :
    MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tvContent)

    // FIX: Changed Map.Entry? to Entry?
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        val valor = e?.y?.toDouble() ?: 0.0
        val fmt = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        tvContent.text = fmt.format(valor)

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        // Centraliza o balão acima do ponto
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}
