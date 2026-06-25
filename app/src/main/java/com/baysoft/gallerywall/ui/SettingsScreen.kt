package com.baysoft.gallerywall.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.GalleryWall
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.ui.theme.GalleryWallTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val prefs = remember { if (isPreview) null else PreferenceManager.getDefaultSharedPreferences(context) }

    // State bindings
    var autoEnabled by remember { mutableStateOf(prefs?.getBoolean(Settings.PREF_AUTO_WALLPAPER_ENABLED, false) ?: false) }
    var autoApply by remember { mutableStateOf(prefs?.getBoolean(Settings.PREF_AUTO_APPLY_WALLPAPER, true) ?: true) }
    var promptTemplate by remember { mutableStateOf(prefs?.getString(Settings.PREF_AUTOMATION_PROMPT, Settings.DEFAULT_AUTOMATION_PROMPT) ?: Settings.DEFAULT_AUTOMATION_PROMPT) }
    var periodValue by remember { mutableStateOf(prefs?.getString(Settings.PREF_PERIOD, "1h") ?: "1s") }
    var periodUnit by remember { mutableStateOf(prefs?.getString(Settings.PREF_PERIOD_UNIT, Settings.DEFAULT_PERIOD_UNIT) ?: Settings.DEFAULT_PERIOD_UNIT) }
    
    var constraintCharging by remember { mutableStateOf(prefs?.getBoolean(Settings.PREF_CONSTRAINT_CHARGING, true) ?: true) }
    var constraintIdle by remember { mutableStateOf(prefs?.getBoolean(Settings.PREF_CONSTRAINT_IDLE, true) ?: true) }
    var postProcessingFilter by remember { mutableStateOf(prefs?.getString(Settings.PREF_POST_PROCESSING_FILTER, "none") ?: "none") }

    val activeProviderId = remember(prefs) { prefs?.getString(Settings.PREF_WALLPAPER_PROVIDER, "local_ai") ?: "local_ai" }
    val activeModelPath = remember(prefs) { prefs?.getString(Settings.PREF_ACTIVE_MODEL_PATH, null) }
    val isModelSelected = remember(activeModelPath) {
        !activeModelPath.isNullOrEmpty() && java.io.File(activeModelPath).exists()
    }

    var showNoModelWarning by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled by system, nothing specific needed here for now
    }

    // Helper to persist and reschedule WorkManager tasks
    val saveAndReschedule = {
        if (!isPreview && prefs != null) {
            val editor = prefs.edit()
            editor.putBoolean(Settings.PREF_AUTO_WALLPAPER_ENABLED, autoEnabled)
            editor.putBoolean(Settings.PREF_AUTO_APPLY_WALLPAPER, autoApply)
            editor.putString(Settings.PREF_AUTOMATION_PROMPT, promptTemplate)
            editor.putString(Settings.PREF_PERIOD, periodValue)
            editor.putString(Settings.PREF_PERIOD_UNIT, periodUnit)
            editor.putBoolean(Settings.PREF_CONSTRAINT_CHARGING, constraintCharging)
            editor.putBoolean(Settings.PREF_CONSTRAINT_IDLE, constraintIdle)
            editor.putString(Settings.PREF_POST_PROCESSING_FILTER, postProcessingFilter)
            editor.apply()
            
            // Reschedule WorkManager
            GalleryWall.schedule(context)
        }
    }

    if (showNoModelWarning) {
        AlertDialog(
            onDismissRequest = { showNoModelWarning = false },
            title = { Text("No Provider Activated") },
            text = { Text("You've selected 'On-Device AI' as your wallpaper provider, but no model is currently downloaded or active. Automation might fail until you setup a model in the Providers tab.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNoModelWarning = false
                        autoEnabled = true
                        saveAndReschedule()
                    }
                ) {
                    Text("Enable Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoModelWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    SettingsScreenContent(
        modifier = modifier,
        autoEnabled = autoEnabled,
        onAutoEnabledChange = {
            if (it && activeProviderId == "local_ai" && !isModelSelected) {
                showNoModelWarning = true
            } else {
                autoEnabled = it
                saveAndReschedule()
                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        },
        autoApply = autoApply,
        onAutoApplyChange = {
            autoApply = it
            saveAndReschedule()
        },
        promptTemplate = promptTemplate,
        onPromptTemplateChange = {
            promptTemplate = it
            saveAndReschedule()
        },
        periodValue = periodValue,
        onPeriodValueChange = {
            periodValue = it
            saveAndReschedule()
        },
        periodUnit = periodUnit,
        onPeriodUnitChange = {
            periodUnit = it
            saveAndReschedule()
        },
        constraintCharging = constraintCharging,
        onConstraintChargingChange = {
            constraintCharging = it
            saveAndReschedule()
        },
        constraintIdle = constraintIdle,
        onConstraintIdleChange = {
            constraintIdle = it
            saveAndReschedule()
        },
        postProcessingFilter = postProcessingFilter,
        onPostProcessingFilterChange = {
            postProcessingFilter = it
            saveAndReschedule()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    modifier: Modifier = Modifier,
    autoEnabled: Boolean,
    onAutoEnabledChange: (Boolean) -> Unit,
    autoApply: Boolean,
    onAutoApplyChange: (Boolean) -> Unit,
    promptTemplate: String,
    onPromptTemplateChange: (String) -> Unit,
    periodValue: String,
    onPeriodValueChange: (String) -> Unit,
    periodUnit: String,
    onPeriodUnitChange: (String) -> Unit,
    constraintCharging: Boolean,
    onConstraintChargingChange: (Boolean) -> Unit,
    constraintIdle: Boolean,
    onConstraintIdleChange: (Boolean) -> Unit,
    postProcessingFilter: String,
    onPostProcessingFilterChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Configure app-wide preferences, rotation frequency, and execution constraints.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        // Master Switch Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (autoEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Auto-Wallpaper Switcher",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Automatically update wallpaper on a period you define.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Switch(
                    checked = autoEnabled,
                    onCheckedChange = onAutoEnabledChange
                )
            }
        }

        if (autoEnabled) {
            // Auto Apply Switch Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Apply automatically",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Change wallpaper immediately when automation runs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Switch(
                        checked = autoApply,
                        onCheckedChange = onAutoApplyChange
                    )
                }
            }

            // Prompt Input Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Wallpaper Prompt",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Define standard keywords used for background generations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = promptTemplate,
                        onValueChange = onPromptTemplateChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Prompt") },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Text
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        maxLines = 3
                    )
                }
            }

            // Scheduling Period Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Schedule Interval",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Adjust the interval duration below to set how often background generations occur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val maxVal = when (periodUnit) {
                        "HOURS" -> 24f
                        "DAYS" -> 30f
                        "WEEKS" -> 12f
                        "MONTHS" -> 12f
                        else -> 24f
                    }
                    val minVal = 1f
                    val steps = (maxVal - minVal).toInt() - 1
                    val currentValue = (periodValue.toFloatOrNull() ?: 6f).coerceIn(minVal, maxVal)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Every ${currentValue.toInt()} ${periodUnit.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Period Unit Dropdown Selector
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("Unit: $periodUnit")
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                val units = listOf("HOURS", "DAYS", "WEEKS", "MONTHS")
                                units.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit) },
                                        onClick = {
                                            dropdownExpanded = false
                                            // Adjust period value if it exceeds max for new unit
                                            val newMax = when (unit) {
                                                "HOURS" -> 24f
                                                "DAYS" -> 30f
                                                "WEEKS" -> 12f
                                                "MONTHS" -> 12f
                                                else -> 24f
                                            }
                                            val adjusted = currentValue.coerceIn(1f, newMax)
                                            onPeriodUnitChange(unit)
                                            onPeriodValueChange(adjusted.toInt().toString())
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = currentValue,
                        onValueChange = {
                            onPeriodValueChange(it.toInt().toString())
                        },
                        valueRange = minVal..maxVal,
                        steps = steps,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // Background System Constraints
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Execution Constraints",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure conditions to preserve battery and processor performance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Charging constraint
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Only When Device is Charging", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = constraintCharging,
                            onCheckedChange = onConstraintChargingChange
                        )
                    }

                    // Idle constraint
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Only When Device is Idle", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = constraintIdle,
                            onCheckedChange = onConstraintIdleChange
                        )
                    }
                }
            }
        }

        // Post-processing Filter Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Post-processing Filter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Apply a visual filter to the generated wallpaper.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                var filterDropdownExpanded by remember { mutableStateOf(false) }
                val filterLabels = mapOf(
                    "none" to "None",
                    "bw" to "Black & White",
                    "sepia" to "Sepia",
                    "invert" to "Invert",
                    "blur" to "Blur"
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { filterDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(text = "Filter: ${filterLabels[postProcessingFilter] ?: "None"}")
                    }
                    DropdownMenu(
                        expanded = filterDropdownExpanded,
                        onDismissRequest = { filterDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        filterLabels.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onPostProcessingFilterChange(value)
                                    filterDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    GalleryWallTheme {
        SettingsScreen()
    }
}
