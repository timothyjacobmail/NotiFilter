package co.adityarajput.notifilter.utils

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.*
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.*
import android.widget.*
import co.adityarajput.notifilter.R

@SuppressLint("ClickableViewAccessibility")
class ManualGlow private constructor(private val context: Context) : ContextWrapper(context) {
    companion object {
        @Volatile private var instance: ManualGlow? = null
        fun get(context: Context): ManualGlow = instance ?: synchronized(this) {
            instance ?: ManualGlow(context.applicationContext).also { instance = it }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private var foreground: String? = null
    private var lastUsageQuery = 0L
    private var pollScheduled = false
    private var placing = false
    private val launcherPackage by lazy {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
    }

    private fun isHomeVisible(): Boolean {
        if (!isGranted(Permission.USAGE_ACCESS)) return false
        val now = System.currentTimeMillis()
        val start = if (lastUsageQuery == 0L || lastUsageQuery > now) now - 3_600_000 else lastUsageQuery
        val events = getSystemService(android.app.usage.UsageStatsManager::class.java).queryEvents(start, now)
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                foreground = event.packageName
            } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED && foreground == event.packageName) {
                foreground = null
            }
        }
        lastUsageQuery = now
        return foreground != null && foreground == launcherPackage
    }

    private fun schedulePoll() {
        if (pollScheduled || (!placing && !IconGlow.isActive(this))) return
        pollScheduled = true
        handler.postDelayed({ pollScheduled = false; refreshGlow() }, 500)
    }
    private var centerX = 0.5f
    private var centerY = 0.5f
    private var sizeDp = 100f
    private var ring: View? = null
    private var panel: View? = null
    private var placementTarget = ""
    private val activeRings = mutableMapOf<Position, GlowView>()

    private data class Position(val x: Float, val y: Float, val size: Float)
    private fun savedPosition(target: String): Position? {
        val prefs = IconGlow.preferences(this)
        val prefix = when {
            prefs.getBoolean("position_saved:$target", false) -> ":$target"
            prefs.getBoolean("position_saved", false) -> ""
            else -> return null
        }
        return Position(prefs.getFloat("x$prefix", 0.5f), prefs.getFloat("y$prefix", 0.5f),
            prefs.getFloat("size$prefix", 100f))
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { refreshGlow() }
    }

    init {
        androidx.core.content.ContextCompat.registerReceiver(this, screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_CONFIGURATION_CHANGED) },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        loadPosition()
    }

    private fun loadPosition() {
        val position = savedPosition(placementTarget) ?: Position(0.5f, 0.5f, 100f)
        centerX = position.x
        centerY = position.y
        sizeDp = position.size
    }
    private fun dimensions(): Pair<Int, Int> {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windows.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    fun beginPlacement(target: String) {
        handler.post {
            removeWindows()
            placementTarget = target
            loadPosition()
            placing = true
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            handler.postDelayed({ refreshGlow() }, 350)
        }
    }

    fun refreshGlow() {
        handler.post {
            val (width, height) = dimensions()
            val unlocked = !getSystemService(KeyguardManager::class.java).isKeyguardLocked && getSystemService(PowerManager::class.java).isInteractive
            val homeVisible = isHomeVisible()
            schedulePoll()
            val visible = homeVisible && android.provider.Settings.canDrawOverlays(this) && unlocked && width < height && (placing || IconGlow.isActive(this))
            if (!visible) { removeWindows(); return@post }
            try {
                val size = dp(sizeDp)
                if (!placing) {
                    updateActiveRings(width, height)
                    return@post
                }
                val params = ringLayout(Position(centerX, centerY, sizeDp), width, height, editable = true)
                if (ring == null) {
                    ring = GlowView(this, IconGlow.speed(this, placementTarget))
                    ring!!.setOnTouchListener(object : View.OnTouchListener {
                        var startX = 0f; var startY = 0f; var oldX = 0f; var oldY = 0f
                        override fun onTouch(view: View, event: MotionEvent): Boolean {
                            if (!placing) return false
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> { startX = event.rawX; startY = event.rawY; oldX = centerX; oldY = centerY }
                                MotionEvent.ACTION_MOVE -> {
                                    centerX = (oldX + (event.rawX - startX) / width).coerceIn(size / 2f / width, 1 - size / 2f / width)
                                    centerY = (oldY + (event.rawY - startY) / height).coerceIn(size / 2f / height, 1 - size / 2f / height)
                                    refreshGlow()
                                }
                            }
                            return true
                        }
                    })
                    windows.addView(ring, params)
                } else windows.updateViewLayout(ring, params)
                if (placing && panel == null) showControls()
                if (!placing && panel != null) { windows.removeView(panel); panel = null }
            } catch (e: RuntimeException) {
                co.adityarajput.notifilter.utils.Logger.e("ManualGlow", "Unable to display overlay", e)
                removeWindows()
            }
        }
    }

    private fun ringLayout(position: Position, width: Int, height: Int, editable: Boolean): WindowManager.LayoutParams {
        val size = dp(position.size)
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            if (editable) flags else flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = (position.x * width - size / 2).toInt().coerceIn(0, (width - size).coerceAtLeast(0))
            y = (position.y * height - size / 2).toInt().coerceIn(0, (height - size).coerceAtLeast(0))
            alpha = 0.7f
            title = "NotiFilter pulsing icon glow"
        }
    }

    private fun updateActiveRings(width: Int, height: Int) {
        // Legacy filters retain the previously shared spot until individually repositioned.
        val positions = IconGlow.activeTargets(this).sorted().mapNotNull { target ->
            savedPosition(target)?.let { it to IconGlow.speed(this, target) }
        }.toMap()
        val obsolete = activeRings.keys - positions.keys
        obsolete.forEach { position -> windows.removeView(activeRings.remove(position)) }
        positions.forEach { (position, speed) ->
            val params = ringLayout(position, width, height, editable = false)
            val existing = activeRings[position]
            if (existing == null || existing.speed != speed) {
                existing?.let { windows.removeView(it) }
                val view = GlowView(this, speed)
                windows.addView(view, params)
                activeRings[position] = view
            } else if ((existing.layoutParams as WindowManager.LayoutParams).let {
                    it.x != params.x || it.y != params.y || it.width != params.width
                }) windows.updateViewLayout(existing, params)
        }
    }

    private fun showControls() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            setBackgroundColor(Color.rgb(24, 31, 44))
        }
        layout.addView(TextView(this).apply { setText(R.string.glow_drag); setTextColor(Color.WHITE); textSize = 16f })
        val buttons = LinearLayout(this)
        fun button(label: Int, action: () -> Unit) {
            buttons.addView(Button(this).apply { setText(label); textSize = 11f; setOnClickListener { action() } },
                LinearLayout.LayoutParams(0, dp(54f), 1f))
        }
        button(R.string.glow_smaller) { sizeDp = (sizeDp - 8).coerceAtLeast(56f); refreshGlow() }
        button(R.string.glow_larger) { sizeDp = (sizeDp + 8).coerceAtMost(200f); refreshGlow() }
        button(R.string.glow_save) {
            IconGlow.preferences(this).edit().putFloat("x:$placementTarget", centerX).putFloat("y:$placementTarget", centerY).putFloat("size:$placementTarget", sizeDp).putBoolean("position_saved:$placementTarget", true).apply()
            placing = false
            removeWindows()
            startActivity(Intent(this, co.adityarajput.notifilter.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        button(R.string.glow_cancel) {
            placing = false; loadPosition(); removeWindows()
            startActivity(Intent(this, co.adityarajput.notifilter.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        layout.addView(buttons)
        panel = layout
        windows.addView(layout, WindowManager.LayoutParams(dimensions().first - dp(16f), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(32f); title = "NotiFilter glow placement" })
    }

    private fun removeWindows() {
        activeRings.values.forEach { runCatching { windows.removeView(it) } }
        activeRings.clear()
        ring?.let { runCatching { windows.removeView(it) } }; ring = null
        panel?.let { runCatching { windows.removeView(it) } }; panel = null
    }
    private class GlowView(context: Context, val speed: GlowSpeed) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var colorMix = 0f
        private val pulse = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = speed.halfCycleMillis
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener {
                colorMix = it.animatedValue as Float
                updateGradient()
                invalidate()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            pulse.start()
        }

        override fun onDetachedFromWindow() {
            pulse.cancel()
            super.onDetachedFromWindow()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            updateGradient()
        }

        private fun updateGradient() {
            if (width == 0 || height == 0) return
            val radius = width / 2f
            // Blue -> red -> blue, with a transparent centre so the icon stays visible.
            val red = (30 + 225 * colorMix).toInt()
            val green = (130 - 90 * colorMix).toInt()
            val blue = (255 - 215 * colorMix).toInt()
            paint.shader = RadialGradient(radius, height / 2f, radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(180, red, green, blue), Color.argb(70, red, green, blue), Color.TRANSPARENT),
                floatArrayOf(0f, 0.48f, 0.66f, 0.82f, 1f), Shader.TileMode.CLAMP)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawCircle(width / 2f, height / 2f, width / 2f, paint)
        }
    }
}
