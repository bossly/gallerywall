package com.baysoft.gallerywall.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.ml.LocalMLEngine
import com.baysoft.gallerywall.ui.theme.GalleryWallTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class GgufModel(
    val id: String,
    val name: String,
    val description: String,
    val size: String,
    val downloadUrl: String,
    val sha256: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current
    val prefs = remember { if (isPreview) null else PreferenceManager.getDefaultSharedPreferences(context) }
    
    var activeModelPath by remember { mutableStateOf(prefs?.getString(Settings.PREF_ACTIVE_MODEL_PATH, null)) }
    var selectedScale by remember { mutableStateOf(prefs?.getInt(Settings.PREF_SCALE_FACTOR, Settings.DEFAULT_SCALE_FACTOR) ?: Settings.DEFAULT_SCALE_FACTOR) }
    val downloadingStates = remember { mutableStateMapOf<String, String>() }

    val baseDir = remember { 
        if (isPreview) {
            File("")
        } else {
            context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        }
    }
    val getModelFile = { id: String ->
        if (isPreview) File("") else File(baseDir, id)
    }

    var refreshTrigger by remember { mutableStateOf(0) }
    var modelsList by remember { mutableStateOf<List<GgufModel>>(emptyList()) }

    LaunchedEffect(refreshTrigger) {
        val builtIn = listOf(
            GgufModel(
                id = "stable_diffusion_1_5",
                name = "Stable Diffusion v1.5 (Lite)",
                description = "On-device Stable Diffusion v1.5 text-to-image model. Zipped package contains pre-converted sub-model components optimized for MediaPipe Tasks Vision.",
                size = "1.8 GB",
                downloadUrl = "https://github.com/bossly/gallerywall/releases/download/2.3.1/stable_diffusion_v1_5.zip",
                sha256 = "56680bed991bc2fe5785504d3bfaa4ce495b62f0535c7f3b72eb206ef09cfcf4"
            ),
//            GgufModel(
//                id = "bk_sdm_small",
//                name = "BK-SDM-Small (0.49B-param U-Net)",
//                description = "On-device BK-SDM-Small text-to-image model. Zipped package contains pre-converted sub-model components optimized for MediaPipe Tasks Vision.",
//                size = "1.41 GB",
//                downloadUrl = "https://huggingface.co/nota-ai/bk-sdm-small/resolve/main/bk-sdm-small.zip"
//            ),
//            GgufModel(
//                id = "bk_sdm_base",
//                name = "BK-SDM-Base (0.58B-param U-Net)",
//                description = "On-device BK-SDM-Base text-to-image model. Zipped package contains pre-converted sub-model components optimized for MediaPipe Tasks Vision.",
//                size = "1.56 GB",
//                downloadUrl = "https://huggingface.co/nota-ai/bk-sdm-base/resolve/main/bk-sdm-base.zip"
//            )
        )

        val list = builtIn.toMutableList()
        val builtInIds = builtIn.map { it.id }.toSet()

        if (!isPreview && baseDir.exists() && baseDir.isDirectory) {
            val subDirs = baseDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            for (dir in subDirs) {
                if (dir.name !in builtInIds) {
                    val sizeBytes = getFolderSize(dir)
                    val sizeStr = formatSize(sizeBytes)
                    list.add(
                        GgufModel(
                            id = dir.name,
                            name = dir.name,
                            description = "Imported custom on-device Stable Diffusion model.",
                            size = sizeStr,
                            downloadUrl = ""
                        )
                    )
                }
            }
        }
        modelsList = list
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val fileNameWithExt = getFileName(context, uri) ?: "imported_model"
                    val modelId = if (fileNameWithExt.endsWith(".zip", ignoreCase = true)) {
                        fileNameWithExt.substring(0, fileNameWithExt.length - 4)
                    } else {
                        fileNameWithExt
                    }

                    Log.d("ProvidersScreen", "Selected model ZIP file for import via picker: $uri for model ID/Name: $modelId")
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

    var selectedProviderId by remember {
        mutableStateOf(prefs?.getString(Settings.PREF_WALLPAPER_PROVIDER, "local_ai") ?: "local_ai")
    }

    val allProviders = remember { com.baysoft.gallerywall.provider.WallpaperProviderRegistry.all() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Wallpaper Providers",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Choose a wallpaper generation engine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Provider selection list
        items(allProviders.size) { index ->
            val provider = allProviders[index]
            val isSelected = selectedProviderId == provider.id
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(MaterialTheme.shapes.large)
                    .clickable {
                        selectedProviderId = provider.id
                        prefs?.edit()?.putString(Settings.PREF_WALLPAPER_PROVIDER, provider.id)?.apply()
                        Toast.makeText(context, "Provider: ${context.getString(provider.titleRes)}", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
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
            }
        }

        // AI Model section – only visible when local_ai provider is selected
        if (selectedProviderId == "local_ai") {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "On-Device AI Models",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Button(
                        onClick = {
                            filePickerLauncher.launch("*/*")
                        }
                    ) {
                        Text("Import ZIP")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(modelsList) { model ->
                val file = getModelFile(model.id)
                val exists = file.exists() && file.isDirectory
                val isCurrentlyActive = activeModelPath == file.absolutePath

                // Display size dynamically (actual size on disk if downloaded/imported, otherwise fallback description size)
                val displaySize = remember(refreshTrigger, exists) {
                    if (exists) {
                        val sizeBytes = getFolderSize(file)
                        formatSize(sizeBytes)
                    } else {
                        model.size
                    }
                }

                key(refreshTrigger) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentlyActive) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(MaterialTheme.shapes.large)
                            .clickable(enabled = exists) {
                                activeModelPath = file.absolutePath
                                prefs?.edit()?.putString(Settings.PREF_ACTIVE_MODEL_PATH, file.absolutePath)?.apply()
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
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Size: $displaySize",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (isCurrentlyActive) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = "Active & Loaded",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else if (exists) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = "Downloaded",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
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
                                                file.deleteRecursively()
                                                if (isCurrentlyActive) {
                                                    LocalMLEngine.getInstance().unloadModel()
                                                    activeModelPath = null
                                                    prefs?.edit()?.remove(Settings.PREF_ACTIVE_MODEL_PATH)?.apply()
                                                }
                                                refreshTrigger++
                                                Toast.makeText(context, "Deleted model folder", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Model Folder",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                } else {
                                    val currentStatus = downloadingStates[model.id]
                                    if (currentStatus != null) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (currentStatus.startsWith("Error")) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = currentStatus,
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Button(
                                                        onClick = {
                                                            downloadingStates.remove(model.id)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    ) {
                                                        Text("Retry")
                                                    }
                                                }
                                            } else {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = currentStatus,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    } else {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (model.downloadUrl.isNotEmpty()) {
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                Log.d("ProvidersScreen", "Download button clicked for model: ${model.name} (${model.id})")
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
                                                                    
                                                                    val baseDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
                                                                    
                                                                    var errorMessage: String? = null
                                                                    val unzipSuccess = withContext(Dispatchers.IO) {
                                                                        try {
                                                                            if (model.sha256.isNotEmpty()) {
                                                                                downloadingStates[model.id] = "Verifying..."
                                                                                val actualSha = calculateSha256(localZip)
                                                                                if (actualSha != model.sha256) {
                                                                                    Log.e("ProvidersScreen", "Local zip SHA256 mismatch! Expected ${model.sha256}, got $actualSha")
                                                                                    errorMessage = "Local file corrupted (checksum mismatch)"
                                                                                    return@withContext false
                                                                                }
                                                                            }

                                                                            downloadingStates[model.id] = "Extracting..."
                                                                            baseDir.mkdirs()
                                                                            val targetDir = getModelFile(model.id)
                                                                            if (targetDir.exists()) {
                                                                                targetDir.deleteRecursively()
                                                                            }
                                                                            targetDir.mkdirs()
                                                                            localZip.inputStream().use { input ->
                                                                                unzip(input, targetDir)
                                                                            }
                                                                            true
                                                                        } catch (e: Exception) {
                                                                            Log.e("ProvidersScreen", "Error unzipping local model ZIP", e)
                                                                            errorMessage = "Extraction failed: ${e.message}"
                                                                            false
                                                                        }
                                                                    }
                                                                    if (unzipSuccess) {
                                                                        downloadingStates.remove(model.id)
                                                                        Toast.makeText(context, "Loaded model successfully from local ZIP!", Toast.LENGTH_SHORT).show()
                                                                    } else {
                                                                        Log.e("ProvidersScreen", "Failed to load/extract model from local ZIP for model ${model.id}")
                                                                        downloadingStates[model.id] = "Error: ${errorMessage ?: "Unknown error"}"
                                                                        Toast.makeText(context, "Failed to load model from local ZIP: $errorMessage", Toast.LENGTH_LONG).show()
                                                                    }
                                                                    refreshTrigger++
                                                                    return@launch
                                                                }
                                                                
                                                                val baseDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
                                                                val zipFile = File(baseDir, "${model.id}.zip")
                                                                if (zipFile.exists()) {
                                                                    zipFile.delete()
                                                                }
                                                                baseDir.mkdirs()

                                                                Log.d("ProvidersScreen", "Target local storage path: ${zipFile.absolutePath}")
                                                                val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
                                                                    .setTitle(model.name)
                                                                    .setDescription("Downloading on-device Stable Diffusion model package")
                                                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                                    .setDestinationUri(Uri.fromFile(zipFile))
                                                                    .setAllowedOverMetered(true)
                                                                    .setAllowedOverRoaming(false)

                                                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                                                val downloadId = dm.enqueue(request)

                                                                downloadingStates[model.id] = "Downloading..."
                                                                Toast.makeText(context, "Started download in background. Monitor via system tray progress.", Toast.LENGTH_LONG).show()
                                                                
                                                                scope.launch {
                                                                    var isDone = false
                                                                    var isError = false
                                                                    var errorMessage: String? = null
                                                                    
                                                                    while (!isDone && !isError) {
                                                                        kotlinx.coroutines.delay(1000)
                                                                        val q = DownloadManager.Query().setFilterById(downloadId)
                                                                        val c = dm.query(q)
                                                                        if (c != null && c.moveToFirst()) {
                                                                            val statusCol = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                                                            if (statusCol != -1) {
                                                                                val status = c.getInt(statusCol)
                                                                                when (status) {
                                                                                    DownloadManager.STATUS_SUCCESSFUL -> {
                                                                                        isDone = true
                                                                                    }
                                                                                    DownloadManager.STATUS_FAILED -> {
                                                                                        isError = true
                                                                                        val reasonCol = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                                                                                        val reason = if (reasonCol != -1) c.getInt(reasonCol) else -1
                                                                                        errorMessage = "Download failed: status code $reason"
                                                                                    }
                                                                                    DownloadManager.STATUS_PAUSED -> {
                                                                                        // still waiting/paused
                                                                                    }
                                                                                    DownloadManager.STATUS_RUNNING -> {
                                                                                        val totalCol = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                                                                        val currentCol = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                                                                        if (totalCol != -1 && currentCol != -1) {
                                                                                            val total = c.getLong(totalCol)
                                                                                            val current = c.getLong(currentCol)
                                                                                            if (total > 0) {
                                                                                                val percent = (current * 100 / total).toInt()
                                                                                                downloadingStates[model.id] = "Downloading ($percent%)"
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                            c.close()
                                                                        } else {
                                                                            isError = true
                                                                            errorMessage = "Download cancelled or failed"
                                                                        }
                                                                    }
                                                                    
                                                                    if (isError) {
                                                                        Log.e("ProvidersScreen", "Download failed for model ${model.id}: $errorMessage")
                                                                        downloadingStates[model.id] = "Error: $errorMessage"
                                                                        Toast.makeText(context, errorMessage ?: "Download failed", Toast.LENGTH_LONG).show()
                                                                    } else {
                                                                        val unzipSuccess = withContext(Dispatchers.IO) {
                                                                            try {
                                                                                if (model.sha256.isNotEmpty()) {
                                                                                    downloadingStates[model.id] = "Verifying..."
                                                                                    val actualSha = calculateSha256(zipFile)
                                                                                    if (actualSha != model.sha256) {
                                                                                        Log.e("ProvidersScreen", "Downloaded zip SHA256 mismatch! Expected ${model.sha256}, got $actualSha")
                                                                                        errorMessage = "Checksum mismatch! Corrupted download."
                                                                                        return@withContext false
                                                                                    }
                                                                                }

                                                                                downloadingStates[model.id] = "Extracting..."
                                                                                val targetDir = getModelFile(model.id)
                                                                                if (targetDir.exists()) {
                                                                                    targetDir.deleteRecursively()
                                                                                    }
                                                                                targetDir.mkdirs()
                                                                                zipFile.inputStream().use { input ->
                                                                                    unzip(input, targetDir)
                                                                                }
                                                                                true
                                                                            } catch (e: Exception) {
                                                                                Log.e("ProvidersScreen", "Error unzipping model for model ${model.id}", e)
                                                                                errorMessage = "Extraction failed: ${e.message}"
                                                                                false
                                                                            } finally {
                                                                                zipFile.delete()
                                                                            }
                                                                        }
                                                                        
                                                                        if (unzipSuccess) {
                                                                            downloadingStates.remove(model.id)
                                                                            Toast.makeText(context, "Loaded model successfully!", Toast.LENGTH_SHORT).show()
                                                                        } else {
                                                                            Log.e("ProvidersScreen", "Extraction failed for model ${model.id}: $errorMessage")
                                                                            downloadingStates[model.id] = "Error: $errorMessage"
                                                                            Toast.makeText(context, errorMessage ?: "Extraction failed", Toast.LENGTH_LONG).show()
                                                                        }
                                                                    }
                                                                    refreshTrigger++
                                                                }
                                                            } catch (e: Exception) {
                                                                Log.e("ProvidersScreen", "Exception caught during download initiation flow for model '${model.id}'", e)
                                                                downloadingStates[model.id] = "Error: ${e.localizedMessage}"
                                                                Toast.makeText(context, "Failed to download model: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Download")
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Download")
                                                }
                                            }
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

            /*
            // Engine Configuration
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Engine Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = MaterialTheme.shapes.large,
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
                                text = "Upscale: ${selectedScale}×",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Scale the native model output tile before repeating.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Slider(
                            value = selectedScale.toFloat(),
                            onValueChange = { selectedScale = it.toInt() },
                            onValueChangeFinished = {
                                prefs?.edit()?.putInt(Settings.PREF_SCALE_FACTOR, selectedScale)?.apply()
                            },
                            valueRange = 2f..12f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )

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
            }
            */
        }
    }
}

private fun getFolderSize(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    var size = 0L
    val files = file.listFiles()
    if (files != null) {
        for (f in files) {
            size += getFolderSize(f)
        }
    }
    return size
}

private fun formatSize(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.toDouble())).toInt()
    return String.format("%.2f %s", sizeInBytes / Math.pow(1024.toDouble(), digitGroups.toDouble()), units[digitGroups])
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
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

private fun calculateSha256(file: File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { fis ->
        val buffer = ByteArray(8192)
        var bytesRead = fis.read(buffer)
        while (bytesRead != -1) {
            digest.update(buffer, 0, bytesRead)
            bytesRead = fis.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

@Preview(showBackground = true)
@Composable
fun ProvidersScreenPreview() {
    GalleryWallTheme {
        ProvidersScreen()
    }
}
