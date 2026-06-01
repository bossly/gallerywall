package com.baysoft.gallerywall.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.GalleryWall
import com.baysoft.gallerywall.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    // State bindings
    var autoEnabled by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_AUTO_WALLPAPER_ENABLED, false)) }
    var promptTemplate by remember { mutableStateOf(prefs.getString(Settings.PREF_AUTOMATION_PROMPT, Settings.DEFAULT_AUTOMATION_PROMPT) ?: Settings.DEFAULT_AUTOMATION_PROMPT) }
    var periodValue by remember { mutableStateOf(prefs.getString(Settings.PREF_PERIOD, Settings.DEFAULT_PERIOD_MINUTES_STRING) ?: Settings.DEFAULT_PERIOD_MINUTES_STRING) }
    var periodUnit by remember { mutableStateOf(prefs.getString(Settings.PREF_PERIOD_UNIT, Settings.DEFAULT_PERIOD_UNIT) ?: Settings.DEFAULT_PERIOD_UNIT) }
    
    var constraintWifi by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_CONSTRAINT_WIFI, true)) }
    var constraintCharging by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_CONSTRAINT_CHARGING, false)) }
    var constraintIdle by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_CONSTRAINT_IDLE, false)) }

    // Helper to persist and reschedule WorkManager tasks
    val saveAndReschedule = {
        val editor = prefs.edit()
        editor.putBoolean(Settings.PREF_AUTO_WALLPAPER_ENABLED, autoEnabled)
        editor.putString(Settings.PREF_AUTOMATION_PROMPT, promptTemplate)
        editor.putString(Settings.PREF_PERIOD, periodValue)
        editor.putString(Settings.PREF_PERIOD_UNIT, periodUnit)
        editor.putBoolean(Settings.PREF_CONSTRAINT_WIFI, constraintWifi)
        editor.putBoolean(Settings.PREF_CONSTRAINT_CHARGING, constraintCharging)
        editor.putBoolean(Settings.PREF_CONSTRAINT_IDLE, constraintIdle)
        editor.apply()
        
        // Reschedule WorkManager
        GalleryWall.schedule(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Automation Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Setup background automatic wallpaper rotation frequency and execution constraints.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        // Master Switch Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (autoEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(20.dp),
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
                    onCheckedChange = {
                        autoEnabled = it
                        saveAndReschedule()
                        val msg = if (it) "Auto-Wallpaper Enabled" else "Auto-Wallpaper Disabled"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Prompt Input Configuration Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Wallpaper Prompt Template",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Define standard keywords. Supports dynamic bracket tags like [TimeOfDay], [Season], or [Weather] which resolve live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = promptTemplate,
                    onValueChange = {
                        promptTemplate = it
                        saveAndReschedule()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AI / Shading Prompt Template") },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                // Quick Inject Tags
                Text(
                    text = "Quick Inject Variables:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val injectTag = { tag: String ->
                        if (!promptTemplate.contains(tag)) {
                            promptTemplate = if (promptTemplate.isEmpty() || promptTemplate.endsWith(" ") || promptTemplate.endsWith(",")) {
                                "$promptTemplate$tag"
                            } else {
                                "$promptTemplate, $tag"
                            }
                            saveAndReschedule()
                        }
                    }

                    AssistChip(
                        onClick = { injectTag("[TimeOfDay]") },
                        label = { Text("[TimeOfDay]") }
                    )
                    AssistChip(
                        onClick = { injectTag("[Season]") },
                        label = { Text("[Season]") }
                    )
                    AssistChip(
                        onClick = { injectTag("[Weather]") },
                        label = { Text("[Weather]") }
                    )
                }
            }
        }

        // Scheduling Period Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Schedule Interval",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "How often background generations occur (clamped to 15 min minimum).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = periodValue,
                        onValueChange = {
                            periodValue = it
                            saveAndReschedule()
                        },
                        modifier = Modifier.width(100.dp),
                        label = { Text("Count") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    // Period Unit Dropdown Selector
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
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
                                        periodUnit = unit
                                        dropdownExpanded = false
                                        saveAndReschedule()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Background System Constraints
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Execution Constraints",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure conditions to preserve battery, mobile data, and processor performance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Wifi constraint
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Only on Wi-Fi Networks", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = constraintWifi,
                        onCheckedChange = {
                            constraintWifi = it
                            saveAndReschedule()
                        }
                    )
                }

                // Charging constraint
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Only When Device is Charging", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = constraintCharging,
                        onCheckedChange = {
                            constraintCharging = it
                            saveAndReschedule()
                        }
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
                        onCheckedChange = {
                            constraintIdle = it
                            saveAndReschedule()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}
