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
import kotlin.math.abs

class FloatingWaifuService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var rootView: FrameLayout? = null
    private var webView: WebView? = null
    private var params: WindowManager.LayoutParams? = null
    private var header: LinearLayout? = null
    private var resizeHandle: ImageButton? = null
    private var restoreButton: ImageButton? = null
    private var normalWidth = 0
    private var normalHeight = 0
    private var isMinimized = false

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
        val windowParams = WindowManager.LayoutParams(
            (metrics.widthPixels * 0.78f).toInt().coerceAtMost(metrics.widthPixels),
            (metrics.heightPixels * 0.64f).toInt().coerceAtMost(metrics.heightPixels),
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(80)
        }
        params = windowParams
        normalWidth = windowParams.width
        normalHeight = windowParams.height

        val root = FrameLayout(this)
        rootView = root
        val web = WebView(this).apply {
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
        webView = web
        root.addView(web, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(220, 20, 24, 40))
        }
        val moveHandle = TextView(this).apply {
            text = "⠿  Move"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Drag to move floating companion"
        }
        toolbar.addView(moveHandle, LinearLayout.LayoutParams(0, dp(48), 1f))

        val minimize = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Minimize floating companion"
        }
        toolbar.addView(minimize, LinearLayout.LayoutParams(dp(48), dp(48)))

        val close = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Close floating companion"
            setOnClickListener { stopSelf() }
        }
        toolbar.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        header = toolbar
        root.addView(toolbar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP
        ))

        val resize = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            setBackgroundColor(Color.argb(210, 20, 24, 40))
            contentDescription = "Resize floating companion"
        }
        resizeHandle = resize
        root.addView(resize, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.END))

        val restore = ImageButton(this).apply {
            contentDescription = "Restore floating companion"
            visibility = View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(38, 48, 82))
                setStroke(dp(2), Color.WHITE)
            }
        }
        restoreButton = restore
        root.addView(restore, FrameLayout.LayoutParams(dp(68), dp(68), Gravity.CENTER))

        var dragX = 0
        var dragY = 0
        var touchX = 0f
        var touchY = 0f
        val dragListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragX = windowParams.x
                    dragY = windowParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    windowParams.x = (dragX + (event.rawX - touchX).toInt())
                        .coerceIn(0, (metrics.widthPixels - windowParams.width).coerceAtLeast(0))
                    windowParams.y = (dragY + (event.rawY - touchY).toInt())
                        .coerceIn(0, (metrics.heightPixels - windowParams.height).coerceAtLeast(0))
                    windowManager.updateViewLayout(root, windowParams)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        moveHandle.setOnTouchListener(dragListener)

        var startWidth = 0
        var startHeight = 0
        var startX = 0f
        var startY = 0f
        resize.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startWidth = windowParams.width
                    startHeight = windowParams.height
                    startX = event.rawX
                    startY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    windowParams.width = (startWidth + (event.rawX - startX).toInt())
                        .coerceIn(dp(220), metrics.widthPixels)
                    windowParams.height = (startHeight + (event.rawY - startY).toInt())
                        .coerceIn(dp(280), metrics.heightPixels)
                    windowManager.updateViewLayout(root, windowParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    normalWidth = windowParams.width
                    normalHeight = windowParams.height
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        minimize.setOnClickListener {
            normalWidth = windowParams.width
            normalHeight = windowParams.height
            isMinimized = true
            web.visibility = View.GONE
            toolbar.visibility = View.GONE
            resize.visibility = View.GONE
            restore.visibility = View.VISIBLE
            windowParams.width = dp(68)
            windowParams.height = dp(68)
            root.background = circleBackground()
            windowManager.updateViewLayout(root, windowParams)
        }

        // The minimized bubble can be dragged; a tap restores the full window.
        var miniDownX = 0f
        var miniDownY = 0f
        var miniWindowX = 0
        var miniWindowY = 0
        var miniMoved = false
        restore.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    miniDownX = event.rawX
                    miniDownY = event.rawY
                    miniWindowX = windowParams.x
                    miniWindowY = windowParams.y
                    miniMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - miniDownX).toInt()
                    val dy = (event.rawY - miniDownY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) miniMoved = true
                    if (miniMoved) {
                        windowParams.x = (miniWindowX + dx).coerceIn(0, (metrics.widthPixels - windowParams.width).coerceAtLeast(0))
                        windowParams.y = (miniWindowY + dy).coerceIn(0, (metrics.heightPixels - windowParams.height).coerceAtLeast(0))
                        windowManager.updateViewLayout(root, windowParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!miniMoved && isMinimized) restoreFloatingView()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        val repository = SettingsRepository(applicationContext)
        serviceScope.launch {
            val ip = repository.serverIpFlow.first()
            val port = repository.serverPortFlow.first()
            web.loadUrl("http://$ip:$port")
        }
        windowManager.addView(root, windowParams)
    }

    private fun restoreFloatingView() {
        val root = rootView ?: return
        val windowParams = params ?: return
        isMinimized = false
        root.background = null
        webView?.visibility = View.VISIBLE
        header?.visibility = View.VISIBLE
        resizeHandle?.visibility = View.VISIBLE
        restoreButton?.visibility = View.GONE
        windowParams.width = normalWidth
        windowParams.height = normalHeight
        windowManager.updateViewLayout(root, windowParams)
    }

    private fun circleBackground() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.rgb(38, 48, 82))
        setStroke(dp(2), Color.WHITE)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        rootView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        webView?.destroy()
        webView = null
        rootView = null
        params = null
    }
}
