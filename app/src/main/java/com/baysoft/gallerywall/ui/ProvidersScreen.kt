package com.baysoft.gallerywall.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GgufModel(
    val id: String,
    val name: String,
    val description: String,
    val size: String,
    val downloadUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    
    // Core model configs
    var activeProviderId by remember { mutableStateOf(prefs.getString(Settings.PREF_WALLPAPER_PROVIDER, "tile_noise") ?: "tile_noise") }
    var activeModelPath by remember { mutableStateOf(prefs.getString(Settings.PREF_ACTIVE_MODEL_PATH, null)) }
    var selectedScale by remember { mutableStateOf(prefs.getInt(Settings.PREF_SCALE_FACTOR, Settings.DEFAULT_SCALE_FACTOR)) }
    var activeColorsString by remember { mutableStateOf(prefs.getString(Settings.PREF_GENERATED_COLORS, Settings.DEFAULT_GENERATED_COLORS) ?: Settings.DEFAULT_GENERATED_COLORS) }

    // Parse active colors string to a list of dynamic hex values
    val colorList = remember(activeColorsString) {
        activeColorsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    // Modal state for custom visual Color Picker
    var editingColorIndex by remember { mutableStateOf<Int?>(null) }

    // Custom model preferences
    var customModelName by remember { mutableStateOf(prefs.getString("custom_model_name", "") ?: "") }
    var customModelUrl by remember { mutableStateOf(prefs.getString("custom_model_url", "") ?: "") }
    var showCustomModelDialog by remember { mutableStateOf(false) }

    // Fallback/Initial MediaPipe Stable Diffusion models list (pre-converted Stable Diffusion v1.5 directories in zip form)
    val fallbackModels = remember(customModelName, customModelUrl) {
        val list = mutableListOf(
            GgufModel(
                id = "stable_diffusion_1_5",
                name = "Stable Diffusion v1.5 (Lite)",
                description = "On-device Stable Diffusion v1.5 text-to-image model. Zipped package contains pre-converted sub-model components optimized for MediaPipe Tasks Vision.",
                size = "1.2 GB",
                downloadUrl = "https://huggingface.co/Qualcomm/Stable-Diffusion-v1.5/resolve/main/stable_diffusion_v1_5.zip"
            )
        )
        if (customModelName.isNotEmpty() && customModelUrl.isNotEmpty()) {
            list.add(
                GgufModel(
                    id = "custom_model",
                    name = customModelName,
                    description = "Custom user-provided Stable Diffusion v1.5 model directory zip package.",
                    size = "Custom",
                    downloadUrl = customModelUrl
                )
            )
        }
        list
    }

    var modelsList by remember { mutableStateOf(fallbackModels) }
    LaunchedEffect(fallbackModels) {
        modelsList = fallbackModels
    }
    var isFetchingList by remember { mutableStateOf(false) }

    // File exists helper (representing the unzipped directory containing Stable Diffusion model components)
    val getModelFile = { id: String ->
        val baseDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        File(baseDir, id)
    }

    // Refresh model state helper
    var refreshTrigger by remember { mutableStateOf(0) }
    var previewTrigger by remember { mutableStateOf(0) }

    var importingModelId by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val modelId = importingModelId ?: "stable_diffusion_1_5"
                    Log.d("ProvidersScreen", "Selected model ZIP file for import via picker: $uri for model ID: $modelId")
                    val baseDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
                    baseDir.mkdirs()

                    Toast.makeText(context, "Importing and unzipping model zip...", Toast.LENGTH_LONG).show()

                    withContext(Dispatchers.IO) {
                        val targetDir = getModelFile(modelId)
                        if (targetDir.exists()) {
                            targetDir.deleteRecursively()
                        }
                        targetDir.mkdirs()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            unzip(input, targetDir)
                        }
                    }
                    Toast.makeText(context, "Model imported successfully!", Toast.LENGTH_SHORT).show()
                    refreshTrigger++
                } catch (e: Exception) {
                    Log.e("ProvidersScreen", "Failed to import selected model zip", e)
                    Toast.makeText(context, "Failed to import model: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Fetch dynamic JSON hosted list from GitHub
    val fetchRemoteModels = {
        scope.launch {
            isFetchingList = true
            try {
                val fetched = withContext(Dispatchers.IO) {
                    val rawJson = URL("https://raw.githubusercontent.com/bossly/gallerywall/main/models.json").readText()
                    val list = mutableListOf<GgufModel>()
                    val regex = Regex("""\{\s*"id"\s*:\s*"([^"]+)"\s*,\s*"name"\s*:\s*"([^"]+)"\s*,\s*"description"\s*:\s*"([^"]+)"\s*,\s*"size"\s*:\s*"([^"]+)"\s*,\s*"downloadUrl"\s*:\s*"([^"]+)"\s*\}""")
                    regex.findAll(rawJson).forEach { match ->
                        list.add(
                            GgufModel(
                                id = match.groupValues[1],
                                name = match.groupValues[2],
                                description = match.groupValues[3],
                                size = match.groupValues[4],
                                downloadUrl = match.groupValues[5]
                            )
                        )
                    }
                    if (list.isEmpty()) fallbackModels else list
                }
                modelsList = fetched
                Toast.makeText(context, "Synced available models from GitHub!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                modelsList = fallbackModels
            } finally {
                isFetchingList = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchRemoteModels()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wallpaper Engines",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { fetchRemoteModels() },
                    enabled = !isFetchingList
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Sync available models list"
                    )
                }
            }
            Text(
                text = "Select active generation engine, download ML models dynamically, or configure resolution specs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section 1: Core Engine Providers Selection
        item {
            Text(
                text = "1. Active Rendering Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(WallpaperProviderRegistry.all()) { provider ->
            val isSelected = activeProviderId == provider.id
            val borderModifier = if (isSelected) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            } else Modifier

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .then(borderModifier)
                    .clickable {
                        activeProviderId = provider.id
                        prefs.edit().putString(Settings.PREF_WALLPAPER_PROVIDER, provider.id).apply()
                        Toast.makeText(context, "Set active: ${provider.id}", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(provider.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = context.getString(provider.summaryRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Expansible Prompt Editor for Local AI Provider when selected
                    if (provider.id == "local_ai" && isSelected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        var promptValue by remember {
                            mutableStateOf(prefs.getString(Settings.PREF_AUTOMATION_PROMPT, Settings.DEFAULT_AUTOMATION_PROMPT) ?: Settings.DEFAULT_AUTOMATION_PROMPT)
                        }
                        
                        OutlinedTextField(
                            value = promptValue,
                            onValueChange = {
                                promptValue = it
                                prefs.edit().putString(Settings.PREF_AUTOMATION_PROMPT, it).apply()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("AI Generator Prompt") },
                            maxLines = 2
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Mini Inject Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val inject = { tag: String ->
                                if (!promptValue.contains(tag)) {
                                    val newPrompt = if (promptValue.isEmpty() || promptValue.endsWith(" ") || promptValue.endsWith(",")) {
                                        "$promptValue$tag"
                                    } else {
                                        "$promptValue, $tag"
                                    }
                                    promptValue = newPrompt
                                    prefs.edit().putString(Settings.PREF_AUTOMATION_PROMPT, newPrompt).apply()
                                }
                            }
                            AssistChip(onClick = { inject("[TimeOfDay]") }, label = { Text("[TimeOfDay]") })
                            AssistChip(onClick = { inject("[Season]") }, label = { Text("[Season]") })
                            AssistChip(onClick = { inject("[Weather]") }, label = { Text("[Weather]") })
                        }
                    }
                }
            }
        }

        // Section 2: Generation Resolution Size Settings & Palette Pickers
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "2. Engine Configurations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tile Upscale Factor",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${selectedScale}× → ${64 * selectedScale}×${64 * selectedScale}px",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Scale the native 64×64 tile before tiling. Higher = larger tiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Slider(
                        value = selectedScale.toFloat(),
                        onValueChange = { selectedScale = it.toInt() },
                        onValueChangeFinished = {
                            prefs.edit().putInt(Settings.PREF_SCALE_FACTOR, selectedScale).apply()
                        },
                        valueRange = 2f..12f,
                        steps = 9, // 2,3,4,5,6,7,8,9,10,11,12 → 10 intervals, 9 intermediate stops
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Scale factor step labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("2×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("6×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("12×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Tile Preview ──────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tile Preview",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { previewTrigger++ }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Regenerate Preview",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Async preview generation
                    var previewBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    var previewLoading by remember { mutableStateOf(false) }
                    var previewError by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(previewTrigger, selectedScale, activeColorsString, activeProviderId) {
                        previewLoading = true
                        previewError = null
                        previewBitmap = null
                        try {
                            val bmp = withContext(Dispatchers.IO) {
                                val provider = WallpaperProviderRegistry.get(activeProviderId)
                                if (provider != null) {
                                    // Generate just the tile (not full wallpaper) for preview
                                    val prefsSnap = PreferenceManager.getDefaultSharedPreferences(context)
                                    val settingsSnap = Settings(prefsSnap)
                                    val colors = com.baysoft.gallerywall.WallpaperGenerator.parseColors(settingsSnap.generatedColorsHex)
                                    val promptTemplate = settingsSnap.automationPrompt
                                    val prompt = com.baysoft.gallerywall.ml.DynamicPromptParser.parse(context, promptTemplate)
                                    val tileSize = settingsSnap.scaleFactor * 64
                                    val seed = (0..Int.MAX_VALUE).random()
                                    com.baysoft.gallerywall.provider.ProceduralGenerator.generateSeamlessTile(tileSize, colors, prompt, seed)
                                } else null
                            }
                            if (bmp != null) {
                                previewBitmap = bmp.asImageBitmap()
                            } else {
                                previewError = "No provider selected"
                            }
                        } catch (e: Exception) {
                            previewError = e.message ?: "Preview failed"
                        }
                        previewLoading = false
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            previewLoading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            previewError != null -> {
                                Text(
                                    text = previewError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            previewBitmap != null -> {
                                androidx.compose.foundation.Image(
                                    bitmap = previewBitmap!!,
                                    contentDescription = "Tile Preview",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }
                    }

                    Text(
                        text = "Preview uses the procedural tile engine. Tap ↻ to regenerate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Visual Color Palette Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Harmonious Color Palette",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap a color circle to adjust, long press to remove, or tap '+' to add. Affects all generation modes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Row of interactive color circles & Add Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        colorList.forEachIndexed { index, hex ->
                            val parsedColor = remember(hex) {
                                try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(parsedColor)
                                    .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { editingColorIndex = index },
                                            onLongPress = {
                                                if (colorList.size > 1) {
                                                    colorList.removeAt(index)
                                                    activeColorsString = colorList.joinToString(",")
                                                    prefs.edit().putString(Settings.PREF_GENERATED_COLORS, activeColorsString).apply()
                                                    Toast.makeText(context, "Color removed", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Must keep at least 1 color", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                            )
                        }

                        // Outlined Add '+' Circle Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable {
                                    // Add a new purple accent color and open color picker instantly
                                    colorList.add("#6750A4")
                                    activeColorsString = colorList.joinToString(",")
                                    prefs.edit().putString(Settings.PREF_GENERATED_COLORS, activeColorsString).apply()
                                    editingColorIndex = colorList.size - 1
                                }
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add Color",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Randomize Palette Button
                        Button(
                            onClick = {
                                val randomized = generateHarmoniousPalette()
                                activeColorsString = randomized
                                prefs.edit().putString(Settings.PREF_GENERATED_COLORS, randomized).apply()
                                Toast.makeText(context, "Harmonious Palette Randomized!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Randomize Colors")
                        }

                        // Reset to M3 Defaults
                        OutlinedButton(
                            onClick = {
                                activeColorsString = Settings.DEFAULT_GENERATED_COLORS
                                prefs.edit().putString(Settings.PREF_GENERATED_COLORS, Settings.DEFAULT_GENERATED_COLORS).apply()
                                Toast.makeText(context, "Restored M3 Default Palette", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Defaults")
                        }
                    }
                }
            }
        }

        // Section 3: On-Device AI Models Download Manager (Visible only when local_ai provider is selected)
        if (activeProviderId == "local_ai") {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3. On-Device AI Models (MediaPipe)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = { showCustomModelDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Custom Model")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Custom URL")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(modelsList) { model ->
                val file = getModelFile(model.id)
                val exists = file.exists() && file.isDirectory
                val isCurrentlyActive = activeModelPath == file.absolutePath

                // Trigger reading trigger
                key(refreshTrigger) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentlyActive) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = exists) {
                                activeModelPath = file.absolutePath
                                prefs.edit().putString(Settings.PREF_ACTIVE_MODEL_PATH, file.absolutePath).apply()
                                Toast.makeText(context, "Active model set to: ${model.name}", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Size: ${model.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (exists) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (isCurrentlyActive) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Active Model",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                file.delete()
                                                if (isCurrentlyActive) {
                                                    activeModelPath = null
                                                    prefs.edit().remove(Settings.PREF_ACTIVE_MODEL_PATH).apply()
                                                }
                                                refreshTrigger++
                                                Toast.makeText(context, "Deleted model file", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Model File",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        Log.d("ProvidersScreen", "Download button clicked for model: ${model.name} (${model.id})")
                                                        Log.d("ProvidersScreen", "Model Download URL: ${model.downloadUrl}")
                                                        
                                                        // 1. Try direct local files in public Downloads directory first
                                                        val urlFilename = try {
                                                            Uri.parse(model.downloadUrl).lastPathSegment ?: "${model.id}.zip"
                                                        } catch (e: Exception) {
                                                            "${model.id}.zip"
                                                        }
                                                        
                                                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                                        val possibleDirs = listOf(
                                                            downloadsDir,
                                                            File("/sdcard/Download"),
                                                            File("/storage/emulated/0/Download")
                                                        )
                                                        
                                                        var localZip: File? = null
                                                        for (dir in possibleDirs) {
                                                            val file = File(dir, urlFilename)
                                                            if (file.exists() && file.canRead()) {
                                                                localZip = file
                                                                break
                                                            }
                                                            val fileAlt = File(dir, "${model.id}.zip")
                                                            if (fileAlt.exists() && fileAlt.canRead()) {
                                                                localZip = fileAlt
                                                                break
                                                            }
                                                        }

                                                        if (localZip != null) {
                                                            Log.d("ProvidersScreen", "Found local zip at: ${localZip.absolutePath}. Unzipping directly...")
                                                            Toast.makeText(context, "Found local ZIP in Downloads! Loading...", Toast.LENGTH_LONG).show()
                                                            
                                                            val baseDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
                                                            
                                                            withContext(Dispatchers.IO) {
                                                                baseDir.mkdirs()
                                                                val targetDir = getModelFile(model.id)
                                                                if (targetDir.exists()) {
                                                                    targetDir.deleteRecursively()
                                                                }
                                                                targetDir.mkdirs()
                                                                localZip.inputStream().use { input ->
                                                                    unzip(input, targetDir)
                                                                }
                                                            }
                                                            Toast.makeText(context, "Loaded model successfully from local ZIP!", Toast.LENGTH_SHORT).show()
                                                            refreshTrigger++
                                                            return@launch
                                                        }
                                                        
                                                        // 2. If no local readable zip, proceed with regular system DownloadManager
                                                        val baseDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
                                                        val zipFile = File(baseDir, "${model.id}.zip")
                                                        if (zipFile.exists()) {
                                                            zipFile.delete()
                                                        }
                                                        baseDir.mkdirs()

                                                        Log.d("ProvidersScreen", "Target local storage path: ${zipFile.absolutePath}")
                                                        Log.d("ProvidersScreen", "Configuring DownloadManager.Request...")
                                                        val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
                                                            .setTitle(model.name)
                                                            .setDescription("Downloading on-device Stable Diffusion model package")
                                                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                            .setDestinationUri(Uri.fromFile(zipFile))
                                                            .setAllowedOverMetered(true)
                                                            .setAllowedOverRoaming(false)

                                                        Log.d("ProvidersScreen", "Fetching system DOWNLOAD_SERVICE and enqueuing request...")
                                                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                                        val downloadId = dm.enqueue(request)
                                                        Log.d("ProvidersScreen", "Download enqueued successfully with system ID: $downloadId")

                                                        Toast.makeText(context, "Started download in background. Monitor via system tray progress.", Toast.LENGTH_LONG).show()
                                                        
                                                        // Poll for download completion and unzip
                                                        Log.d("ProvidersScreen", "Launching background file existence polling loop...")
                                                        scope.launch {
                                                            var pollIteration = 0
                                                            while (!zipFile.exists() || zipFile.length() == 0L) {
                                                                pollIteration++
                                                                Log.v("ProvidersScreen", "Polling check #$pollIteration: Zip file '${zipFile.name}' does not exist or empty yet. Waiting 2s...")
                                                                kotlinx.coroutines.delay(2000)
                                                            }
                                                            Log.i("ProvidersScreen", "Poller detected downloaded zip file exists! unzipping model package...")
                                                            
                                                            withContext(Dispatchers.IO) {
                                                                try {
                                                                    val targetDir = getModelFile(model.id)
                                                                    if (targetDir.exists()) {
                                                                        targetDir.deleteRecursively()
                                                                    }
                                                                    targetDir.mkdirs()
                                                                    zipFile.inputStream().use { input ->
                                                                        unzip(input, targetDir)
                                                                    }
                                                                    Log.i("ProvidersScreen", "Successfully unzipped model to directory: ${targetDir.absolutePath}")
                                                                } catch (e: Exception) {
                                                                    Log.e("ProvidersScreen", "Error unzipping model", e)
                                                                } finally {
                                                                    zipFile.delete() // clean up zip file
                                                                }
                                                            }
                                                            refreshTrigger++
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("ProvidersScreen", "Exception caught during download initiation flow for model '${model.id}'", e)
                                                        Toast.makeText(context, "Failed to download model: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Download")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                importingModelId = model.id
                                                filePickerLauncher.launch("*/*")
                                            }
                                        ) {
                                            Text("Import ZIP")
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Circular Hue Color Picker Dialog
    editingColorIndex?.let { index ->
        val initialHex = colorList.getOrNull(index) ?: "#FFFFFF"
        var selectedColorHex by remember { mutableStateOf(initialHex) }
        val activePickedColor = remember(selectedColorHex) {
            try { Color(android.graphics.Color.parseColor(selectedColorHex)) } catch (e: Exception) { Color.White }
        }

        Dialog(
            onDismissRequest = { editingColorIndex = null }
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Visual Color Picker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Dynamic Preview Area
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(activePickedColor)
                            .border(3.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), CircleShape)
                    )

                    Text(
                        text = "Hue Spectrum Wheel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Gorgeous Circular Sweep Gradient Color Wheel Canvas
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val sweepBrush = remember {
                            Brush.sweepGradient(
                                colors = listOf(
                                    Color.Red, Color.Magenta, Color.Blue, Color.Cyan,
                                    Color.Green, Color.Yellow, Color.Red
                                )
                            )
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = { offset ->
                                            val cx = size.width / 2f
                                            val cy = size.height / 2f
                                            val dx = offset.x - cx
                                            val dy = offset.y - cy
                                            val distance = sqrt(dx * dx + dy * dy)
                                            val radius = size.width / 2f
                                            
                                            // Only register if tap lands inside the ring boundary
                                            if (distance <= radius && distance >= radius - 30.dp.toPx()) {
                                                var angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                                                // Convert range from -PI..PI to 0..2*PI (degrees)
                                                var degrees = Math.toDegrees(angle.toDouble()).toFloat()
                                                if (degrees < 0) degrees += 360f
                                                
                                                selectedColorHex = hslToHex(degrees, 0.75f, 0.5f)
                                            }
                                        }
                                    )
                                }
                        ) {
                            // Draw circular color ring
                            drawCircle(
                                brush = sweepBrush,
                                radius = size.width / 2f - 15.dp.toPx(),
                                style = Stroke(width = 30.dp.toPx())
                            )
                        }
                    }

                    Text(
                        text = "Selected Hex: $selectedColorHex",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { editingColorIndex = null }) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (index < colorList.size) {
                                    colorList[index] = selectedColorHex
                                    activeColorsString = colorList.joinToString(",")
                                    prefs.edit().putString(Settings.PREF_GENERATED_COLORS, activeColorsString).apply()
                                }
                                editingColorIndex = null
                            }
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }

    if (showCustomModelDialog) {
        var tempName by remember { mutableStateOf(customModelName) }
        var tempUrl by remember { mutableStateOf(customModelUrl) }
        
        Dialog(onDismissRequest = { showCustomModelDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Add Custom Model ZIP",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Provide a public URL to download a zipped (.zip) folder containing your converted Stable Diffusion v1.5 model components.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Model Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Model ZIP Download URL") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCustomModelDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                customModelName = tempName
                                customModelUrl = tempUrl
                                prefs.edit().putString("custom_model_name", tempName).apply()
                                prefs.edit().putString("custom_model_url", tempUrl).apply()
                                showCustomModelDialog = false
                                refreshTrigger++
                            },
                            enabled = tempName.isNotEmpty() && tempUrl.isNotEmpty()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

// Top-level self-contained procedural analogues color helpers
private fun generateHarmoniousPalette(): String {
    val random = java.util.Random()
    val hue = random.nextFloat() * 360f
    
    // Analogous hue shifts (30 degree offset)
    val h1 = hue
    val h2 = (hue + 30f) % 360f
    val h3 = (hue + 60f) % 360f
    
    val s = 0.55f + random.nextFloat() * 0.15f // Premium soft saturation 55% - 70%
    
    // Lightness offsets to yield background, accent, and highlight
    val l1 = 0.15f + random.nextFloat() * 0.1f // Background / Dark base
    val l2 = 0.5f + random.nextFloat() * 0.1f  // Accent midtone
    val l3 = 0.8f + random.nextFloat() * 0.1f  // Bright highlight
    
    val c1 = hslToHex(h1, s, l1)
    val c2 = hslToHex(h2, s, l2)
    val c3 = hslToHex(h3, s, l3)
    
    return "$c1,$c2,$c3"
}

private fun hslToHex(h: Float, s: Float, l: Float): String {
    val c = (1f - Math.abs(2f * l - 1f)) * s
    val x = c * (1f - Math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    
    var r = 0f
    var g = 0f
    var b = 0f
    
    when {
        h < 60f -> { r = c; g = x }
        h < 120f -> { r = x; g = c }
        h < 180f -> { g = c; b = x }
        h < 240f -> { g = x; b = c }
        h < 300f -> { r = x; b = c }
        else -> { r = c; b = x }
    }
    
    val rInt = ((r + m) * 255f).toInt().coerceIn(0, 255)
    val gInt = ((g + m) * 255f).toInt().coerceIn(0, 255)
    val bInt = ((b + m) * 255f).toInt().coerceIn(0, 255)
    
    return String.format("#%02X%02X%02X", rInt, gInt, bInt)
}

private fun unzip(zipInputStream: java.io.InputStream, targetDirectory: File) {
    java.util.zip.ZipInputStream(java.io.BufferedInputStream(zipInputStream)).use { zis ->
        var ze = zis.nextEntry
        while (ze != null) {
            if (ze.name.startsWith("__MACOSX") || ze.name.contains(".DS_Store")) {
                ze = zis.nextEntry
                continue
            }
            val file = File(targetDirectory, ze.name)
            val dir = if (ze.isDirectory) file else file.parentFile
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw java.io.IOException("Failed to ensure directory: ${dir.absolutePath}")
            }
            if (!ze.isDirectory) {
                java.io.FileOutputStream(file).use { fos ->
                    zis.copyTo(fos)
                }
            }
            ze = zis.nextEntry
        }
    }
}
