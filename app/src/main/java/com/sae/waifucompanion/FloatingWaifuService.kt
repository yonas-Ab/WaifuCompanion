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
import android.graphics.drawable.GradientDrawable
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
import android.widget.LinearLayout
import android.widget.TextView
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, FloatingWaifuService::class.java).apply {
            action = ACTION_STOP_OVERLAY
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val activityPendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
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
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            (metrics.widthPixels * 0.82f).toInt(),
            (metrics.heightPixels * 0.68f).toInt(),
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(100)
        }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        overlayLayout = root

        val avatarWebView = WebView(this).apply {
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
                    request?.resources?.forEach { resource ->
                        if (resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                            request.grant(arrayOf(resource))
                            return
                        }
                    }
                    request?.deny()
                }
            }
        }
        webView = avatarWebView
        root.addView(avatarWebView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(190, 20, 24, 40))
        }
        val dragHandle = TextView(this).apply {
            text = "⠿  Move"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Drag to move floating companion"
        }
        header.addView(dragHandle, LinearLayout.LayoutParams(0, dp(52), 1f))

        val minimizeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Minimize floating companion"
        }
        header.addView(minimizeButton, LinearLayout.LayoutParams(dp(48), dp(52)))

        val closeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Close floating companion"
            setOnClickListener { stopSelf() }
        }
        header.addView(closeButton, LinearLayout.LayoutParams(dp(48), dp(52)))
        root.addView(header, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(52), Gravity.TOP
        ))

        val resizeHandle = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            setBackgroundColor(Color.argb(180, 20, 24, 40))
            contentDescription = "Resize floating companion"
        }
        root.addView(resizeHandle, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.END))

        val miniButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            contentDescription = "Restore floating companion"
            visibility = View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(38, 48, 82))
                setStroke(dp(2), Color.WHITE)
            }
            setOnClickListener {
                params.width = normalWidth
                params.height = normalHeight
                root.background = null
                avatarWebView.visibility = View.VISIBLE
                header.visibility = View.VISIBLE
                resizeHandle.visibility = View.VISIBLE
                miniButton.visibility = View.GONE
                windowManager.updateViewLayout(root, params)
            }
        }
        root.addView(miniButton, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER))

        var resizeStartWidth = 0
        var resizeStartHeight = 0
        var resizeStartX = 0f
        var resizeStartY = 0f
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeStartWidth = params.width
                    resizeStartHeight = params.height
                    resizeStartX = event.rawX
                    resizeStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val screen = resources.displayMetrics
                    params.width = (resizeStartWidth + (event.rawX - resizeStartX).toInt())
                        .coerceIn(dp(220), screen.widthPixels)
                    params.height = (resizeStartHeight + (event.rawY - resizeStartY).toInt())
                        .coerceIn(dp(280), screen.heightPixels)
                    windowManager.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        var dragStartX = 0
        var dragStartY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        val dragListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = params.x
                    dragStartY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val screen = resources.displayMetrics
                    params.x = (dragStartX + (event.rawX - touchStartX).toInt())
                        .coerceIn(0, (screen.widthPixels - params.width).coerceAtLeast(0))
                    params.y = (dragStartY + (event.rawY - touchStartY).toInt())
                        .coerceIn(0, (screen.heightPixels - params.height).coerceAtLeast(0))
                    windowManager.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        dragHandle.setOnTouchListener(dragListener)
        miniButton.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_MOVE &&
                (kotlin.math.abs(event.rawX - touchStartX) > dp(8) || kotlin.math.abs(event.rawY - touchStartY) > dp(8))) {
                dragListener.onTouch(view, event)
            } else if (event.action == MotionEvent.ACTION_DOWN) {
                dragListener.onTouch(view, event)
            } else if (event.action == MotionEvent.ACTION_UP) {
                val moved = kotlin.math.abs(event.rawX - touchStartX) > dp(8) ||
                        kotlin.math.abs(event.rawY - touchStartY) > dp(8)
                if (!moved) miniButton.performClick()
                true
            } else {
                dragListener.onTouch(view, event)
            }
        }

        var normalWidth = params.width
        var normalHeight = params.height
        minimizeButton.setOnClickListener {
            normalWidth = params.width
            normalHeight = params.height
            avatarWebView.visibility = View.GONE
            header.visibility = View.GONE
            resizeHandle.visibility = View.GONE
            miniButton.visibility = View.VISIBLE
            root.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(38, 48, 82))
                setStroke(dp(2), Color.WHITE)
            }
            params.width = dp(68)
            params.height = dp(68)
            windowManager.updateViewLayout(root, params)
        }

        val repository = SettingsRepository(applicationContext)
        serviceScope.launch {
            val ip = repository.serverIpFlow.first()
            val port = repository.serverPortFlow.first()
            avatarWebView.loadUrl("http://$ip:$port")
        }
        windowManager.addView(root, params)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        overlayLayout?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        webView?.destroy()
        webView = null
        overlayLayout = null
    }
}
