package com.baysoft.gallerywall.ui

import com.bumptech.glide.Glide
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.data.WallpaperEntity

class WallpaperAdapter : ListAdapter<WallpaperEntity, WallpaperAdapter.WallpaperViewHolder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WallpaperViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wallpaper, parent, false)
        return WallpaperViewHolder(view)
    }

    override fun onBindViewHolder(holder: WallpaperViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class WallpaperViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        fun bind(wallpaper: WallpaperEntity) {
            Glide.with(imageView.context)
                .load(wallpaper.imagePath)
                .centerCrop()
                .into(imageView)
            val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(wallpaper.dateAdded))
            dateText.text = date
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WallpaperEntity>() {
            override fun areItemsTheSame(oldItem: WallpaperEntity, newItem: WallpaperEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: WallpaperEntity, newItem: WallpaperEntity) = oldItem == newItem
        }
    }
}
