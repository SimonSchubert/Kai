package com.kainext.duo.overlay

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
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "KaiOverlay"

/**
 * Hosts the floating control window shown while the agent drives the phone.
 *
 * Why a service rather than a Compose overlay inside the activity: the whole point is that the
 * window stays visible while the agent operates *other* apps, so it cannot live in Kai's own
 * activity. A foreground service plus the `SYSTEM_ALERT_WINDOW` permission is the only way a
 * view can float above other apps without taking focus away from them.
 *
 * The window is deliberately small and out of the way (top-start, draggable) and shows three
 * things: what the agent is doing right now, a rolling log of recent steps, and two controls —
 * a message field and a stop button.
 *
 * Implemented with framework views rather than Compose on purpose: a Compose view attached to a
 * raw `WindowManager` needs a synthetic `LifecycleOwner` and `SavedStateRegistryOwner`, and a
 * mistake there fails at runtime (or at compile time, on a version bump) rather than at build
 * time. Plain views have no such plumbing and are styled to match the app by hand below.
 */
class KaiOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var statusView: TextView? = null
    private var logView: TextView? = null
    private var logScroll: ScrollView? = null
    private var messageInput: EditText? = null
    private var sendButton: Button? = null
    private var stopButton: Button? = null
    private var phaseDot: View? = null
    private var titleView: TextView? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var renderJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        // The foreground notification is what keeps the process alive while other apps are in
        // front. Without it the OS may reclaim the process and the window would vanish mid-task.
        val started = runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }.isSuccess
        if (!started) {
            Log.w(TAG, "could not enter foreground; stopping")
            stopSelf()
            return
        }
        if (!addOverlay()) {
            stopSelf()
            return
        }
        OverlayController.show()
        renderJob = serviceScope.launch {
            OverlayController.state.collectLatest { render(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The notification's Stop action lands here rather than in the window, so the user can
        // stop the agent without first finding the floating window.
        if (intent?.action == ACTION_STOP) {
            OverlayController.requestStop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        renderJob?.cancel()
        removeOverlay()
        serviceScope.cancel()
        OverlayController.hide()
        super.onDestroy()
    }

    // ---- window ----

    private fun addOverlay(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted; cannot show the control window")
            return false
        }
        val params = WindowManager.LayoutParams(
            dp(292),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }
        layoutParams = params

        val view = buildWindowView()
        return try {
            windowManager.addView(view, params)
            rootView = view
            true
        } catch (e: Throwable) {
            Log.w(TAG, "addView failed", e)
            false
        }
    }

    private fun removeOverlay() {
        rootView?.let { view -> runCatching { windowManager.removeView(view) } }
        rootView = null
    }

    /**
     * Builds the window's view tree.
     *
     * Colours are taken from the app's own palette (dark translucent surface, primary accent) so
     * the window reads as part of Kai rather than as a foreign widget floating over the phone.
     */
    private fun buildWindowView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(COLOR_SURFACE)
                setStroke(dp(1), COLOR_STROKE)
            }
        }

        // Header: phase dot, title, and drag handle behaviour.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        phaseDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLOR_IDLE)
            }
            layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply {
                marginEnd = dp(8)
            }
        }
        titleView = TextView(this).apply {
            text = "Kai Next"
            setTextColor(COLOR_ON_SURFACE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(phaseDot)
        header.addView(titleView)
        root.addView(header)

        statusView = TextView(this).apply {
            setTextColor(COLOR_ON_SURFACE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(6), 0, 0)
            maxLines = 2
            text = "Idle"
        }
        root.addView(statusView)

        // Log: monospace, bounded height, scrolled to the newest line on every update.
        logScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(COLOR_LOG_BG)
            }
            isVerticalScrollBarEnabled = true
            visibility = View.GONE
        }
        logView = TextView(this).apply {
            setTextColor(COLOR_LOG_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setLineSpacing(0f, 1.15f)
        }
        logScroll?.addView(logView)
        root.addView(logScroll)

        // Message field: lets the user steer the agent mid-task without opening the app.
        messageInput = EditText(this).apply {
            hint = "Message Kai…"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_ON_SURFACE)
            setHintTextColor(COLOR_MUTED)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(COLOR_LOG_BG)
                setStroke(dp(1), COLOR_STROKE)
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
        }
        root.addView(messageInput)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4) }
        }
        sendButton = textButton("Send") {
            val text = messageInput?.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                OverlayController.submitUserMessage(text)
                messageInput?.setText("")
            }
        }
        stopButton = textButton("Stop") { OverlayController.requestStop() }.apply {
            setTextColor(COLOR_ERROR)
        }
        val hideButton = textButton("Hide") {
            // Hiding only removes the window; the agent keeps working and the notification stays.
            OverlayController.hide()
            stopSelf()
        }
        buttons.addView(sendButton)
        buttons.addView(stopButton)
        buttons.addView(hideButton)
        root.addView(buttons)

        // Dragging: the header acts as a handle so the window never covers the control the agent
        // is trying to tap. Moving the window updates the layout params directly, which is the
        // only way to reposition a raw overlay view.
        attachDrag(header)
        return root
    }

    private fun textButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(COLOR_PRIMARY)
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.TRANSPARENT)
        }
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), dp(6), dp(10), dp(6))
        setOnClickListener { onClick() }
    }

    private fun attachDrag(handle: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        handle.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    rootView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
                    true
                }
                else -> false
            }
        }
    }

    /** Pushes [state] into the views. Called on the main thread via `collectLatest`. */
    private fun render(state: OverlayState) {
        statusView?.text = state.statusText.ifBlank { "Idle" }
        phaseDot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                when (state.phase) {
                    OverlayPhase.IDLE -> COLOR_IDLE
                    OverlayPhase.WORKING -> COLOR_PRIMARY
                    OverlayPhase.WAITING -> COLOR_WAITING
                    OverlayPhase.DONE -> COLOR_DONE
                    OverlayPhase.FAILED -> COLOR_ERROR
                },
            )
        }
        titleView?.text = when (state.phase) {
            OverlayPhase.IDLE -> "Kai Next"
            OverlayPhase.WORKING -> "Kai Next · working"
            OverlayPhase.WAITING -> "Kai Next · waiting"
            OverlayPhase.DONE -> "Kai Next · done"
            OverlayPhase.FAILED -> "Kai Next · failed"
        }
        if (state.log.isEmpty()) {
            logScroll?.visibility = View.GONE
        } else {
            logScroll?.visibility = View.VISIBLE
            logView?.text = state.log.joinToString("\n") { it.text }
            // Follow the tail: the window's purpose is to show what is happening *now*.
            logScroll?.post { logScroll?.fullScroll(View.FOCUS_DOWN) }
        }
        sendButton?.isEnabled = state.isRunning
        stopButton?.isEnabled = state.isRunning
        if (!state.isVisible) {
            stopSelf()
        }
    }

    // ---- notification ----

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Kai Next device control",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while Kai is operating the phone"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, KaiOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder
            .setContentTitle("Kai is operating your phone")
            .setContentText("Use the floating window to watch progress or stop")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
        // The int-based overload is deprecated on API 26+ in favour of the Icon one, but it still
        // works and avoids depending on an icon resource just for a single notification action.
        @Suppress("DEPRECATION")
        builder.addAction(0, "Stop", stopIntent)
        return builder.build()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "kai_overlay_channel"
        private const val NOTIFICATION_ID = 9002
        private const val REQUEST_STOP = 4214
        const val ACTION_STOP = "com.kainext.duo.overlay.STOP"

        /** True when the user has granted the "display over other apps" permission. */
        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        /** Starts the window. Safe to call when it is already running. */
        fun start(context: Context) {
            val intent = Intent(context, KaiOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stops the window and tears the service down. */
        fun stop(context: Context) {
            context.stopService(Intent(context, KaiOverlayService::class.java))
        }

        /** Opens the system page where the user grants the overlay permission. */
        fun openOverlaySettings(context: Context) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + context.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.onFailure { Log.w(TAG, "could not open overlay settings", it) }
        }
    }
}

// Palette. Hardcoded rather than resolved from the app theme because an overlay window has no
// theme of its own, and these values are the app's dark-surface tones.
private val COLOR_SURFACE = Color.parseColor("#F21C1B1F")
private val COLOR_STROKE = Color.parseColor("#33FFFFFF")
private val COLOR_ON_SURFACE = Color.parseColor("#F2FFFFFF")
private val COLOR_MUTED = Color.parseColor("#99FFFFFF")
private val COLOR_LOG_BG = Color.parseColor("#33FFFFFF")
private val COLOR_LOG_TEXT = Color.parseColor("#CCFFFFFF")
private val COLOR_PRIMARY = Color.parseColor("#8AB4F8")
private val COLOR_ERROR = Color.parseColor("#F28B82")
private val COLOR_WAITING = Color.parseColor("#FDD663")
private val COLOR_DONE = Color.parseColor("#81C995")
private val COLOR_IDLE = Color.parseColor("#8E918F")
