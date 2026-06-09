package com.baysoft.gallerywall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.data.WallpaperDatabase
import com.baysoft.gallerywall.data.WallpaperRepository
import com.baysoft.gallerywall.ml.DynamicPromptParser
import com.baysoft.gallerywall.ml.LocalMLEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class ImageGenerationService : Service() {

    sealed class GenerationState {
        object Idle : GenerationState()
        object LoadingModel : GenerationState()
        data class Generating(val progress: Float, val currentStep: Int, val totalSteps: Int) : GenerationState()
        data class Success(val filePath: String) : GenerationState()
        data class Error(val message: String) : GenerationState()
    }

    companion object {
        private const val TAG = "ImageGenService"
        private const val NOTIFICATION_ID = 2026
        private const val CHANNEL_ID = "image_generation_channel"

        const val EXTRA_PROMPT = "extra_prompt"
        const val EXTRA_MODEL_PATH = "extra_model_path"
        const val EXTRA_COLORS_HEX = "extra_colors_hex"

        private val _state = MutableStateFlow<GenerationState>(GenerationState.Idle)
        val state: StateFlow<GenerationState> = _state.asStateFlow()

        fun start(context: Context, prompt: String, modelPath: String, colorsHex: String) {
            val intent = Intent(context, ImageGenerationService::class.java).apply {
                putExtra(EXTRA_PROMPT, prompt)
                putExtra(EXTRA_MODEL_PATH, modelPath)
                putExtra(EXTRA_COLORS_HEX, colorsHex)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prompt = intent?.getStringExtra(EXTRA_PROMPT) ?: ""
        val modelPath = intent?.getStringExtra(EXTRA_MODEL_PATH) ?: ""

        startForeground(NOTIFICATION_ID, buildNotification("Initializing service..."))

        activeJob?.cancel()
        activeJob = serviceScope.launch {
            try {
                _state.value = GenerationState.LoadingModel
                updateNotification("Loading AI model directory...")

                val context = applicationContext
                val engine = LocalMLEngine.getInstance()

                // 1. Load the model on background Dispatchers.Default
                val modelLoaded = withContext(Dispatchers.Default) {
                    if (modelPath.isNotEmpty()) {
                        val file = File(modelPath)
                        if (file.exists() && file.isDirectory) {
                            engine.loadModel(context, modelPath)
                        } else false
                    } else false
                }

                if (!modelLoaded) {
                    val errorDetail = engine.lastLoadError ?: "Please download the model package first."
                    throw IllegalStateException("Failed to load Stable Diffusion model. $errorDetail")
                }

                updateNotification("Generating wallpaper...")
                
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val settings = Settings(prefs)

                // 2. Perform progressive generation on Dispatchers.Default
                val rawTile = withContext(Dispatchers.Default) {
                    engine.generateTileProgressively(
                        prompt = prompt,
                        steps = 20,
                        seed = -1,
                        supportTransparency = false
                    ) { step, total ->
                        val progress = step.toFloat() / total.toFloat()
                        _state.value = GenerationState.Generating(progress, step, total)
                        updateNotification("Generating image: step $step/$total")
                    }
                }

                if (rawTile == null) {
                    throw IllegalStateException("MediaPipe AI generator returned a null bitmap.")
                }

                // 3. Upscale & repeat tile to wallpaper size
                val scaleFactor = settings.scaleFactor
                val scaledSize = rawTile.width * scaleFactor
                val generatedTile = if (scaleFactor > 1) {
                    Bitmap.createScaledBitmap(rawTile, scaledSize, scaledSize, true)
                } else {
                    rawTile
                }

                val wallpaperBmp = WallpaperGenerator.renderTiledWallpaper(context, generatedTile)

                // 4. Update wallpaper and database
                GalleryWall.updateWallpaper(context, wallpaperBmp)

                val file = File(context.filesDir, "wallpaper_local_ai_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    wallpaperBmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                val filePath = file.absolutePath
                GalleryWall.rememberAppliedWallpaperPath(context, filePath)

                val db = WallpaperDatabase.getInstance(context)
                val repo = WallpaperRepository(db.wallpaperDao())
                repo.addWallpaper(filePath, "local_ai", prompt)

                // Notify UI to update recents list
                context.sendBroadcast(Intent("com.baysoft.gallerywall.WALLPAPER_SET"))

                _state.value = GenerationState.Success(filePath)
                stopForeground(true)
                stopSelf()

            } catch (e: CancellationException) {
                Log.i(TAG, "Image generation cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Generation service failed", e)
                _state.value = GenerationState.Error(e.message ?: "Image generation failed")
                showErrorNotification(e.message ?: "Image generation failed")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        activeJob?.cancel()
        serviceScope.cancel()
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GalleryWall On-Device AI")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun showErrorNotification(message: String) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GalleryWall AI Failed")
            .setContentText(message)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AI Image Generation"
            val descriptionText = "Notifications for on-device AI wallpaper generation progress"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
