package com.baysoft.gallerywall.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.provider.WallpaperProvider
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class ProvidersFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: ProviderListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_providers, container, false)
        recyclerView = view.findViewById(R.id.recycler_providers)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        listAdapter = ProviderListAdapter(
            providers = WallpaperProviderRegistry.all(),
            onSelect = { provider ->
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putString(Settings.PREF_WALLPAPER_PROVIDER, provider.id)
                    .apply()
                listAdapter.selectedId = provider.id
                listAdapter.notifyDataSetChanged()
                Snackbar.make(view, R.string.provider_selected, Snackbar.LENGTH_SHORT).show()
            },
            onConfigure = { provider ->
            },
        )
        recyclerView.adapter = listAdapter
        return view
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        listAdapter.selectedId = Settings(prefs).activeProviderId
        listAdapter.notifyDataSetChanged()
    }

    private class ProviderListAdapter(
        private val providers: List<WallpaperProvider>,
        private val onSelect: (WallpaperProvider) -> Unit,
        private val onConfigure: (WallpaperProvider) -> Unit,
    ) : RecyclerView.Adapter<ProviderListAdapter.VH>() {

        var selectedId: String = ""

        override fun getItemCount(): Int = providers.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_provider, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val provider = providers[position]
            val ctx = holder.itemView.context
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)

            holder.title.text = ctx.getString(provider.titleRes)
            holder.summary.text = ctx.getString(provider.summaryRes)
            holder.radio.isChecked = provider.id == selectedId
            holder.card.setOnClickListener {
                if (provider.id != selectedId) {
                    onSelect(provider)
                }
            }
            holder.configure.setOnClickListener {
                onConfigure(provider)
            }
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: View = itemView
            val preview: android.widget.ImageView = itemView.findViewById(R.id.preview)
            val radio: android.widget.RadioButton = itemView.findViewById(R.id.radio)
            val title: android.widget.TextView = itemView.findViewById(R.id.text_title)
            val summary: android.widget.TextView = itemView.findViewById(R.id.text_summary)
            val configure: MaterialButton = itemView.findViewById(R.id.btnConfigure)
        }
    }
}
