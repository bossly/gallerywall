package com.baysoft.gallerywall.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.GalleryWall
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.ImageGenerationService
import com.baysoft.gallerywall.data.WallpaperDatabase
import com.baysoft.gallerywall.data.WallpaperEntity
import com.baysoft.gallerywall.data.WallpaperRepository
import com.baysoft.gallerywall.provider.ProviderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.baysoft.gallerywall.ui.theme.GalleryWallTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // DB Repository
    val repository = remember {
        val db = WallpaperDatabase.getInstance(context)
        WallpaperRepository(db.wallpaperDao())
    }

    var wallpapers by remember { mutableStateOf<List<WallpaperEntity>>(emptyList()) }
    var selectedWallpaper by remember { mutableStateOf<WallpaperEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isGenerating by remember { mutableStateOf(false) }
    var currentProviderState by remember { mutableStateOf<ProviderState?>(null) }

    // Helper to refresh recents
    val refreshRecents = {
        scope.launch {
            isLoading = true
            val list = withContext(Dispatchers.IO) {
                repository.getRecentWallpapers()
            }
            wallpapers = list
            isLoading = false
        }
    }

    val serviceState by ImageGenerationService.state.collectAsState()
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val settings = remember(prefs) { Settings(prefs) }
    val activeProviderId = settings.activeProviderId

    val isCurrentlyGenerating = isGenerating || (
        activeProviderId == "local_ai" && (
            serviceState is ImageGenerationService.GenerationState.LoadingModel ||
            serviceState is ImageGenerationService.GenerationState.Generating
        )
    )

    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    var promptText by remember {
        mutableStateOf(prefs.getString(Settings.PREF_AUTOMATION_PROMPT, Settings.DEFAULT_AUTOMATION_PROMPT) ?: Settings.DEFAULT_AUTOMATION_PROMPT)
    }

    val onGenerate: () -> Unit = {
        val providerId = settings.activeProviderId
        if (providerId == "local_ai") {
            val activeModelPath = settings.activeModelPath
            if (activeModelPath.isNullOrEmpty() || !File(activeModelPath).exists()) {
                Toast.makeText(context, "No model loaded. Please download the Stable Diffusion model first.", Toast.LENGTH_LONG).show()
            } else {
                val resolvedPrompt = try {
                    com.baysoft.gallerywall.ml.DynamicPromptParser.parse(context, promptText)
                } catch (e: Exception) {
                    promptText
                }
                val colors = settings.generatedColorsHex
                ImageGenerationService.start(context, resolvedPrompt, activeModelPath, colors)
            }
        } else {
            scope.launch {
                isGenerating = true
                currentProviderState = null
                try {
                    val generated = withContext(Dispatchers.IO) {
                        val bitmap = GalleryWall.createWallpaperBitmap(context) { state ->
                            scope.launch {
                                currentProviderState = state
                            }
                        }
                        GalleryWall.updateWallpaper(context, bitmap)
                        val file = File(context.filesDir, "wallpaper_${providerId}_${System.currentTimeMillis()}.jpg")
                        java.io.FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        val filePath = file.absolutePath
                        GalleryWall.rememberAppliedWallpaperPath(context, filePath)
                        repository.addWallpaper(filePath, providerId, promptText)
                        true
                    }
                    if (generated) {
                        Toast.makeText(context, "New wallpaper generated and applied!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    refreshRecents()
                    isGenerating = false
                    currentProviderState = null
                }
            }
        }
    }

    LaunchedEffect(serviceState) {
        when (serviceState) {
            is ImageGenerationService.GenerationState.Success -> {
                refreshRecents()
            }
            is ImageGenerationService.GenerationState.Error -> {
                showErrorDialog = (serviceState as ImageGenerationService.GenerationState.Error).message
            }
            else -> {}
        }
    }

    // Load once on composition
    LaunchedEffect(Unit) {
        refreshRecents()
    }

    GalleryScreenContent(
        modifier = modifier,
        wallpapers = wallpapers,
        selectedWallpaper = selectedWallpaper,
        isLoading = isLoading,
        isCurrentlyGenerating = isCurrentlyGenerating,
        currentProviderState = currentProviderState,
        serviceState = serviceState,
        activeProviderId = activeProviderId,
        showErrorDialog = showErrorDialog,
        promptText = promptText,
        onPromptTextChange = {
            promptText = it
            prefs.edit().putString(Settings.PREF_AUTOMATION_PROMPT, it).apply()
        },
        onGenerate = onGenerate,
        onSelectWallpaper = { selectedWallpaper = it },
        onDeleteWallpaper = { entity ->
            scope.launch {
                repository.deleteWallpaper(entity)
                if (selectedWallpaper == entity) {
                    selectedWallpaper = null
                }
                refreshRecents()
                Toast.makeText(context, "Deleted wallpaper", Toast.LENGTH_SHORT).show()
            }
        },
        onApplyWallpaper = { bitmap ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    GalleryWall.updateWallpaper(context, bitmap)
                }
                Toast.makeText(context, "Wallpaper Applied!", Toast.LENGTH_SHORT).show()
                selectedWallpaper = null
            }
        },
        onDismissErrorDialog = { showErrorDialog = null }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreenContent(
    modifier: Modifier = Modifier,
    wallpapers: List<WallpaperEntity>,
    selectedWallpaper: WallpaperEntity?,
    isLoading: Boolean,
    isCurrentlyGenerating: Boolean,
    currentProviderState: ProviderState?,
    serviceState: ImageGenerationService.GenerationState,
    activeProviderId: String,
    showErrorDialog: String?,
    promptText: String,
    onPromptTextChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onSelectWallpaper: (WallpaperEntity?) -> Unit,
    onDeleteWallpaper: (WallpaperEntity) -> Unit,
    onApplyWallpaper: (Bitmap) -> Unit,
    onDismissErrorDialog: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (wallpapers.isEmpty() && !isCurrentlyGenerating) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Generate Your First Wallpaper",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Generate your first wallpaper using on-device AI model with the default prompt: '${Settings.DEFAULT_AUTOMATION_PROMPT}'",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { onGenerate() }) {
                    Icon(Icons.Default.Add, contentDescription = "Generate")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate")
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Historical Wallpapers",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Long press any wallpaper to delete or apply directly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isCurrentlyGenerating) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.6f)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (activeProviderId == "local_ai") {
                                        val currentState = serviceState
                                        when (currentState) {
                                            is ImageGenerationService.GenerationState.LoadingModel -> {
                                                CircularProgressIndicator(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = "Loading Model...",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = "Loading weights to internal storage",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                            is ImageGenerationService.GenerationState.Generating -> {
                                                LinearProgressIndicator(
                                                    progress = currentState.progress,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = "Generating...",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = "Step: ${currentState.currentStep}/${currentState.totalSteps}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                            else -> {
                                                CircularProgressIndicator(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = "Initializing...",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        val pState = currentProviderState
                                        if (pState != null) {
                                            LinearProgressIndicator(
                                                progress = pState.progress,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Generating...",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = pState.message ?: "Creating seamless tile",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                textAlign = TextAlign.Center
                                            )
                                        } else {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Generating...",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "Creating seamless tile",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(wallpapers) { entity ->
                        val file = File(entity.filePath)
                        val isPreview = LocalInspectionMode.current
                        if (file.exists() || isPreview) {
                            val bitmap = remember(entity.filePath) {
                                if (isPreview) {
                                    Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888).apply {
                                        eraseColor(android.graphics.Color.DKGRAY)
                                    }
                                } else {
                                    decodeWallpaperBitmap(entity.filePath)
                                }
                            }
                            if (bitmap != null) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.6f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            onClick = { onSelectWallpaper(entity) },
                                            onLongClick = {
                                                onDeleteWallpaper(entity)
                                            }
                                        )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Wallpaper preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Date of generation label
                                        val dateText = remember(entity.dateAdded) {
                                            java.text.DateFormat.getDateTimeInstance(
                                                java.text.DateFormat.SHORT,
                                                java.text.DateFormat.SHORT
                                            ).format(java.util.Date(entity.dateAdded))
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .align(Alignment.TopEnd)
                                        ) {
                                            Text(
                                                text = dateText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom prompt input bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = onPromptTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Describe your wallpaper...") },
                    maxLines = 2,
                    shape = MaterialTheme.shapes.medium,
                    enabled = !isCurrentlyGenerating
                )
                FilledIconButton(
                    onClick = {
                        focusManager.clearFocus()
                        if (isCurrentlyGenerating) return@FilledIconButton
                        onGenerate()
                    },
                    enabled = !isCurrentlyGenerating,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isCurrentlyGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Generate wallpaper")
                    }
                }
            }
        }

        // Preview Detail Modal Dialog
        selectedWallpaper?.let { entity ->
            val isPreview = LocalInspectionMode.current
            val bitmap = remember(entity.filePath) {
                if (isPreview) {
                    Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(android.graphics.Color.DKGRAY)
                    }
                } else {
                    decodeWallpaperBitmap(entity.filePath)
                }
            }
            if (bitmap != null) {
                Dialog(
                    onDismissRequest = { onSelectWallpaper(null) },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Full wallpaper preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Prompt overlay at the top
                            val previewText = entity.prompt.ifEmpty {
                                val isMl = entity.providerId == "local_ai" || entity.filePath.contains("local_ai")
                                val isProcedural = entity.providerId == "procedural" || entity.filePath.contains("tile_noise") || entity.filePath.contains("procedural")
                                val isColor = entity.providerId == "random_color" || entity.filePath.contains("random_color")
                                if (isMl) "Local AI" else if (isProcedural) "Procedural" else if (isColor) "Color" else "Pattern"
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 48.dp, start = 24.dp, end = 24.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = previewText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            // Controls bar at the bottom
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(32.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    onClick = {
                                        onDeleteWallpaper(entity)
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delete")
                                }

                                Button(
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    onClick = {
                                        onApplyWallpaper(bitmap)
                                    }
                                ) {
                                    Icon(Icons.Default.Done, contentDescription = "Set Wallpaper")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Apply Wallpaper")
                                }
                            }
                        }
                    }
                }
            }
        }

        showErrorDialog?.let { errorMessage ->
            AlertDialog(
                onDismissRequest = onDismissErrorDialog,
                title = { Text("Generation Failed") },
                text = { Text(errorMessage) },
                confirmButton = {
                    TextButton(onClick = onDismissErrorDialog) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

private fun decodeWallpaperBitmap(path: String): android.graphics.Bitmap? {
    val file = java.io.File(path)
    if (!file.exists()) return null
    return try {
        val source = android.graphics.ImageDecoder.createSource(file)
        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: Exception) {
        android.graphics.BitmapFactory.decodeFile(path)
    }
}

@Preview(showBackground = true)
@Composable
fun GalleryScreenPreview() {
    GalleryWallTheme {
        GalleryScreenContent(
            wallpapers = listOf(
                WallpaperEntity(
                    id = 1,
                    filePath = "mock_path_1.jpg",
                    dateAdded = System.currentTimeMillis() - 3600000,
                    providerId = "local_ai",
                    prompt = "A futuristic city in cyberpunk style, neon lights"
                ),
                WallpaperEntity(
                    id = 2,
                    filePath = "mock_path_2.jpg",
                    dateAdded = System.currentTimeMillis() - 7200000,
                    providerId = "procedural",
                    prompt = "Geometric seamless tile pattern with green and blue shades"
                ),
                WallpaperEntity(
                    id = 3,
                    filePath = "mock_path_3.jpg",
                    dateAdded = System.currentTimeMillis() - 10800000,
                    providerId = "random_color",
                    prompt = "Warm sunset colors gradient"
                )
            ),
            selectedWallpaper = null,
            isLoading = false,
            isCurrentlyGenerating = false,
            currentProviderState = null,
            serviceState = ImageGenerationService.GenerationState.Idle,
            activeProviderId = "local_ai",
            showErrorDialog = null,
            promptText = "Describe your wallpaper...",
            onPromptTextChange = {},
            onGenerate = {},
            onSelectWallpaper = {},
            onDeleteWallpaper = {},
            onApplyWallpaper = {},
            onDismissErrorDialog = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GalleryScreenEmptyPreview() {
    GalleryWallTheme {
        GalleryScreenContent(
            wallpapers = emptyList(),
            selectedWallpaper = null,
            isLoading = false,
            isCurrentlyGenerating = false,
            currentProviderState = null,
            serviceState = ImageGenerationService.GenerationState.Idle,
            activeProviderId = "local_ai",
            showErrorDialog = null,
            promptText = "",
            onPromptTextChange = {},
            onGenerate = {},
            onSelectWallpaper = {},
            onDeleteWallpaper = {},
            onApplyWallpaper = {},
            onDismissErrorDialog = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GalleryScreenGeneratingPreview() {
    GalleryWallTheme {
        GalleryScreenContent(
            wallpapers = emptyList(),
            selectedWallpaper = null,
            isLoading = false,
            isCurrentlyGenerating = true,
            currentProviderState = null,
            serviceState = ImageGenerationService.GenerationState.Generating(0.45f, 9, 20),
            activeProviderId = "local_ai",
            showErrorDialog = null,
            promptText = "A cute fluffy kitten",
            onPromptTextChange = {},
            onGenerate = {},
            onSelectWallpaper = {},
            onDeleteWallpaper = {},
            onApplyWallpaper = {},
            onDismissErrorDialog = {}
        )
    }
}
