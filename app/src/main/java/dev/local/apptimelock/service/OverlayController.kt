package dev.local.apptimelock.service

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.DecelerateInterpolator
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import dev.local.apptimelock.core.Session
import dev.local.apptimelock.core.formatRemaining
import dev.local.apptimelock.data.BadgePosition

/** Small native windows avoid Activity launches and give the IME normal focus behavior. */
class OverlayController(
    private val service: AccessibilityService,
    private val onStart: (String, Int, (Boolean) -> Unit) -> Unit,
    private val onHome: () -> Unit,
    private val onPosition: (BadgePosition) -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var badge: TextView? = null
    private var badgeParams: WindowManager.LayoutParams? = null
    private var gate: View? = null
    private var gateKey: String? = null
    private var appliedPosition = BadgePosition()
    private var dragging = false
    private var badgePackage: String? = null
    private var expanded = false
    private var badgeRemaining = 0L
    private var slideAnimator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val collapse = Runnable {
        if (!dragging) { expanded = false; updateBadgeAppearance() }
    }
    val badgeStatus: String get() = "visible=${badge != null} expanded=$expanded"
    private val dark get() = service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val background get() = Color.parseColor(if (dark) "#161C18" else "#F7F6F0")
    private val surface get() = Color.parseColor(if (dark) "#222D24" else "#FFFEF9")
    private val foreground get() = Color.parseColor(if (dark) "#E2E8DD" else "#25312A")
    private val muted get() = Color.parseColor(if (dark) "#B8C2B3" else "#657061")
    private val primary get() = Color.parseColor(if (dark) "#A8CBA9" else "#496B50")
    private val soft get() = Color.parseColor(if (dark) "#304D35" else "#E0EADC")
    // Only metadata for views created by this class; never inspect another app's nodes.
    @Suppress("DEPRECATION")
    val ownWindowIds: Set<Int> get() = listOfNotNull(badge, gate).filter { it.isAttachedToWindow }
        .map { it.createAccessibilityNodeInfo().windowId }.filter { it >= 0 }.toSet()
    val isBlocking: Boolean get() = gate != null

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
    private fun round(color: Int, radius: Int = 20) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun label(value: String, size: Float, color: Int = foreground, bold: Boolean = false) = TextView(service).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setLineSpacing(dp(4).toFloat(), 1f)
    }
    private fun vertical() = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
    private fun LinearLayout.addSpace(size: Int) { addView(View(service), LinearLayout.LayoutParams(1, dp(size))) }
    private fun action(value: String, filled: Boolean, callback: () -> Unit) = Button(service).apply {
        text = value; textSize = 16f; isAllCaps = false
        setTextColor(if (filled) { if (dark) Color.parseColor("#14381F") else Color.WHITE } else primary)
        background = round(if (filled) primary else soft, 16)
        minHeight = dp(52); minimumHeight = dp(52)
        setOnClickListener { callback() }
    }

    fun showBadge(pkg: String, remainingMs: Long, position: BadgePosition) {
        hideGate()
        if (badgePackage != null && badgePackage != pkg) hideBadge()
        badgeRemaining = remainingMs
        if (badge == null) {
            badgePackage = pkg
            expanded = false
            appliedPosition = position
            val view = label("", 15f, if (dark) Color.parseColor("#14381F") else Color.WHITE, true).apply {
                background = round(primary, 18)
                setPadding(0, dp(6), 0, dp(6))
                elevation = dp(4).toFloat()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            val params = WindowManager.LayoutParams(
                dp(12), dp(48),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.LEFT; title = "应用时间锁 · 剩余时间" }
            var downX = 0f; var downY = 0f; var baseX = 0; var baseY = 0
            var moved = false
            val slop = ViewConfiguration.get(service).scaledTouchSlop
            view.setOnClickListener {
                expanded = !expanded
                updateBadgeAppearance()
                scheduleCollapse()
            }
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        slideAnimator?.end()
                        dragging = true; moved = false; handler.removeCallbacks(collapse)
                        downX = event.rawX; downY = event.rawY; baseX = params.x; baseY = params.y; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.rawX - downX) > slop || kotlin.math.abs(event.rawY - downY) > slop) moved = true
                        if (moved) {
                            params.x = baseX + (event.rawX - downX).toInt()
                            params.y = baseY + (event.rawY - downY).toInt()
                            clampBadge(view, params)
                            wm.updateViewLayout(view, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        dragging = false
                        if (moved) {
                            val bounds = wm.currentWindowMetrics.bounds
                            val right = params.x + view.width / 2 > bounds.width() / 2
                            val safe = safeBounds(view)
                            val fraction = ((params.y - safe[1]).toFloat() / (safe[3] - safe[1]).coerceAtLeast(1)).coerceIn(0f, 1f)
                            appliedPosition = BadgePosition(right, fraction)
                            placeBadge(view, params, appliedPosition)
                            wm.updateViewLayout(view, params)
                            onPosition(appliedPosition)
                        } else if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                        scheduleCollapse()
                        true
                    }
                    else -> false
                }
            }
            badge = view; badgeParams = params
            wm.addView(view, params)
            view.post { if (badge === view) { placeBadge(view, params, appliedPosition); wm.updateViewLayout(view, params) } }
        }
        badge?.let { view ->
            updateBadgeText(view)
            if (!dragging && slideAnimator == null) badgeParams?.let { params ->
                val oldX = params.x
                val oldY = params.y
                placeBadge(view, params, appliedPosition)
                if (oldX != params.x || oldY != params.y) wm.updateViewLayout(view, params)
            }
        }
    }

    private fun scheduleCollapse() {
        handler.removeCallbacks(collapse)
        if (expanded && badge != null) handler.postDelayed(collapse, 5_000)
    }

    private fun updateBadgeText(view: TextView) {
        val time = formatRemaining(badgeRemaining)
        view.text = if (expanded && slideAnimator == null) time else ""
        view.contentDescription = if (expanded) "剩余 $time，点击收回边缘，可拖动" else "倒计时侧边栏，点击滑出查看时间，可拖动"
    }

    private fun updateBadgeAppearance() {
        val view = badge ?: return
        val params = badgeParams ?: return
        val oldAnimator = slideAnimator
        slideAnimator = null
        oldAnimator?.cancel()
        val animation = ValueAnimator.ofInt(params.width, dp(if (expanded) 72 else 12))
        slideAnimator = animation
        updateBadgeText(view)
        animation.duration = 240
        animation.interpolator = DecelerateInterpolator()
        animation.addUpdateListener {
            if (badge === view && view.isAttachedToWindow) {
                params.width = it.animatedValue as Int
                placeBadge(view, params, appliedPosition)
                wm.updateViewLayout(view, params)
            }
        }
        animation.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (slideAnimator === animation) {
                    slideAnimator = null
                    if (badge === view) updateBadgeText(view)
                }
            }
        })
        animation.start()
    }

    private fun safeBounds(view: View): IntArray {
        val metrics = wm.currentWindowMetrics
        val system = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val ime = metrics.windowInsets.getInsets(WindowInsets.Type.ime())
        return intArrayOf(
            system.left, system.top + dp(8),
            (metrics.bounds.width() - system.right - (badgeParams?.width ?: view.width)).coerceAtLeast(0),
            (metrics.bounds.height() - maxOf(system.bottom, ime.bottom) - view.height - dp(8)).coerceAtLeast(system.top + dp(8)),
        )
    }
    private fun clampBadge(view: View, params: WindowManager.LayoutParams) {
        val b = safeBounds(view)
        params.x = params.x.coerceIn(b[0], maxOf(b[0], b[2]))
        params.y = params.y.coerceIn(b[1], maxOf(b[1], b[3]))
    }
    private fun placeBadge(view: View, params: WindowManager.LayoutParams, position: BadgePosition) {
        val b = safeBounds(view)
        params.x = if (position.right) b[2] else b[0]
        params.y = b[1] + ((b[3] - b[1]) * position.verticalFraction).toInt()
        clampBadge(view, params)
    }

    fun showGate(pkg: String, session: Session) {
        hideBadge()
        val key = "$pkg:${session.hasExpired}"
        if (gateKey == key && gate != null) return
        hideGate()
        val root = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) onHome()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(this@OverlayController.background)
            isFocusableInTouchMode = true
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(dp(24) + safe.left, dp(24) + safe.top, dp(24) + safe.right, dp(24) + safe.bottom)
                insets
            }
        }
        val scroll = ScrollView(service).apply { isFillViewport = true; clipToPadding = false }
        val body = vertical().apply { gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(16)) }
        scroll.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val appInfo = runCatching { service.packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
        val name = appInfo?.let { service.packageManager.getApplicationLabel(it).toString() } ?: pkg

        fun chooser() {
            body.removeAllViews()
            appInfo?.let { info ->
                val icon = ImageView(service).apply { setImageDrawable(service.packageManager.getApplicationIcon(info)) }
                body.addView(icon, LinearLayout.LayoutParams(dp(56), dp(56)))
                body.addSpace(12)
            }
            body.addView(label(name, 15f, muted))
            body.addSpace(26)
            body.addView(label("这次准备\n使用多久？", 31f, bold = true))
            body.addSpace(12)
            body.addView(label("先定一个时间，再安心开始。", 14f, muted))
            body.addSpace(28)
            val card = vertical().apply { setPadding(dp(18), dp(20), dp(18), dp(20)); background = round(surface, 24) }
            val row = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
            var selected: Int? = null
            var custom = false
            val input = EditText(service).apply {
                hint = "1—180 分钟"; inputType = InputType.TYPE_CLASS_NUMBER
                setSingleLine(true); textSize = 18f; setTextColor(this@OverlayController.foreground); setHintTextColor(muted)
                backgroundTintList = android.content.res.ColorStateList.valueOf(primary)
                visibility = View.GONE
                contentDescription = "自定义使用分钟数"
            }
            val error = label("", 12f, Color.parseColor("#B34B3E"))
            val presets = mutableListOf<Button>()
            listOf(10, 20, 30).forEach { minutes ->
                val button = action("$minutes\n分钟", false) {
                    custom = false; selected = minutes; input.visibility = View.GONE; error.text = ""
                    service.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
                    presets.forEachIndexed { index, item ->
                        val isSelected = listOf(10, 20, 30)[index] == minutes
                        item.background = round(if (isSelected) primary else soft, 14)
                        item.setTextColor(if (isSelected) { if (dark) Color.parseColor("#14381F") else Color.WHITE } else primary)
                        item.isSelected = isSelected
                    }
                }
                button.textSize = 15f
                presets += button
                row.addView(button, LinearLayout.LayoutParams(0, dp(72), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            card.addSpace(14)
            val customButton = action("自定义时长", false) {
                custom = true; selected = null; error.text = ""
                presets.forEach { it.background = round(soft, 14); it.setTextColor(primary); it.isSelected = false }
                input.visibility = View.VISIBLE
                input.requestFocus()
                input.post { service.getSystemService(InputMethodManager::class.java).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
            }
            card.addView(customButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
            card.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
            card.addSpace(12)
            card.addView(error)
            body.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            body.addSpace(22)
            lateinit var start: Button
            start = action("开始使用", true) {
                val minutes = if (custom) input.text.toString().toIntOrNull() else selected
                if (minutes == null || minutes !in 1..180) {
                    error.text = if (custom) "请输入 1—180 的整数分钟" else "请先选择使用时长"
                } else {
                    start.isEnabled = false
                    service.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
                    onStart(pkg, minutes) { accepted ->
                        if (accepted) hideGate() else { start.isEnabled = true; error.text = "暂时无法开始，请返回桌面后重试" }
                    }
                }
            }
            body.addView(start, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
            body.addSpace(10)
            body.addView(action("返回桌面", false, onHome), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
            body.addSpace(18)
            body.addView(label("离开时暂停，回来后继续。", 12f, muted))
        }

        if (session.hasExpired) {
            body.addView(label("✓", 58f, primary))
            body.addSpace(24)
            body.addView(label("时间到了", 34f, bold = true))
            body.addSpace(12)
            body.addView(label("$name · 这次已使用 ${session.grantedMs / 60_000} 分钟", 15f, muted))
            body.addSpace(16)
            body.addView(label("放下手机，给自己一点空白。\n也可以重新决定下一段时间。", 16f, muted))
            body.addSpace(40)
            body.addView(action("结束使用", true, onHome), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
            body.addSpace(14)
            body.addView(action("再用一会", false) { chooser() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        } else chooser()
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            title = "应用时间锁 · 使用提醒"
        }
        gate = root; gateKey = key
        wm.addView(root, params)
        root.requestFocus()
        root.requestApplyInsets()
    }

    fun hideBadge() {
        val animation = slideAnimator
        slideAnimator = null
        animation?.cancel()
        handler.removeCallbacks(collapse)
        badge?.let { if (it.isAttachedToWindow) wm.removeViewImmediate(it) }
        badge = null; badgeParams = null; dragging = false
        badgePackage = null; expanded = false
    }
    fun hideGate() {
        gate?.let {
            service.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0)
            if (it.isAttachedToWindow) wm.removeViewImmediate(it)
        }
        gate = null; gateKey = null
    }
    fun hideAll() { hideBadge(); hideGate() }
}
