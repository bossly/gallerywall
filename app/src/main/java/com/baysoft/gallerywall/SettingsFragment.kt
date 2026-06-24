package com.baysoft.gallerywall

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.PreferenceManager
import androidx.navigation.fragment.findNavController
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        Settings.migrateLegacyPrefsIfNeeded(prefs)

        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<ListPreference>(Settings.PREF_PERIOD)?.apply {
            summaryProvider = Preference.SummaryProvider<ListPreference> { pref ->
                val entry = pref.entry ?: ""
                getString(R.string.pref_period_summary_format, entry)
            }
        }

        findPreference<SwitchPreferenceCompat>(Settings.PREF_AUTO_WALLPAPER_ENABLED)?.run {
            updateDependentPrefs(isChecked)
            setOnPreferenceChangeListener { _, newValue ->
                val ctx = context ?: return@setOnPreferenceChangeListener true
                val enabled = newValue as Boolean
                updateDependentPrefs(enabled)
                if (enabled) {
                    GalleryWall.schedule(ctx.applicationContext)
                    // Request notification permission on Android 13+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                        }
                    }
                } else {
                    GalleryWall.cancelSchedule(ctx.applicationContext)
                }
                true
            }
        }

        listOf(
            Settings.PREF_CONSTRAINT_WIFI,
            Settings.PREF_CONSTRAINT_CHARGING,
            Settings.PREF_CONSTRAINT_IDLE,
            Settings.PREF_CONSTRAINT_BATTERY_LOW,
        ).forEach { key ->
            findPreference<CheckBoxPreference>(key)?.setOnPreferenceChangeListener { _, _ ->
                rescheduleIfEnabled()
                true
            }
        }

        findPreference<ListPreference>(Settings.PREF_PERIOD)?.setOnPreferenceChangeListener { _, _ ->
            rescheduleIfEnabled()
            true
        }

        findPreference<Preference>(Settings.PREF_WALLPAPER_SOURCE_NAV)?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.providersFragment)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        bindWallpaperSourceSummary()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_settings, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_help_title)
                    .setMessage(R.string.settings_help_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun rescheduleIfEnabled() {
        val ctx = context ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (Settings(prefs).autoWallpaperEnabled) {
            GalleryWall.schedule(ctx.applicationContext)
        }
    }

    private fun updateDependentPrefs(masterEnabled: Boolean) {
        findPreference<ListPreference>(Settings.PREF_PERIOD)?.isEnabled = masterEnabled
    }

    private fun bindWallpaperSourceSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val settings = Settings(prefs)
        val provider = WallpaperProviderRegistry.get(settings.activeProviderId)
            ?: WallpaperProviderRegistry.defaultProvider
        val name = getString(provider.titleRes)
        findPreference<Preference>(Settings.PREF_WALLPAPER_SOURCE_NAV)?.summary =
            getString(R.string.pref_wallpaper_source_summary_format, name)
    }
}
