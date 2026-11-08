package com.sae.waifucompanion

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FloatingWaifuService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var overlayLayout: FrameLayout? = null
    private var webView: WebView? = null

    companion object {
        const val ACTION_STOP_OVERLAY = "com.sae.waifucompanion.STOP_OVERLAY"
        private const val NOTIFICATION_CHANNEL_ID = "sae_overlay_channel"
        private const val NOTIFICATION_ID = 4040
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        createFloatingView()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Sae Waifu Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, FloatingWaifuService::class.java).apply {
            action = ACTION_STOP_OVERLAY
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val activityPendingIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Sae AI Waifu Overlay")
            .setContentText("Avatar is floating on your home screen.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(activityPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close Overlay", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createFloatingView() {
        // WindowManager Layout Params (Translucent & Interactive)
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            600, // Initial overlay width
            850, // Initial overlay height
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        // Parent FrameLayout Container (Transparent background)
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        overlayLayout = rootLayout

        // Secondary Transparent WebView
        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.resources?.forEach { res ->
                        if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                            request.grant(arrayOf(res))
                            return
                        }
                    }
                    request?.deny()
                }
            }
        }
        rootLayout.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Overlay Close Button
        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            setOnClickListener { stopSelf() }
        }
        val btnParams = FrameLayout.LayoutParams(64, 64).apply {
            gravity = Gravity.TOP or Gravity.END
            marginEnd = 8
            topMargin = 8
        }
        rootLayout.addView(closeBtn, btnParams)

        // Drag to move listener
        rootLayout.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(rootLayout, params)
                        return true
                    }
                }
                return false
            }
        })

        // Fetch URL from DataStore and Load
        val repository = SettingsRepository(applicationContext)
        serviceScope.launch {
            val ip = repository.serverIpFlow.first()
            val port = repository.serverPortFlow.first()
            val url = "http://$ip:$port"
            webView?.loadUrl(url)
        }

        windowManager.addView(rootLayout, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        overlayLayout?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
        }
        webView?.destroy()
        webView = null
        overlayLayout = null
    }
}