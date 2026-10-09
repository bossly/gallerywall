package com.baysoft.gallerywall.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.data.AutomationHistoryEntity
import com.baysoft.gallerywall.data.AutomationHistoryRepository
import com.baysoft.gallerywall.data.WallpaperDatabase
import com.baysoft.gallerywall.ui.theme.GalleryWallTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()

    val repository = remember {
        if (isPreview) null else {
            val db = WallpaperDatabase.getInstance(context)
            AutomationHistoryRepository(db.automationHistoryDao())
        }
    }

    val historyList by if (repository != null) {
        repository.allHistory.collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(getPreviewHistoryList()) }
    }

    var showClearDialog by remember { mutableStateOf(false) }
    var selectedRecordForDetails by remember { mutableStateOf<AutomationHistoryEntity?>(null) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_history_confirm_title)) },
            text = { Text(stringResource(R.string.clear_history_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            repository?.clearAll()
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.clear_history),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (selectedRecordForDetails != null) {
        val record = selectedRecordForDetails!!
        HistoryDetailsDialog(
            record = record,
            onDismiss = { selectedRecordForDetails = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.automation_history_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                },
                actions = {
                    if (historyList.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.clear_history),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (historyList.isEmpty()) {
                EmptyHistoryView(modifier = Modifier.fillMaxSize())
            } else {
                // Summary metric cards
                val totalCount = historyList.size
                val successCount = historyList.count { it.status == AutomationHistoryEntity.STATUS_SUCCESS }
                val skippedCount = historyList.count { it.status == AutomationHistoryEntity.STATUS_SKIPPED }
                val failedCount = historyList.count { it.status == AutomationHistoryEntity.STATUS_FAILED }

                HistoryMetricsRow(
                    total = totalCount,
                    success = successCount,
                    skipped = skippedCount,
                    failed = failedCount
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(historyList, key = { it.id }) { record ->
                        HistoryRecordCard(
                            record = record,
                            onClick = { selectedRecordForDetails = record }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMetricsRow(
    total: Int,
    success: Int,
    skipped: Int,
    failed: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricBadge(
            label = stringResource(R.string.history_total_runs),
            count = total,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        MetricBadge(
            label = stringResource(R.string.history_successful_runs),
            count = success,
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f),
            contentColor = Color(0xFF2E7D32),
            modifier = Modifier.weight(1f)
        )
        MetricBadge(
            label = stringResource(R.string.history_skipped_runs),
            count = skipped,
            containerColor = Color(0xFFFF9800).copy(alpha = 0.15f),
            contentColor = Color(0xFFEF6C00),
            modifier = Modifier.weight(1f)
        )
        if (failed > 0) {
            MetricBadge(
                label = stringResource(R.string.history_failed_runs),
                count = failed,
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricBadge(
    label: String,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun HistoryRecordCard(
    record: AutomationHistoryEntity,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy  HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }

    val statusColor = when (record.status) {
        AutomationHistoryEntity.STATUS_SUCCESS -> Color(0xFF2E7D32)
        AutomationHistoryEntity.STATUS_SKIPPED -> Color(0xFFEF6C00)
        AutomationHistoryEntity.STATUS_FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val statusBg = when (record.status) {
        AutomationHistoryEntity.STATUS_SUCCESS -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        AutomationHistoryEntity.STATUS_SKIPPED -> Color(0xFFFF9800).copy(alpha = 0.15f)
        AutomationHistoryEntity.STATUS_FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }

    val statusLabel = when (record.status) {
        AutomationHistoryEntity.STATUS_SUCCESS -> stringResource(R.string.status_success)
        AutomationHistoryEntity.STATUS_SKIPPED -> stringResource(R.string.status_skipped)
        AutomationHistoryEntity.STATUS_FAILED -> stringResource(R.string.status_failed)
        else -> stringResource(R.string.status_running)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Status badge & formatted time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Prompt snippet & Provider
            if (record.prompt.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\"${record.prompt}\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (record.providerId.isNotBlank()) {
                            Text(
                                text = record.providerId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Constraint Chips Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Charging badge
                ConstraintBadge(
                    icon = "⚡",
                    text = if (record.isCharging) stringResource(R.string.constraint_status_charging) else stringResource(R.string.constraint_status_not_charging),
                    isPassed = !record.chargingRequired || record.isCharging,
                    modifier = Modifier.weight(1f)
                )

                // Idle badge
                ConstraintBadge(
                    icon = "📱",
                    text = if (record.isDeviceIdle) stringResource(R.string.constraint_status_idle) else stringResource(R.string.constraint_status_active),
                    isPassed = !record.idleRequired || record.isDeviceIdle,
                    modifier = Modifier.weight(1f)
                )

                // Battery badge
                if (record.batteryLevel >= 0) {
                    ConstraintBadge(
                        icon = "🔋",
                        text = "${record.batteryLevel}%",
                        isPassed = !record.batteryLowRequired || (record.batteryLevel >= 15 && !record.isPowerSaveMode),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reason / Output Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (record.statusReason.isNotBlank()) {
                        Text(
                            text = record.statusReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (record.status == AutomationHistoryEntity.STATUS_SUCCESS) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                statusColor
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (record.durationMs > 0) {
                        Text(
                            text = stringResource(R.string.history_record_duration_label, formatDuration(record.durationMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                // Thumbnail preview if generated
                if (!record.wallpaperFilePath.isNullOrEmpty() && File(record.wallpaperFilePath).exists()) {
                    AsyncImage(
                        model = File(record.wallpaperFilePath),
                        contentDescription = stringResource(R.string.wallpaper),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun ConstraintBadge(
    icon: String,
    text: String,
    isPassed: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isPassed) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    }

    val textColor = if (isPassed) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyHistoryView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_history),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_history_records),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_history_records_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun HistoryDetailsDialog(
    record: AutomationHistoryEntity,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val fullDate = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.history_details_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow("Timestamp", fullDate)
                DetailRow("Status", record.status)
                DetailRow("Can Start", if (record.canStart) "Yes" else "No")
                if (record.statusReason.isNotBlank()) {
                    DetailRow("Reason", record.statusReason)
                }
                if (record.prompt.isNotBlank()) {
                    DetailRow("Prompt", record.prompt)
                }
                if (record.providerId.isNotBlank()) {
                    DetailRow("Provider", record.providerId)
                }
                DetailRow("Charging", "${record.isCharging} (Required: ${record.chargingRequired})")
                DetailRow("Device Idle", "${record.isDeviceIdle} (Required: ${record.idleRequired})")
                DetailRow("Battery Level", "${record.batteryLevel}% (Power Save: ${record.isPowerSaveMode})")
                DetailRow("Model Ready", record.isModelReady.toString())
                if (record.durationMs > 0) {
                    DetailRow("Duration", formatDuration(record.durationMs))
                }
                if (!record.wallpaperFilePath.isNullOrEmpty()) {
                    DetailRow("File Path", record.wallpaperFilePath)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDuration(ms: Long): String {
    return if (ms < 1000) {
        "${ms}ms"
    } else {
        String.format(Locale.US, "%.1fs", ms / 1000f)
    }
}

private fun getPreviewHistoryList(): List<AutomationHistoryEntity> {
    return listOf(
        AutomationHistoryEntity(
            id = 1,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
            prompt = "seamless cute pastel floral pattern",
            providerId = "local_ai",
            isCharging = true,
            chargingRequired = true,
            isDeviceIdle = true,
            idleRequired = true,
            batteryLevel = 85,
            isPowerSaveMode = false,
            batteryLowRequired = true,
            isModelReady = true,
            canStart = true,
            status = AutomationHistoryEntity.STATUS_SUCCESS,
            statusReason = "Wallpaper generated and applied successfully",
            durationMs = 4200L
        ),
        AutomationHistoryEntity(
            id = 2,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 120,
            prompt = "seamless cute pastel floral pattern",
            providerId = "local_ai",
            isCharging = false,
            chargingRequired = true,
            isDeviceIdle = true,
            idleRequired = true,
            batteryLevel = 45,
            isPowerSaveMode = false,
            batteryLowRequired = true,
            isModelReady = true,
            canStart = false,
            status = AutomationHistoryEntity.STATUS_SKIPPED,
            statusReason = "Skipped: Device is not charging",
            durationMs = 0L
        )
    )
}

@Preview(showBackground = true)
@Composable
fun AutomationHistoryScreenPreview() {
    GalleryWallTheme {
        AutomationHistoryScreen(onNavigateBack = {})
    }
}
