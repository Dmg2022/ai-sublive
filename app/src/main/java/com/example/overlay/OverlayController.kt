package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.AppSettings
import com.example.model.PipelineStatus
import com.example.model.SubtitleState
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class OverlayController(
    private val context: Context,
    private val onPositionChanged: ((Int, Int) -> Unit)? = null,
    private val onTogglePause: (() -> Unit)? = null,
    private val onCloseRequested: (() -> Unit)? = null,
    private val onSettingsChanged: ((AppSettings) -> Unit)? = null
) {
    companion object {
        private const val TAG = "OverlayController"

        // Số dòng mặc định và giới hạn khi kéo dãn khung
        private const val DEFAULT_LINES = 2
        private const val MIN_LINES = 1
        private const val MAX_LINES = 6
        private const val MIN_CARD_WIDTH_DP = 200

        // Giới hạn an toàn để không phải đo chuỗi quá dài
        private const val MAX_TRANSLATED_CHARS = 300
        private const val MAX_ORIGINAL_CHARS = 100

        private const val CARD_MAX_WIDTH_DP = 640
        private const val CARD_WIDTH_RATIO = 0.9f
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var isOverlayAttached = false

    private lateinit var rootView: FrameLayout
    private lateinit var subtitleCard: LinearLayout
    private lateinit var tvTranslated: TextView
    private lateinit var tvOriginal: TextView
    private lateinit var miniControlPanel: LinearLayout

    private lateinit var layoutParams: WindowManager.LayoutParams
    private var currentSettings = AppSettings()

    // Kích thước cố định của thẻ phụ đề -> chữ không làm khung đổi kích thước liên tục (nguyên nhân giật)
    private var cardWidthPx = 0
    private var textAreaWidthPx = 0

    // Số dòng hiển thị (có thể kéo dãn) và trạng thái kéo đổi cỡ
    private var displayLines = DEFAULT_LINES
    private lateinit var resizeHandle: TextView
    private var lastRawTrans: String = ""
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var resizeStartWidth = 0
    private var resizeStartLines = 0

    // Nhớ nội dung gần nhất để bỏ qua cập nhật trùng
    private var lastTranslatedShown: String? = null
    private var lastOriginalShown: String? = null

    // Touch dragging tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var lastDownTime = 0L

    @SuppressLint("ClickableViewAccessibility")
    fun createOverlay(settings: AppSettings) {
        if (isOverlayAttached) return
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot create overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }
        currentSettings = settings

        val metrics = context.resources.displayMetrics
        val dp = metrics.density
        cardWidthPx = min((metrics.widthPixels * CARD_WIDTH_RATIO).toInt(), (CARD_MAX_WIDTH_DP * dp).toInt())
        textAreaWidthPx = cardWidthPx - (32 * dp).toInt() // trừ padding trái/phải 16dp
        displayLines = settings.maxLines.coerceIn(MIN_LINES, MAX_LINES)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (settings.overlayPosX >= 0) settings.overlayPosX else ((metrics.widthPixels - cardWidthPx) / 2)
            y = if (settings.overlayPosY >= 0) settings.overlayPosY else (metrics.heightPixels * 2 / 3)
        }

        buildViews()

        try {
            windowManager.addView(rootView, layoutParams)
            isOverlayAttached = true
            Log.i(TAG, "Floating overlay added to WindowManager")
            // Áp dụng cài đặt sau khi overlay đã được gắn vào WindowManager
            applySettings(currentSettings)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating overlay: ${e.message}", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildViews() {
        val dp = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Subtitle Card: chiều rộng cố định
        subtitleCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_overlay_subtitle)
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                cardWidthPx,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Translated Text: luôn đúng DISPLAY_LINES dòng (minLines = maxLines) -> chiều cao không nhảy.
        // Việc chọn những dòng cuối do computeTail() làm trước khi gán text.
        tvTranslated = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSettings.fontSizeSp)
            setShadowLayer(5f, 1f, 1f, Color.BLACK)
            text = context.getString(R.string.overlay_placeholder_trans)
            minLines = displayLines
            maxLines = displayLines
            ellipsize = null
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Original Text
        tvOriginal = TextView(context).apply {
            setTextColor(Color.LTGRAY)
            setTypeface(Typeface.DEFAULT, Typeface.ITALIC)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (currentSettings.fontSizeSp * 0.75f).coerceAtLeast(12f))
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
            text = context.getString(R.string.overlay_placeholder_orig)
            minLines = 2
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = if (currentSettings.showOriginalText) View.VISIBLE else View.GONE
            setPadding(0, (4 * dp).toInt(), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        subtitleCard.addView(tvTranslated)
        subtitleCard.addView(tvOriginal)

        // Mini Quick Controls Panel (shown on long click or tap)
        miniControlPanel = buildControlPanel(dp)
        miniControlPanel.visibility = View.GONE

        // Tay nắm kéo dãn: kéo sang phải/trái = đổi chiều rộng, kéo xuống/lên = thêm/bớt dòng
        resizeHandle = TextView(context).apply {
            text = "◢  Kéo để đổi cỡ khung"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#CC334155"))
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                cardWidthPx,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(subtitleCard)
        container.addView(resizeHandle)
        container.addView(miniControlPanel)

        rootView = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            addView(container)
        }

        setupDragAndGestures()
        setupResizeHandle()
    }

    private fun buildControlPanel(dp: Float): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_overlay_panel)
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                cardWidthPx,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.overlay_control_title)
            setTextColor(Color.parseColor("#A5B4FC"))
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
            setOnClickListener {
                setPanelVisible(false)
            }
        }

        titleRow.addView(title)
        titleRow.addView(closeBtn)
        panel.addView(titleRow)

        val buttonsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, (6 * dp).toInt(), 0, 0)
        }

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Button Font -
        buttonRow.addView(createSmallBtn("A-", dp) {
            val newSize = (currentSettings.fontSizeSp - 2f).coerceAtLeast(14f)
            currentSettings = currentSettings.copy(fontSizeSp = newSize)
            applySettings(currentSettings)
            onSettingsChanged?.invoke(currentSettings)
        })

        // Button Font +
        buttonRow.addView(createSmallBtn("A+", dp) {
            val newSize = (currentSettings.fontSizeSp + 2f).coerceAtMost(36f)
            currentSettings = currentSettings.copy(fontSizeSp = newSize)
            applySettings(currentSettings)
            onSettingsChanged?.invoke(currentSettings)
        })

        // Button Toggle Original Text
        buttonRow.addView(createSmallBtn("🌐 Gốc", dp) {
            val show = !currentSettings.showOriginalText
            currentSettings = currentSettings.copy(showOriginalText = show)
            applySettings(currentSettings)
            onSettingsChanged?.invoke(currentSettings)
        })

        // Button Click-through
        buttonRow.addView(createSmallBtn("👆 Xuyên", dp) {
            setPanelVisible(false)
            subtitleCard.visibility = View.VISIBLE
            toggleClickThrough(true)
        })

        // Button Pause/Resume
        buttonRow.addView(createSmallBtn("⏯ Tạm dừng", dp) {
            onTogglePause?.invoke()
        })

        // Button Close Service
        buttonRow.addView(createSmallBtn("⏹ Đóng", dp) {
            onCloseRequested?.invoke()
        })

        buttonsScroll.addView(buttonRow)
        panel.addView(buttonsScroll)

        return panel
    }

    private fun createSmallBtn(label: String, dp: Float, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundColor(Color.parseColor("#334155"))
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            val margin = (4 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(margin, 0, margin, 0)
            }
            setOnClickListener { onClick() }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndGestures() {
        subtitleCard.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    lastDownTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isDragging = true
                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        clampToScreen()
                        if (isOverlayAttached) {
                            windowManager.updateViewLayout(rootView, layoutParams)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - lastDownTime
                    if (!isDragging && duration > 450) {
                        // Long press -> Toggle quick controls panel
                        setPanelVisible(miniControlPanel.visibility != View.VISIBLE)
                    } else if (!isDragging && duration < 300) {
                        // Short tap -> Close quick controls panel if open
                        if (miniControlPanel.visibility == View.VISIBLE) {
                            setPanelVisible(false)
                        }
                    } else if (isDragging) {
                        onPositionChanged?.invoke(layoutParams.x, layoutParams.y)
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Chỉ để tránh chuỗi quá dài: cắt phần đầu tại ranh giới từ (không thêm dấu "…" để đầu câu không đổi liên tục).
     */
    private fun limitLength(text: String, maxChars: Int): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed

        val cutFrom = trimmed.length - maxChars
        val spaceIdx = trimmed.indexOf(' ', startIndex = cutFrom)
        val start = if (spaceIdx in cutFrom until trimmed.length - 10) spaceIdx + 1 else cutFrom
        return trimmed.substring(start).trimStart()
    }

    /**
     * Đo chữ bằng StaticLayout (không đụng vào View) rồi trả về đúng phần các dòng cuối
     * vừa với DISPLAY_LINES. Nhờ vậy TextView chỉ được gán text MỘT lần mỗi cập nhật,
     * không còn cảnh "hiện full text rồi mới cắt" (gây giật).
     */
    private fun computeTail(tv: TextView, text: String): String {
        if (text.isEmpty() || textAreaWidthPx <= 0) return text

        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, tv.paint, textAreaWidthPx)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(tv.includeFontPadding)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text, tv.paint, textAreaWidthPx, Layout.Alignment.ALIGN_CENTER,
                1f, 0f, tv.includeFontPadding
            )
        }

        val total = layout.lineCount
        if (total <= displayLines) return text

        val start = layout.getLineStart(total - displayLines)
        return if (start in 1 until text.length) text.substring(start).trimStart() else text
    }

    private fun setPanelVisible(visible: Boolean) {
        miniControlPanel.visibility = if (visible) View.VISIBLE else View.GONE
        resizeHandle.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showTranslated(raw: String) {
        lastRawTrans = raw
        setTranslated(computeTail(tvTranslated, limitLength(raw, MAX_TRANSLATED_CHARS)))
    }

    /** Đổi chiều rộng khung và số dòng hiển thị (dùng khi kéo dãn). */
    private fun applyCardSize(widthPx: Int, lines: Int) {
        val metrics = context.resources.displayMetrics
        val dp = metrics.density
        val screenW = metrics.widthPixels

        cardWidthPx = widthPx.coerceIn((MIN_CARD_WIDTH_DP * dp).toInt(), screenW)
        textAreaWidthPx = cardWidthPx - (32 * dp).toInt()
        displayLines = lines.coerceIn(MIN_LINES, MAX_LINES)

        listOf(subtitleCard, resizeHandle, miniControlPanel).forEach { v ->
            v.layoutParams = (v.layoutParams as LinearLayout.LayoutParams).apply { width = cardWidthPx }
        }
        tvTranslated.minLines = displayLines
        tvTranslated.maxLines = displayLines

        // Không để khung tràn ra ngoài màn hình khi kéo rộng
        layoutParams.x = layoutParams.x.coerceIn(0, (screenW - cardWidthPx).coerceAtLeast(0))

        lastTranslatedShown = null
        if (lastRawTrans.isNotEmpty()) showTranslated(lastRawTrans)
        if (isOverlayAttached) windowManager.updateViewLayout(rootView, layoutParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeHandle() {
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeStartX = event.rawX
                    resizeStartY = event.rawY
                    resizeStartWidth = cardWidthPx
                    resizeStartLines = displayLines
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val lineH = tvTranslated.lineHeight.coerceAtLeast(1)
                    val newLines = resizeStartLines + ((event.rawY - resizeStartY) / lineH).roundToInt()
                    val newWidth = resizeStartWidth + (event.rawX - resizeStartX).toInt()
                    if (newLines != displayLines || newWidth != cardWidthPx) {
                        applyCardSize(newWidth, newLines)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Lưu số dòng vào cài đặt (chiều rộng chỉ giữ trong phiên chạy)
                    currentSettings = currentSettings.copy(maxLines = displayLines)
                    onSettingsChanged?.invoke(currentSettings)
                    onPositionChanged?.invoke(layoutParams.x, layoutParams.y)
                    true
                }
                else -> false
            }
        }
    }

    private fun setTranslated(text: String) {
        if (text == lastTranslatedShown) return
        lastTranslatedShown = text
        tvTranslated.text = text
    }

    private fun setOriginal(text: String) {
        if (text == lastOriginalShown) return
        lastOriginalShown = text
        tvOriginal.text = text
    }

    private fun setTranslatedAlpha(alpha: Float) {
        if (tvTranslated.alpha != alpha) tvTranslated.alpha = alpha
    }

    fun updateSubtitles(state: SubtitleState) {
        if (!isOverlayAttached) return

        rootView.post {
            when (state.status) {
                PipelineStatus.SILENCE, PipelineStatus.IDLE -> {
                    if (state.translatedText.isBlank()) {
                        setTranslated("")
                        setOriginal("")
                    }
                }
                PipelineStatus.PAUSED -> {
                    setTranslated("[Tạm dừng]")
                    setTranslatedAlpha(0.5f)
                }
                else -> {
                    val rawTrans = state.translatedText.ifBlank {
                        if (state.originalText.isNotBlank()) state.originalText else ""
                    }
                    showTranslated(rawTrans)

                    // Giảm độ chênh alpha giữa partial / final để đỡ nhấp nháy
                    setTranslatedAlpha(if (state.isFinal) 1.0f else 0.85f)

                    if (currentSettings.showOriginalText) {
                        // Giữ nguyên VISIBLE khi bật hiển thị gốc (không bật/tắt GONE liên tục -> khung không nhảy)
                        val orig = if (state.originalText.isNotBlank() && state.originalText != rawTrans) {
                            limitLength(state.originalText, MAX_ORIGINAL_CHARS)
                        } else {
                            ""
                        }
                        setOriginal(orig)
                    }
                }
            }
        }
    }

    fun applySettings(settings: AppSettings) {
        currentSettings = settings
        if (!isOverlayAttached) return

        rootView.post {
            tvTranslated.setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSizeSp)
            tvOriginal.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                (settings.fontSizeSp * 0.75f).coerceAtLeast(12f)
            )
            displayLines = settings.maxLines.coerceIn(MIN_LINES, MAX_LINES)
            tvTranslated.minLines = displayLines
            tvTranslated.maxLines = displayLines
            tvTranslated.ellipsize = null
            tvOriginal.ellipsize = TextUtils.TruncateAt.END

            // Đổi cỡ chữ / số dòng thì phải tính lại phần chữ hiển thị
            lastTranslatedShown = null
            if (lastRawTrans.isNotEmpty()) showTranslated(lastRawTrans)

            // Background Opacity
            val baseDrawable = ContextCompat.getDrawable(context, R.drawable.bg_overlay_subtitle)?.mutate()
            if (baseDrawable is GradientDrawable) {
                val alphaInt = (settings.backgroundOpacity * 255).roundToInt().coerceIn(20, 255)
                baseDrawable.setColor(Color.argb(alphaInt, 18, 20, 26))
                subtitleCard.background = baseDrawable
            }

            tvOriginal.visibility = if (settings.showOriginalText) View.VISIBLE else View.GONE
            toggleClickThrough(settings.clickThroughEnabled)
        }
    }

    private fun toggleClickThrough(enabled: Boolean) {
        currentSettings = currentSettings.copy(clickThroughEnabled = enabled)
        if (!isOverlayAttached) return

        val wasEnabled = (layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        if (wasEnabled != enabled) {
            if (enabled) {
                layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            windowManager.updateViewLayout(rootView, layoutParams)
        }
        onSettingsChanged?.invoke(currentSettings)
    }

    private fun clampToScreen() {
        val metrics = context.resources.displayMetrics
        val maxX = (metrics.widthPixels - cardWidthPx).coerceAtLeast(0)
        val maxY = metrics.heightPixels - 100
        layoutParams.x = layoutParams.x.coerceIn(0, maxX)
        layoutParams.y = layoutParams.y.coerceIn(0, maxY)
    }

    fun destroy() {
        if (isOverlayAttached) {
            try {
                windowManager.removeView(rootView)
                Log.i(TAG, "Floating overlay removed from WindowManager")
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay view: ${e.message}")
            } finally {
                isOverlayAttached = false
            }
        }
    }
}
