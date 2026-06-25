package com.baysoft.gallerywall.ui

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.graphics.Bitmap
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.List
import androidx.compose.ui.focus.onFocusChanged
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
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
import androidx.compose.ui.res.stringResource
import com.baysoft.gallerywall.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.GalleryWall
import com.baysoft.gallerywall.PromptFilter
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.ImageGenerationService
import com.baysoft.gallerywall.data.WallpaperDatabase
import com.baysoft.gallerywall.data.WallpaperEntity
import com.baysoft.gallerywall.data.WallpaperRepository
import com.baysoft.gallerywall.provider.ProviderState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.baysoft.gallerywall.provider.ProviderReadiness
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry
import com.baysoft.gallerywall.ui.theme.GalleryWallTheme

private const val TAG = "GalleryScreen"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    modifier: Modifier = Modifier,
    onNavigateToProviders: () -> Unit = {},
    showPromptOverlay: Boolean = false,
    onDismissPromptOverlay: () -> Unit = {},
    triggerDirectGeneration: Boolean = false,
    onDirectGenerationStarted: () -> Unit = {}
) {
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
    val activeModelPath = settings.activeModelPath
    val isModelSelected = remember(activeModelPath) {
        !activeModelPath.isNullOrEmpty() && File(activeModelPath).exists()
    }

    val isCurrentlyGenerating = isGenerating || (
        activeProviderId == "local_ai" && (
            serviceState is ImageGenerationService.GenerationState.LoadingModel ||
            serviceState is ImageGenerationService.GenerationState.Generating
        )
    )

    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    var showGenerationWarning by remember { mutableStateOf(false) }
    var promptText by remember {
        mutableStateOf(prefs.getString(Settings.PREF_AUTOMATION_PROMPT, "") ?: "")
    }

    val performGeneration: () -> Unit = {
        val providerId = settings.activeProviderId

        // Auto-set default prompt if empty
        if (promptText.isBlank()) {
            val defaultPrompt = Settings.DEFAULT_AUTOMATION_PROMPT
            promptText = defaultPrompt
            prefs.edit { putString(Settings.PREF_AUTOMATION_PROMPT, defaultPrompt) }
        }

        if (PromptFilter.containsInappropriateContent(promptText)) {
            Toast.makeText(context, "Inappropriate prompt detected. Please try another one.", Toast.LENGTH_LONG).show()
        } else if (providerId == "local_ai") {
            val activeModelPath = settings.activeModelPath
            if (activeModelPath.isNullOrEmpty() || !File(activeModelPath).exists()) {
                Toast.makeText(context, "No model loaded. Please download the Stable Diffusion model first.", Toast.LENGTH_LONG).show()
            } else {
                val resolvedPrompt = promptText
                val colors = settings.generatedColorsHex
                ImageGenerationService.start(context, resolvedPrompt, activeModelPath, colors, autoApply = false)
            }
        } else {
            scope.launch {
                isGenerating = true
                currentProviderState = null
                try {
                    val filePath = withContext(Dispatchers.IO) {
                        val bitmap = GalleryWall.createWallpaperBitmap(context) { state ->
                            scope.launch {
                                currentProviderState = state
                            }
                        }
                        
                        // Don't apply automatically on Gallery tab
                        val path = GalleryWall.recordWallpaperSync(context, bitmap, applied = false)
                        
                        // Post notification with Apply button and preview
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        val notification = com.baysoft.gallerywall.GalleryWallNotifications.buildRefreshNotification(
                            context,
                            bitmap,
                            filePath = path,
                            isAlreadyApplied = false
                        )
                        notificationManager.notify(com.baysoft.gallerywall.GalleryWallNotifications.NOTIFICATION_ID, notification)
                        
                        path
                    }
                } catch (e: CancellationException) {
                    Log.i(TAG, "Generation cancelled by user")
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.cancel(com.baysoft.gallerywall.GalleryWallNotifications.PROGRESS_NOTIFICATION_ID)
                    refreshRecents()
                    isGenerating = false
                    currentProviderState = null
                }
            }
        }
    }

    val onGenerate: () -> Unit = {
        val resolvedPrompt = promptText.ifBlank { Settings.DEFAULT_AUTOMATION_PROMPT }
        Log.d(TAG, "onGenerate started with prompt: '$resolvedPrompt'")
        onDismissPromptOverlay()
        if (settings.activeProviderId == "local_ai") {
            showGenerationWarning = true
        } else {
            performGeneration()
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

    LaunchedEffect(triggerDirectGeneration) {
        if (triggerDirectGeneration) {
            onDirectGenerationStarted()
            onGenerate()
        }
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
        isModelSelected = isModelSelected,
        onNavigateToProviders = onNavigateToProviders,
        showErrorDialog = showErrorDialog,
        promptText = promptText,
        onPromptTextChange = {
            promptText = it
            prefs.edit { putString(Settings.PREF_AUTOMATION_PROMPT, it) }
        },
        onGenerate = onGenerate,
        showPromptOverlay = showPromptOverlay,
        onDismissPromptOverlay = onDismissPromptOverlay,
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
        onDismissErrorDialog = { showErrorDialog = null },
        showGenerationWarning = showGenerationWarning,
        onProceedGeneration = performGeneration,
        onDismissWarningDialog = { showGenerationWarning = false }
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
    isModelSelected: Boolean,
    currentProviderState: ProviderState?,
    serviceState: ImageGenerationService.GenerationState,
    activeProviderId: String,
    showErrorDialog: String?,
    showGenerationWarning: Boolean,
    showPromptOverlay: Boolean,
    promptText: String,
    onPromptTextChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onDismissPromptOverlay: () -> Unit,
    onProceedGeneration: () -> Unit,
    onDismissWarningDialog: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onSelectWallpaper: (WallpaperEntity?) -> Unit,
    onDeleteWallpaper: (WallpaperEntity) -> Unit,
    onApplyWallpaper: (Bitmap) -> Unit,
    onDismissErrorDialog: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val activeProvider = remember(activeProviderId) {
        WallpaperProviderRegistry.get(activeProviderId)
    }
    val readiness = remember(activeProvider, isModelSelected) {
        activeProvider?.isReady(context) ?: ProviderReadiness.NONE
    }

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
                    text = if (readiness == ProviderReadiness.NONE) stringResource(R.string.ai_model_required) else stringResource(R.string.generate_first_wallpaper),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (readiness == ProviderReadiness.NONE) {
                        stringResource(R.string.missing_model_desc)
                    } else if (activeProviderId == "local_ai") {
                        stringResource(R.string.generate_first_ai_desc, Settings.DEFAULT_AUTOMATION_PROMPT)
                    } else {
                        stringResource(R.string.generate_first_other_desc, activeProvider?.let { context.getString(it.titleRes) } ?: stringResource(R.string.pref_wallpaper_source_title))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (readiness == ProviderReadiness.NONE) {
                    Button(onClick = { onNavigateToProviders() }) {
                        Icon(Icons.Default.List, contentDescription = stringResource(R.string.tab_providers))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.download_select_model))
                    }
                } else {
                    Button(onClick = { onGenerate() }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.generate))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.generate))
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.tab_wallpapers),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.wallpapers_summary),
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
                                        when (val state = serviceState) {
                                            is ImageGenerationService.GenerationState.LoadingModel -> {
                                                CircularProgressIndicator(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = stringResource(R.string.progress_loading_model),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = stringResource(R.string.progress_loading_weights),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                            is ImageGenerationService.GenerationState.Generating -> {
                                                LinearProgressIndicator(
                                                    progress = { state.progress },
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = stringResource(R.string.progress_generating),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = stringResource(R.string.progress_step, state.currentStep, state.totalSteps),
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
                                                    text = stringResource(R.string.progress_initializing),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        if (currentProviderState != null) {
                                            LinearProgressIndicator(
                                                progress = { currentProviderState.progress },
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = stringResource(R.string.progress_generating),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = currentProviderState.message ?: stringResource(R.string.progress_seamless_tile),
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
                                                text = stringResource(R.string.progress_generating),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = stringResource(R.string.progress_seamless_tile),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = {
                                            val provider = WallpaperProviderRegistry.get(activeProviderId)
                                                ?: WallpaperProviderRegistry.defaultProvider
                                            provider.stop(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_stop),
                                            contentDescription = stringResource(R.string.stop)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.stop))
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
                                    createBitmap(200, 300).apply {
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
                                                onApplyWallpaper(bitmap)
                                            }
                                        )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = stringResource(R.string.provider_preview_description),
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

        // Preview Detail Modal Dialog (Rendered as fullscreen Dialog window covering app bars)
        selectedWallpaper?.let { entity ->
            val isPreview = LocalInspectionMode.current
            val bitmap = remember(entity.filePath) {
                if (isPreview) {
                    createBitmap(200, 300).apply {
                        eraseColor(android.graphics.Color.DKGRAY)
                    }
                } else {
                    decodeWallpaperBitmap(entity.filePath)
                }
            }
            if (bitmap != null) {
                Dialog(
                    onDismissRequest = { onSelectWallpaper(null) },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.provider_preview_description),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Prompt overlay at the top
                            val previewText = entity.prompt.ifEmpty {
                                when {
                                    entity.providerId == "local_ai" || entity.filePath.contains("local_ai") -> stringResource(R.string.provider_ai_title)
                                    entity.providerId == "procedural" || entity.filePath.contains("tile_noise") || entity.filePath.contains("procedural") -> stringResource(R.string.provider_procedural_title)
                                    entity.providerId == "random_color" || entity.filePath.contains("random_color") -> stringResource(R.string.provider_color_title)
                                    else -> stringResource(R.string.wallpaper)
                                }
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
                                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
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
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_wallpaper))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.delete_wallpaper))
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
                                    Icon(Icons.Default.Done, contentDescription = stringResource(R.string.set_wallpaper))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.set_wallpaper))
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
                title = { Text(stringResource(R.string.dialog_failed_title)) },
                text = { Text(errorMessage) },
                confirmButton = {
                    TextButton(onClick = onDismissErrorDialog) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }

        if (showGenerationWarning) {
            AlertDialog(
                onDismissRequest = onDismissWarningDialog,
                title = { Text(stringResource(R.string.dialog_start_title)) },
                text = { Text(stringResource(R.string.dialog_start_desc)) },
                confirmButton = {
                    Button(onClick = {
                        onDismissWarningDialog()
                        onProceedGeneration()
                    }) {
                        Text(stringResource(R.string.proceed))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissWarningDialog) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showPromptOverlay) {
            PromptInputOverlay(
                promptText = promptText,
                onPromptTextChange = onPromptTextChange,
                onDismiss = onDismissPromptOverlay,
                onGenerate = onGenerate
            )
        }
    }
}

private fun decodeWallpaperBitmap(path: String): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    return try {
        val source = android.graphics.ImageDecoder.createSource(file)
        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (_: Exception) {
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
            isModelSelected = true,
            showErrorDialog = null,
            showGenerationWarning = false,
            showPromptOverlay = false,
            promptText = "Describe your wallpaper...",
            onPromptTextChange = {},
            onGenerate = {},
            onDismissPromptOverlay = {},
            onProceedGeneration = {},
            onDismissWarningDialog = {},
            onNavigateToProviders = {},
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
            isModelSelected = true,
            showErrorDialog = null,
            showGenerationWarning = false,
            showPromptOverlay = false,
            promptText = "",
            onPromptTextChange = {},
            onGenerate = {},
            onDismissPromptOverlay = {},
            onProceedGeneration = {},
            onDismissWarningDialog = {},
            onNavigateToProviders = {},
            onSelectWallpaper = {},
            onDeleteWallpaper = {},
            onApplyWallpaper = {},
            onDismissErrorDialog = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GalleryScreenNoModelPreview() {
    GalleryWallTheme {
        GalleryScreenContent(
            wallpapers = emptyList(),
            selectedWallpaper = null,
            isLoading = false,
            isCurrentlyGenerating = false,
            currentProviderState = null,
            serviceState = ImageGenerationService.GenerationState.Idle,
            activeProviderId = "local_ai",
            isModelSelected = false,
            showErrorDialog = null,
            showGenerationWarning = false,
            showPromptOverlay = false,
            promptText = "",
            onPromptTextChange = {},
            onGenerate = {},
            onDismissPromptOverlay = {},
            onProceedGeneration = {},
            onDismissWarningDialog = {},
            onNavigateToProviders = {},
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
            isModelSelected = true,
            showErrorDialog = null,
            showGenerationWarning = false,
            showPromptOverlay = false,
            promptText = "A cute fluffy kitten",
            onPromptTextChange = {},
            onGenerate = {},
            onDismissPromptOverlay = {},
            onProceedGeneration = {},
            onDismissWarningDialog = {},
            onNavigateToProviders = {},
            onSelectWallpaper = {},
            onDeleteWallpaper = {},
            onApplyWallpaper = {},
            onDismissErrorDialog = {}
        )
    }
}
