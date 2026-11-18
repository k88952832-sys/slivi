package dev.aero.menu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private lateinit var panelLp: WindowManager.LayoutParams
    private var panelVisible = false

    private lateinit var content: LinearLayout
    private val tabViews = ArrayList<TextView>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()

        bubbleLp = newParams(dp(52), dp(52)).apply { x = dp(16); y = dp(120) }
        panelLp = newParams(dp(300), WindowManager.LayoutParams.WRAP_CONTENT)

        bubble = buildBubble().also { wm.addView(it, bubbleLp) }
        panel = buildPanel()

        MenuState.onChange = { key, value ->
            if (key == "ui.opacity" && value is Int) panel?.alpha = value / 100f
        }
    }

    override fun onDestroy() {
        MenuState.onChange = null
        bubble?.let { runCatching { wm.removeView(it) } }
        if (panelVisible) panel?.let { runCatching { wm.removeView(it) } }
        bubble = null
        panel = null
        super.onDestroy()
    }

    // ---- window plumbing -------------------------------------------------------------------

    private fun newParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun togglePanel() {
        val p = panel ?: return
        if (panelVisible) {
            wm.removeView(p)
        } else {
            panelLp.x = bubbleLp.x
            panelLp.y = bubbleLp.y + dp(60)
            wm.addView(p, panelLp)
        }
        panelVisible = !panelVisible
    }

    private fun makeDraggable(
        handle: View,
        target: View,
        lp: WindowManager.LayoutParams,
        onClick: (() -> Unit)? = null,
    ) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        if (target.isAttachedToWindow) wm.updateViewLayout(target, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) onClick?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    private fun startAsForeground() {
        val channelId = "aero_overlay"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val n: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Aero Menu")
            .setContentText("Floating menu is running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---- views -----------------------------------------------------------------------------

    private fun buildBubble(): View {
        val v = TextView(this).apply {
            text = "A"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ACCENT)
                setStroke(dp(2), Color.parseColor("#B8A6FF"))
            }
            elevation = dp(6).toFloat()
        }
        makeDraggable(v, v, bubbleLp) { togglePanel() }
        return v
    }

    private fun buildPanel(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(BG, 16).also { it.setStroke(dp(1), Color.parseColor("#2C2C3C")) }
            alpha = MenuState.get(Slider("ui.opacity", "", 40, 100, 95)) / 100f
        }

        // header (drag handle)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(8))
        }
        header.addView(TextView(this).apply {
            text = "AERO"
            textSize = 18f
            setTextColor(ACCENT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "v0.1  UI only"
            textSize = 11f
            setTextColor(MUTED)
            setPadding(0, 0, dp(12), 0)
        })
        header.addView(TextView(this).apply {
            text = "\u2715"
            textSize = 16f
            setTextColor(MUTED)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { togglePanel() }
        })
        makeDraggable(header, root, panelLp)
        root.addView(header)

        // tabs
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), 0, dp(10), dp(8))
        }
        MenuSpec.tabs.forEachIndexed { i, tab ->
            val t = TextView(this).apply {
                text = tab.title
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(7), dp(10), dp(7))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = dp(4) }
                setOnClickListener { selectTab(i) }
            }
            tabViews.add(t)
            tabs.addView(t)
        }
        root.addView(tabs)

        // content
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(10))
        }
        root.addView(
            ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                addView(content)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(280)),
        )

        selectTab(0)
        return root
    }

    private fun selectTab(index: Int) {
        tabViews.forEachIndexed { i, t ->
            val active = i == index
            t.setTextColor(if (active) Color.WHITE else MUTED)
            t.background = rounded(if (active) ACCENT else Color.parseColor("#1C1C28"), 10)
        }
        content.removeAllViews()
        for (item in MenuSpec.tabs[index].items) {
            when (item) {
                is Toggle -> content.addView(toggleRow(item))
                is Slider -> content.addView(sliderRow(item))
            }
        }
    }

    private fun toggleRow(item: Toggle): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = rounded(CARD, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        row.addView(TextView(this).apply {
            text = item.label
            textSize = 14f
            setTextColor(TEXT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(Switch(this).apply {
            isChecked = MenuState.get(item)
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.parseColor("#8A8AA0")))
            trackTintList = ColorStateList(states, intArrayOf(ACCENT, Color.parseColor("#33334A")))
            setOnCheckedChangeListener { _, checked -> MenuState.set(item.key, checked) }
        })
        return row
    }

    private fun sliderRow(item: Slider): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(6))
            background = rounded(CARD, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val value = TextView(this).apply {
            text = MenuState.get(item).toString() + item.unit
            textSize = 13f
            setTextColor(ACCENT)
        }
        top.addView(TextView(this).apply {
            text = item.label
            textSize = 14f
            setTextColor(TEXT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(value)
        col.addView(top)
        col.addView(SeekBar(this).apply {
            max = item.max - item.min
            progress = MenuState.get(item) - item.min
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val v = item.min + p
                    value.text = v.toString() + item.unit
                    if (fromUser) MenuState.set(item.key, v)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })
        return col
    }

    // ---- helpers ---------------------------------------------------------------------------

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        val ACCENT: Int = Color.parseColor("#7C5CFF")
        val BG: Int = Color.parseColor("#F014141C")
        val CARD: Int = Color.parseColor("#1C1C28")
        val TEXT: Int = Color.parseColor("#E6E6F0")
        val MUTED: Int = Color.parseColor("#8A8AA0")
    }
}
