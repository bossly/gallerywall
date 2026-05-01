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
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.data.WallpaperDatabase
import com.baysoft.gallerywall.data.WallpaperRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }
    override fun onCreateOptionsMenu(menu: android.view.Menu, inflater: android.view.MenuInflater) {
        inflater.inflate(R.menu.menu_home, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                findNavController().navigate(R.id.action_homeFragment_to_settingsFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WallpaperAdapter
    private lateinit var repository: WallpaperRepository
    private var placeholderText: View? = null
    private var refreshButton: View? = null
    private var wallpaperSetReceiver: BroadcastReceiver? = null
    private var refreshProgress: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
    recyclerView = view.findViewById(R.id.recyclerView)
    recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        adapter = WallpaperAdapter { wallpaper ->
            // Set wallpaper in background thread
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val context = requireContext().applicationContext
                    val bitmap = com.bumptech.glide.Glide.with(context)
                        .asBitmap()
                        .load(wallpaper.filePath)
                        .submit()
                        .get()
                    com.baysoft.gallerywall.GalleryWall.updateWallpaper(context, bitmap)
                } catch (e: Exception) {
                    // Optionally show error
                }
            }
        }
        recyclerView.adapter = adapter
        placeholderText = view.findViewById(R.id.placeholderText)
        refreshButton = view.findViewById(R.id.refreshButton)
        refreshProgress = view.findViewById(R.id.refreshProgress)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = WallpaperDatabase.getInstance(requireContext())
        repository = WallpaperRepository(db.wallpaperDao())
        refreshButton?.setOnClickListener {
            // Show animation
            refreshButton?.visibility = View.GONE
            refreshProgress?.visibility = View.VISIBLE
            // Force refresh: trigger GalleryWallReceiver (same as widget)
            val intent = com.baysoft.gallerywall.GalleryWallReceiver.updateIntent(requireContext())
            requireContext().sendBroadcast(intent)
        }
        loadRecents()

        // Listen for wallpaper set broadcasts to update recents automatically
        wallpaperSetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                loadRecents()
                // Hide animation
                refreshButton?.visibility = View.VISIBLE
                refreshProgress?.visibility = View.GONE
            }
        }
        requireContext().registerReceiver(
            wallpaperSetReceiver,
            IntentFilter().apply {
                addAction("com.baysoft.gallerywall.WALLPAPER_SET")
                addAction(com.baysoft.gallerywall.GalleryWall.ACTION_REFRESH_IDLE)
            }
        )
    }

    private fun loadRecents() {
        lifecycleScope.launch {
            repository.cleanupOldWallpapers()
            val wallpapers = repository.getRecentWallpapers()
            adapter.submitList(wallpapers)
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
