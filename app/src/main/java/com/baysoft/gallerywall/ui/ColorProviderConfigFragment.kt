package com.baysoft.gallerywall.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.WallpaperGenerator
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ColorProviderConfigFragment : Fragment(R.layout.fragment_color_provider_config) {

    private var syncingFromSliders = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val preview = view.findViewById<ImageView>(R.id.previewWallpaper)
        val inputHex = view.findViewById<TextInputEditText>(R.id.inputHex)
        val sliderRed = view.findViewById<Slider>(R.id.sliderRed)
        val sliderGreen = view.findViewById<Slider>(R.id.sliderGreen)
        val sliderBlue = view.findViewById<Slider>(R.id.sliderBlue)

        fun refreshPreview() {
            val hex = Settings(prefs).colorProviderSolidHex
            val c = Color.parseColor(hex)
            val w = if (preview.width > 0) preview.width else resources.displayMetrics.widthPixels
            val h = preview.height.takeIf { it > 0 }
                ?: (200 * resources.displayMetrics.density).toInt().coerceAtLeast(200)
            lifecycleScope.launch(Dispatchers.Default) {
                val bmp = WallpaperGenerator.renderSolid(w, h, c)
                withContext(Dispatchers.Main.immediate) {
                    preview.setImageBitmap(bmp)
                }
            }
        }

        fun persistAndPreviewArgb(argb: Int) {
            prefs.edit()
                .putString(Settings.PREF_COLOR_PROVIDER_SOLID, WallpaperGenerator.colorToHexString(argb))
                .apply()
            refreshPreview()
        }

        fun applyColorToUi(color: Int, updateHexField: Boolean) {
            syncingFromSliders = true
            sliderRed.value = Color.red(color).toFloat()
            sliderGreen.value = Color.green(color).toFloat()
            sliderBlue.value = Color.blue(color).toFloat()
            if (updateHexField) {
                inputHex.setText(WallpaperGenerator.colorToHexString(color))
            }
            syncingFromSliders = false
        }

        val initial = Color.parseColor(Settings(prefs).colorProviderSolidHex)
        applyColorToUi(initial, updateHexField = true)

        val sliderListener =
            Slider.OnChangeListener { _, _, fromUser ->
                if (!fromUser || syncingFromSliders) return@OnChangeListener
                val r = sliderRed.value.toInt().coerceIn(0, 255)
                val g = sliderGreen.value.toInt().coerceIn(0, 255)
                val b = sliderBlue.value.toInt().coerceIn(0, 255)
                val c = Color.rgb(r, g, b)
                syncingFromSliders = true
                inputHex.setText(WallpaperGenerator.colorToHexString(c))
                syncingFromSliders = false
                persistAndPreviewArgb(c)
            }
        sliderRed.addOnChangeListener(sliderListener)
        sliderGreen.addOnChangeListener(sliderListener)
        sliderBlue.addOnChangeListener(sliderListener)

        inputHex.doAfterTextChanged { editable ->
            if (syncingFromSliders) return@doAfterTextChanged
            val raw = editable?.toString()?.trim() ?: return@doAfterTextChanged
            val withHash = if (raw.startsWith("#")) raw else "#$raw"
            try {
                val c = Color.parseColor(withHash)
                applyColorToUi(c, updateHexField = false)
                persistAndPreviewArgb(c)
            } catch (_: IllegalArgumentException) {
            }
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
}
