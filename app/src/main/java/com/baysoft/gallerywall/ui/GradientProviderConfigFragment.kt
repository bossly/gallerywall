package com.baysoft.gallerywall.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.WallpaperGenerator
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GradientProviderConfigFragment : Fragment(R.layout.fragment_gradient_provider_config) {

    private val colors = mutableListOf<Int>()
    private lateinit var preview: ImageView
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var listAdapter: GradientColorsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        preview = view.findViewById(R.id.previewWallpaper)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_gradient_colors)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnAddColor)

        loadColorsFromPrefs()

        listAdapter = GradientColorsAdapter(
            colors,
            onEdit = { position -> showColorDialog(position) },
            onRemove = { position -> removeAt(position) },
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = listAdapter

        btnAdd.setOnClickListener {
            colors.add(colors.lastOrNull() ?: Color.parseColor("#888888"))
            persistColors()
            listAdapter.notifyItemInserted(colors.size - 1)
            refreshPreview()
        }

        preview.viewTreeObserver.addOnGlobalLayoutListener(
            object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    preview.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    refreshPreview()
                }
            },
        )
    }

    private fun loadColorsFromPrefs() {
        colors.clear()
        colors.addAll(WallpaperGenerator.parseColors(Settings(prefs).generatedColorsHex))
        while (colors.size < 2) {
            colors.add(Color.parseColor("#888888"))
        }
    }

    private fun persistColors() {
        val hex = colors.joinToString(",") { WallpaperGenerator.colorToHexString(it) }
        prefs.edit().putString(Settings.PREF_GENERATED_COLORS, hex).apply()
    }

    private fun removeAt(position: Int) {
        if (colors.size <= 2) return
        colors.removeAt(position)
        persistColors()
        listAdapter.notifyDataSetChanged()
        refreshPreview()
    }

    private fun showColorDialog(position: Int) {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_gradient_color, null)
        val sr = dialogView.findViewById<Slider>(R.id.dialogSliderRed)
        val sg = dialogView.findViewById<Slider>(R.id.dialogSliderGreen)
        val sb = dialogView.findViewById<Slider>(R.id.dialogSliderBlue)

        fun slidersToColor(): Int {
            val r = sr.value.toInt().coerceIn(0, 255)
            val g = sg.value.toInt().coerceIn(0, 255)
            val b = sb.value.toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        }

        val start = colors[position]
        sr.value = Color.red(start).toFloat()
        sg.value = Color.green(start).toFloat()
        sb.value = Color.blue(start).toFloat()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.gradient_edit_color_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                colors[position] = slidersToColor()
                persistColors()
                listAdapter.notifyItemChanged(position)
                refreshPreview()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshPreview() {
        val list = colors.toList()
        val w = if (preview.width > 0) preview.width else resources.displayMetrics.widthPixels
        val h = preview.height.takeIf { it > 0 }
            ?: (200 * resources.displayMetrics.density).toInt().coerceAtLeast(200)
        lifecycleScope.launch(Dispatchers.Default) {
            val bmp = WallpaperGenerator.renderGradient(w, h, list)
            withContext(Dispatchers.Main.immediate) {
                preview.setImageBitmap(bmp)
            }
        }
    }
}
