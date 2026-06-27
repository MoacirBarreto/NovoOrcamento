package com.moacir.Lume

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.NumberFormat
import java.util.Locale

class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val format = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        tvContent.text = format.format(e?.y ?: 0f)
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        // Ajusta o balão para ficar centralizado acima do ponto
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}
