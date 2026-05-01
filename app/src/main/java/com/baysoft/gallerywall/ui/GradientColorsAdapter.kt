package com.baysoft.gallerywall.ui

import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.baysoft.gallerywall.R

class GradientColorsAdapter(
    private val colors: MutableList<Int>,
    private val onEdit: (position: Int) -> Unit,
    private val onRemove: (position: Int) -> Unit,
) : RecyclerView.Adapter<GradientColorsAdapter.VH>() {

    override fun getItemCount(): Int = colors.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gradient_color, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = colors[position]
        holder.swatch.background = ColorDrawable(c)
        holder.label.text = holder.itemView.context.getString(R.string.gradient_color_label, position + 1)
        holder.edit.setOnClickListener { onEdit(position) }
        val canRemove = colors.size > 2
        holder.remove.isEnabled = canRemove
        holder.remove.alpha = if (canRemove) 1f else 0.38f
        holder.remove.setOnClickListener {
            if (canRemove) onRemove(position)
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val swatch: View = itemView.findViewById(R.id.swatch)
        val label: TextView = itemView.findViewById(R.id.textLabel)
        val edit: ImageButton = itemView.findViewById(R.id.btnEditColor)
        val remove: ImageButton = itemView.findViewById(R.id.btnRemoveColor)
    }
}
