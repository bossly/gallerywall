package com.baysoft.gallerywall.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.baysoft.gallerywall.GalleryWall
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.data.WallpaperDatabase
import com.baysoft.gallerywall.data.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WallpaperAdapter
    private lateinit var repository: WallpaperRepository
    private var placeholderText: View? = null
    private var wallpaperSetReceiver: BroadcastReceiver? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        repository = WallpaperRepository(WallpaperDatabase.getInstance(requireContext()).wallpaperDao())
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        adapter = WallpaperAdapter(
            onSetWallpaper = { wallpaper ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val context = requireContext().applicationContext
                        val bitmap = com.bumptech.glide.Glide.with(context)
                            .asBitmap()
                            .load(wallpaper.filePath)
                            .submit()
                            .get()
                        GalleryWall.updateWallpaper(context, bitmap)
                        GalleryWall.rememberAppliedWallpaperPath(context, wallpaper.filePath)
                        withContext(Dispatchers.Main) { loadRecents() }
                    } catch (_: Exception) {
                    }
                }
            },
            onDeleteWallpaper = { wallpaper ->
                lifecycleScope.launch {
                    repository.deleteWallpaper(wallpaper)
                    val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    if (wallpaper.filePath == Settings(prefs).lastAppliedWallpaperPath) {
                        GalleryWall.rememberAppliedWallpaperPath(requireContext().applicationContext, null)
                    }
                    loadRecents()
                }
            },
        )
        recyclerView.adapter = adapter
        placeholderText = view.findViewById(R.id.placeholderText)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadRecents()

        wallpaperSetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                loadRecents()
            }
        }
        requireContext().registerReceiver(
            wallpaperSetReceiver,
            IntentFilter().apply {
                addAction("com.baysoft.gallerywall.WALLPAPER_SET")
                addAction(GalleryWall.ACTION_REFRESH_IDLE)
            },
        )
    }

    private fun loadRecents() {
        lifecycleScope.launch {
            repository.cleanupOldWallpapers()
            val wallpapers = repository.getRecentWallpapers()
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val applied = Settings(prefs).lastAppliedWallpaperPath
            adapter.currentAppliedPath = applied
            adapter.submitList(wallpapers) {
                adapter.notifyDataSetChanged()
            }
            placeholderText?.visibility = if (wallpapers.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wallpaperSetReceiver?.let {
            requireContext().unregisterReceiver(it)
        }
    }
}
