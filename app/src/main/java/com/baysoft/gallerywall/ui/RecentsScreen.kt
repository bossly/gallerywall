package com.baysoft.gallerywall.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.GalleryWall
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.ImageGenerationService
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.data.WallpaperDatabase
import com.baysoft.gallerywall.data.WallpaperEntity
import com.baysoft.gallerywall.data.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(modifier: Modifier = Modifier) {
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

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (wallpapers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No Wallpapers Found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Generate a wallpaper using dynamic AI prompts or seamless noise, and it will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { refreshRecents() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh")
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
                                    val currentState = serviceState
                                    if (activeProviderId == "local_ai") {
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

                    items(wallpapers) { entity ->
                        val file = File(entity.filePath)
                        if (file.exists()) {
                            val bitmap = remember(entity.filePath) {
                                decodeWallpaperBitmap(entity.filePath)
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
                                            onClick = { selectedWallpaper = entity },
                                            onLongClick = {
                                                scope.launch {
                                                    repository.deleteWallpaper(entity)
                                                    refreshRecents()
                                                    Toast.makeText(context, "Deleted wallpaper", Toast.LENGTH_SHORT).show()
                                                }
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

                                        // Dynamic prompt label & engine icon selection
                                        val (badgeLabel, badgeIcon) = remember(entity) {
                                            val isMl = entity.providerId == "local_ai" || entity.filePath.contains("local_ai")
                                            val isProcedural = entity.providerId == "procedural" || entity.filePath.contains("tile_noise") || entity.filePath.contains("procedural")
                                            
                                            val labelStr = if (entity.prompt.isNotEmpty()) {
                                                entity.prompt
                                            } else {
                                                if (isMl) "Local AI" else if (isProcedural) "Procedural" else "Pattern"
                                            }
                                            val iconVal = if (isMl) Icons.Default.Star else Icons.Default.Build
                                            labelStr to iconVal
                                        }
                                        
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .align(Alignment.BottomStart)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = badgeIcon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = badgeLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1
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
        }

        // Floating Action Button to trigger generation
        FloatingActionButton(
            onClick = {
                if (isCurrentlyGenerating) return@FloatingActionButton
                val providerId = settings.activeProviderId
                if (providerId == "local_ai") {
                    val activeModelPath = settings.activeModelPath
                    if (activeModelPath.isNullOrEmpty() || !File(activeModelPath).exists()) {
                        Toast.makeText(context, "No model loaded. Please download the Stable Diffusion model first.", Toast.LENGTH_LONG).show()
                        return@FloatingActionButton
                    }
                    val rawPrompt = settings.automationPrompt
                    val resolvedPrompt = try {
                        com.baysoft.gallerywall.ml.DynamicPromptParser.parse(context, rawPrompt)
                    } catch (e: Exception) {
                        rawPrompt
                    }
                    val colors = settings.generatedColorsHex
                    ImageGenerationService.start(context, resolvedPrompt, activeModelPath, colors)
                } else {
                    scope.launch {
                        isGenerating = true
                        try {
                            val generated = withContext(Dispatchers.IO) {
                                val bitmap = GalleryWall.createWallpaperBitmap(context)
                                GalleryWall.updateWallpaper(context, bitmap)
                                val file = File(context.filesDir, "wallpaper_${providerId}_${System.currentTimeMillis()}.jpg")
                                java.io.FileOutputStream(file).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                }
                                val filePath = file.absolutePath
                                GalleryWall.rememberAppliedWallpaperPath(context, filePath)
                                
                                val promptStr = if (providerId == "procedural") {
                                    try {
                                        com.baysoft.gallerywall.ml.DynamicPromptParser.parse(context, settings.automationPrompt)
                                    } catch (e: Exception) {
                                        settings.automationPrompt
                                    }
                                } else ""
                                
                                repository.addWallpaper(filePath, providerId, promptStr)
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
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 96.dp, end = 16.dp)
        ) {
            Icon(painter = painterResource(id = R.drawable.ic_dice), contentDescription = "Generate random wallpaper")
        }

        // Preview Detail Modal Dialog
        selectedWallpaper?.let { entity ->
            val bitmap = remember(entity.filePath) {
                decodeWallpaperBitmap(entity.filePath)
            }
            if (bitmap != null) {
                Dialog(
                    onDismissRequest = { selectedWallpaper = null },
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
                                        scope.launch {
                                            repository.deleteWallpaper(entity)
                                            selectedWallpaper = null
                                            refreshRecents()
                                            Toast.makeText(context, "Deleted wallpaper", Toast.LENGTH_SHORT).show()
                                        }
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
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                GalleryWall.updateWallpaper(context, bitmap)
                                            }
                                            Toast.makeText(context, "Wallpaper Applied!", Toast.LENGTH_SHORT).show()
                                            selectedWallpaper = null
                                        }
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
                onDismissRequest = { showErrorDialog = null },
                title = { Text("Generation Failed") },
                text = { Text(errorMessage) },
                confirmButton = {
                    TextButton(onClick = { showErrorDialog = null }) {
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
