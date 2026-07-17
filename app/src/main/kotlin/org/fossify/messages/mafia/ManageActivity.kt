package org.fossify.messages.mafia

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.GestureDetectorCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.messages.R
import java.text.Collator
import java.util.Locale
import kotlin.random.Random
import android.text.TextUtils

class ManageActivity : BaseGameActivity() {

    data class TableRow(val playerName: String, val roleName: String, val side: String, val lives: Int = 1, val selectionType: Int = 0)
    data class ExtraColumn(val name: String, val cells: MutableList<String>, val confirmed: Boolean = false)

    private val gson = mafiaGson
    private lateinit var scrollHeadersRow: LinearLayout
    private lateinit var scrollDataLayout: LinearLayout
    private lateinit var scrollRoot: LinearLayout
    private lateinit var horizontalScroll: HorizontalScrollView
    private lateinit var editSwitch: SwitchMaterial       // was Switch
    private lateinit var editButtonsLayout: LinearLayout
    private lateinit var fixedBodyLayout: LinearLayout
    private lateinit var bodyHorizontalScroll: HorizontalScrollView
    private lateinit var timerLayout: FrameLayout
    private lateinit var timerDisplay: MaterialButton    // was Button
    private lateinit var toolsLayout: LinearLayout
    private lateinit var fixedHeaderContainer: LinearLayout
    private var tableData = mutableListOf<TableRow>()
    private var extraColumns = mutableListOf<ExtraColumn>()
    private var isEditMode = false
    private var lastScrollX = 0
    private val cellWidthDp = 90; private val rowHeightDp = 52
    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    // ── role categorisation (unchanged from original) ───────────────────
    private val dayRoles = listOf("جلب", "قاضی", "شهردار", "جادوگر", "شاه کش")
    private val neverActRoles = listOf("شهروند ساده1", "شهروند ساده2", "شهروند ساده3", "شهروند ساده4", "رویین تن", "ساده", "مافیا ساده", "یاغی", "بمب ساز", "فدایی", "کابوس", "معشوقه")
    private val onceOnlyRoles = listOf("ناتو", "اسنایپر", "بازپرس", "سرباز", "مین گذار", "تفنگ ساز", "دستکج")
    private val counterRoles = listOf("گورکن", "گورکن ستاره ")
    private val selfOnceRoles = listOf("جراح", "دکتر", "دکتر ستاره")

    private var timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var timerRunning = false
    private var totalSeconds = 0
    private var remainingSeconds = 0
    private var bellPlayedThisCycle = false
    private var mediaPlayer: android.media.MediaPlayer? = null
    private val cardList = mutableListOf<String>()
    private val persianCollator = Collator.getInstance(Locale.forLanguageTag("fa"))

    // ── colour helpers (theme-aware) ────────────────────────────────────
    private val primaryColor get() = getProperPrimaryColor()
    private val textColor get() = getProperTextColor()
    // A background color is "dark" when its luminance is below 0.5.
    // getProperBackgroundColor() returns the actual current background — light or dark —
    // so this correctly tracks system-level dark/light mode switches.
    private fun isDarkTheme() = ColorUtils.calculateLuminance(getProperBackgroundColor()) < 0.5
    private val headerBgColor get() = if (isDarkTheme()) 0xFF2C2C2C.toInt() else 0xFFE0E0E0.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadTableData(); loadExtraColumns(); loadCardsFromAssets()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        // ── Awake / Asleep switch ─────────────────────────────────────
        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        editSwitch = SwitchMaterial(this).apply {
            isChecked = false
            setOnCheckedChangeListener { _, checked ->
                isEditMode = checked
                refreshEditButtons(); refreshTable()
                refreshTimerVisibility(); refreshToolsVisibility()
            }
        }
        switchRow.addView(makeLabel(getString(R.string.mafia_awake)))
        switchRow.addView(editSwitch)
        switchRow.addView(makeLabel(getString(R.string.mafia_asleep)))

        // ── Fixed header (name / role / life columns) ─────────────────
        fixedHeaderContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(headerBgColor)
        }
        fixedHeaderContainer.addView(headerCell("جان"), LinearLayout.LayoutParams(dp(50), dp(rowHeightDp)))
        fixedHeaderContainer.addView(headerCell("نقش"), LinearLayout.LayoutParams(dp(80), dp(rowHeightDp)))
        fixedHeaderContainer.addView(headerCell("نام"), LinearLayout.LayoutParams(dp(90), dp(rowHeightDp)))

        horizontalScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        scrollHeadersRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        horizontalScroll.addView(scrollHeadersRow)
        fixedHeaderContainer.addView(horizontalScroll, LinearLayout.LayoutParams(0, dp(rowHeightDp), 1f))

        // ── Scrollable table body ─────────────────────────────────────
        val bodyVerticalScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val bodyContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        fixedBodyLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bodyContainer.addView(fixedBodyLayout)

        bodyHorizontalScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        scrollRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollDataLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollRoot.addView(scrollDataLayout)
        bodyHorizontalScroll.addView(scrollRoot)
        bodyContainer.addView(bodyHorizontalScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bodyVerticalScroll.addView(bodyContainer)

        // Keep both scroll views in sync
        horizontalScroll.setOnScrollChangeListener { _, _, _, _, _ -> bodyHorizontalScroll.scrollX = horizontalScroll.scrollX }
        bodyHorizontalScroll.setOnScrollChangeListener { _, _, _, _, _ -> horizontalScroll.scrollX = bodyHorizontalScroll.scrollX }

        val hasBazporsOrNamayandeh = tableData.any { it.roleName == "رویین تن" || it.roleName == "یاغی" }

        // ── Tools row (card / inquiry / talk / lottery) ───────────────
        toolsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
            visibility = if (isEditMode) View.GONE else View.VISIBLE
        }
        if (!hasBazporsOrNamayandeh) toolsLayout.addView(createSmallButton("کارت") { showCardDialog() })
        val inquiryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        inquiryRow.addView(createSmallButton("استعلام") { showInquiryDialog() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toolsLayout.addView(inquiryRow)
        val toolsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        toolsRow.addView(createSmallButton("سر صحبت") { showTalkDialog() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(6), 0) })
        if (!hasBazporsOrNamayandeh) toolsRow.addView(createSmallButton("قرعه مرگ") { showDeathLotteryDialog() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        toolsLayout.addView(toolsRow)

        // ── Timer ─────────────────────────────────────────────────────
        val timerCircleSize = dp(180)
        timerDisplay = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "۰۰:۰۰"
            setTextColor(primaryColor)
            textSize = 30f
            strokeColor = android.content.res.ColorStateList.valueOf(primaryColor)
            strokeWidth = dp(3)
            cornerRadius = timerCircleSize / 2
            setPadding(0, 0, 0, 0)
            setOnLongClickListener { showTimerInputDialog(); true }
        }

        @Suppress("DEPRECATION")
        val gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { stopTimer(); remainingSeconds = 0; startTimer(); return true }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { if (!timerRunning) startTimer() else stopTimer(); return true }
        })
        timerDisplay.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); false }

        timerLayout = FrameLayout(this).apply { visibility = if (isEditMode) View.GONE else View.VISIBLE }
        timerLayout.addView(timerDisplay, FrameLayout.LayoutParams(timerCircleSize, timerCircleSize, Gravity.CENTER))

        // ── Edit-mode action buttons (add night / midday) ─────────────
        editButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            visibility = if (isEditMode) View.VISIBLE else View.GONE
        }
        editButtonsLayout.addView(createSmallButton("شب") { addColumn("شب") }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(6), 0) })
        if (!hasBazporsOrNamayandeh) editButtonsLayout.addView(createSmallButton("نیمروز") { addColumn("نیمروز") }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(6), 0, 0, 0) })

        rootLayout.addView(switchRow)
        rootLayout.addView(fixedHeaderContainer)
        rootLayout.addView(bodyVerticalScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        rootLayout.addView(timerLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        rootLayout.addView(toolsLayout)
        rootLayout.addView(editButtonsLayout)

        setContentView(rootLayout)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "جدول بازی"
        refreshTable()
    }

    // ── small helpers ────────────────────────────────────────────────────

    private fun makeLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(textColor)
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun headerCell(text: String) = TextView(this).apply {
        this.text = text.replace('\n', ' ')
        textSize = 15f
        setTextColor(textColor)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(4), dp(4), dp(4))
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        setHorizontallyScrolling(true)
    }

    private fun dataCell(text: String, strike: Boolean) = TextView(this).apply {
        this.text = text.replace('\n', ' ')
        textSize = 15f
        setTextColor(textColor)
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(4), dp(4), dp(4))
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        setHorizontallyScrolling(true)
        if (strike) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
    }

    private fun createSmallButton(text: String, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        textSize = 12f
        cornerRadius = dp(24)
        setOnClickListener { onClick() }
    }

    // ── visibility helpers ───────────────────────────────────────────────

    private fun refreshTimerVisibility() { timerLayout.visibility = if (!isEditMode) View.VISIBLE else View.GONE }
    private fun refreshToolsVisibility() { toolsLayout.visibility = if (!isEditMode) View.VISIBLE else View.GONE }
    private fun refreshEditButtons() { editButtonsLayout.visibility = if (isEditMode) View.VISIBLE else View.GONE }

    // ── timer ────────────────────────────────────────────────────────────

    private fun showTimerInputDialog() {
        val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(40), dp(20), dp(40), dp(20)) }
        val inp = EditText(this).apply { hint = "ثانیه"; inputType = InputType.TYPE_CLASS_NUMBER }
        lo.addView(TextView(this).apply { text = "زمان را وارد کنید (ثانیه):"; setPadding(0, 0, 0, dp(16)) })
        lo.addView(inp)
        MaterialAlertDialogBuilder(this).setTitle("تنظیم زمان").setView(lo)
            .setPositiveButton("تایید") { _, _ ->
                val sec = inp.text.toString().toIntOrNull() ?: 0
                if (sec > 0) {
                    stopTimer(); totalSeconds = sec; remainingSeconds = 0
                    timerDisplay.text = "${toPersianDigits(sec / 60)}:${toPersianDigits(sec % 60)}"
                    timerDisplay.setTextColor(primaryColor)
                }
            }.setNegativeButton("انصراف", null).show()
    }

    private fun toPersianDigits(n: Int): String {
        val p = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
        return n.toString().padStart(2, '0').map { if (it.isDigit()) p[it - '0'] else it.toString() }.joinToString("")
    }

    private fun startTimer() {
        bellPlayedThisCycle = false
        if (totalSeconds <= 0) { showTimerInputDialog(); return }
        if (remainingSeconds <= 0) remainingSeconds = totalSeconds
        timerRunning = true
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = object : Runnable {
            override fun run() {
                remainingSeconds--
                val abs = kotlin.math.abs(remainingSeconds)
                val prefix = if (remainingSeconds < 0) "-" else ""
                timerDisplay.text = "$prefix${toPersianDigits(abs / 60)}:${toPersianDigits(abs % 60)}"
                if (remainingSeconds <= 0) {
                    timerDisplay.setTextColor(Color.RED)
                    if (!bellPlayedThisCycle) { playBellSound(); bellPlayedThisCycle = true }
                } else {
                    timerDisplay.setTextColor(primaryColor)
                }
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.postDelayed(timerRunnable!!, 1000)
    }

    private fun stopTimer() { timerRunnable?.let { timerHandler.removeCallbacks(it) }; timerRunning = false }

    private fun playBellSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = android.media.MediaPlayer().apply {
                val afd = assets.openFd("bell.mp3")
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close(); prepare(); start()
                setOnCompletionListener { it.release() }
            }
        } catch (e: Exception) { }
    }

    // ── column management ────────────────────────────────────────────────

    private fun getNextNightNumber() =
        (extraColumns.filter { it.name.startsWith("شب") }.mapNotNull { it.name.removePrefix("شب").toIntOrNull() }.maxOrNull() ?: 0) + 1

    private fun addColumn(prefix: String) {
        if (prefix == "نیمروز" && extraColumns.isNotEmpty() && extraColumns.last().name.startsWith("نیمروز")) {
            Toast.makeText(this, "نیمروز قبلا ثبت شده", Toast.LENGTH_SHORT).show(); return
        }
        if (extraColumns.any { !it.confirmed }) {
            Toast.makeText(this, "نخست ستون فعلی را تایید کنید", Toast.LENGTH_SHORT).show(); return
        }
        extraColumns.add(ExtraColumn(if (prefix == "شب") "شب${getNextNightNumber()}" else "نیمروز", MutableList(tableData.size) { "" }, false))
        saveExtraColumns(); refreshTable()
        bodyHorizontalScroll.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                bodyHorizontalScroll.viewTreeObserver.removeOnGlobalLayoutListener(this)
                bodyHorizontalScroll.fullScroll(View.FOCUS_LEFT)
                horizontalScroll.fullScroll(View.FOCUS_LEFT)
            }
        })
    }

    // ── persistence ──────────────────────────────────────────────────────

    private fun loadTableData() {
        val f = java.io.File(filesDir, "game.json"); if (!f.exists()) return
        try {
            val s: ResultActivity.SavedGame = gson.fromJson(f.readText(), object : TypeToken<ResultActivity.SavedGame>() {}.type)
            tableData.clear(); s.results.forEach { tableData.add(TableRow(it.playerName, it.role.name, it.role.side, it.lives, it.role.selectionType)) }
        } catch (e: Exception) { }
    }

    private fun saveTableData() {
        val f = java.io.File(filesDir, "game.json")
        if (!f.exists()) return
        try {
            val savedGame: ResultActivity.SavedGame = gson.fromJson(f.readText(), object : TypeToken<ResultActivity.SavedGame>() {}.type)
            val updatedResults = savedGame.results.mapIndexed { idx, result ->
                if (idx < tableData.size) result.copy(lives = tableData[idx].lives)
                else result
            }
            val updatedGame = savedGame.copy(results = updatedResults)
            f.writeText(gson.toJson(updatedGame))
            android.util.Log.d("ManageActivity", "Game saved successfully")
        } catch (e: Exception) {
            android.util.Log.e("ManageActivity", "Error saving game", e)
        }
    }

    private fun loadExtraColumns() {
        val f = java.io.File(filesDir, "extra_columns.json"); if (!f.exists()) return
        try { extraColumns.clear(); extraColumns.addAll(gson.fromJson(f.readText(), object : TypeToken<List<ExtraColumn>>() {}.type)) } catch (e: Exception) { }
    }

    private fun saveExtraColumns() { java.io.File(filesDir, "extra_columns.json").writeText(gson.toJson(extraColumns)) }

    private fun loadCardsFromAssets() {
        try {
            val j = assets.open("cards.json").bufferedReader().use { it.readText() }
            val d: Map<String, List<String>> = gson.fromJson(j, object : TypeToken<Map<String, List<String>>>() {}.type)
            cardList.clear(); cardList.addAll(d["CARDS"] ?: emptyList())
        } catch (e: Exception) { }
    }

    // ── table rendering (logic unchanged, only colour references updated) ─

    private fun hasJalebWorkedInPreviousNights(cIdx: Int, rIdx: Int) = extraColumns.take(cIdx).filter { it.name.startsWith("شب") }
        .any { it.cells.getOrElse(rIdx) { "" }.let { c -> c.isNotEmpty() && c != "-" } }
    private fun hasWizardWorked(cIdx: Int, isDay: Boolean, rIdx: Int) = extraColumns.take(cIdx).any { (it.name.startsWith("نیمروز") == isDay) && it.cells.getOrElse(rIdx) { "" }.let { c -> c.isNotEmpty() && c != "-" } }
    private fun hasOnceOnlyWorked(cIdx: Int, rIdx: Int) = extraColumns.take(cIdx).any { it.cells.getOrElse(rIdx) { "" }.let { c -> c.isNotEmpty() && c != "-" } }
    private fun canRammalAct(cIdx: Int, rIdx: Int, nightNum: Int): Boolean {
        val nights = extraColumns.take(cIdx).filter { it.name.startsWith("شب") }
        val worked = nights.any { it.cells.getOrElse(rIdx) { "" }.let { c -> c.isNotEmpty() && c != "-" } }
        if (!worked) return true
        val first = nights.indexOfFirst { it.cells.getOrElse(rIdx) { "" }.let { c -> c.isNotEmpty() && c != "-" } }
        if (first == -1) return false
        if (getNightNumber(nights[first].name) % 2 != 0) return false
        val cnt = nights.count { getNightNumber(it.name) % 2 == 0 && it.cells.getOrElse(rIdx) { "" }.let { c -> c.isNotEmpty() && c != "-" } }
        return cnt < 2 && nightNum % 2 == 0
    }
    private fun getNightNumber(colName: String) = colName.removePrefix("شب").toIntOrNull() ?: 0
    private fun findMaxNumber(colIdx: Int, rowIdx: Int): Int { var max = 0; for (c in 0 until colIdx) { val n = extraColumns.getOrNull(c)?.cells?.getOrElse(rowIdx) { "" }?.toIntOrNull() ?: continue; if (n > max) max = n }; return max }
    private fun shahKeshCount(colIdx: Int, rowIdx: Int): Int { var c = 0; for (i in 0 until colIdx) { val cell = extraColumns.getOrNull(i)?.cells?.getOrElse(rowIdx) { "" } ?: ""; if (cell.isNotEmpty() && cell != "-") c++ }; return c + 1 }
    private fun canDeleteColumn(colIdx: Int) = colIdx == extraColumns.size - 1

    private fun rowBg(side: String): Int = when (side) {
        "مافیا" -> if (isDarkTheme()) 0xFF4A1010.toInt() else 0xFFFFCDD2.toInt()
        "شهروند" -> if (isDarkTheme()) 0xFF0D2A3A.toInt() else 0xFFBBDEFB.toInt()
        "مستقل" -> if (isDarkTheme()) 0xFF3A2800.toInt() else 0xFFFFE0B2.toInt()
        else -> Color.TRANSPARENT
    }

    private fun refreshTable() {
        if (!isEditMode) {
            scrollHeadersRow.removeAllViews(); scrollDataLayout.removeAllViews(); fixedBodyLayout.removeAllViews()
            fixedHeaderContainer.visibility = View.GONE; bodyHorizontalScroll.visibility = View.GONE; horizontalScroll.visibility = View.GONE
            return
        }
        fixedHeaderContainer.visibility = View.VISIBLE; bodyHorizontalScroll.visibility = View.VISIBLE; horizontalScroll.visibility = View.VISIBLE
        lastScrollX = bodyHorizontalScroll.scrollX
        scrollHeadersRow.removeAllViews(); scrollDataLayout.removeAllViews(); fixedBodyLayout.removeAllViews()
        val rh = dp(rowHeightDp)

        // calculate column widths based on longest content (headers + cells)
        val sDensity = resources.displayMetrics.scaledDensity
        val headerPaintLarge = android.graphics.Paint().apply { isAntiAlias = true; textSize = 15f * sDensity }
        val headerPaintSmall = android.graphics.Paint().apply { isAntiAlias = true; textSize = 13f * sDensity }
        val cellPaint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 15f * sDensity }

        val fixedHeaders = listOf("جان", "نقش", "نام")
        val minFixed = listOf(dp(50), dp(80), dp(90))
        val fixedWidths = mutableListOf<Int>()
        for (i in fixedHeaders.indices) {
            var maxW = headerPaintLarge.measureText(fixedHeaders[i]).toInt()
            for ((rowIdx, row) in tableData.withIndex()) {
                val content = when (i) {
                    0 -> if (row.lives == 0) "🖤" else "❤️"
                    1 -> displayRoleName(row.roleName)
                    else -> row.playerName
                }
                val measured = cellPaint.measureText(content.replace('\n', ' ')).toInt()
                maxW = maxOf(maxW, measured)
            }
            fixedWidths.add(maxOf(maxW + dp(8), minFixed[i]))
        }

        // prepare scrollable columns (extraColumns) widths
        val scrollColWidths = mutableListOf<Int>()
        extraColumns.forEachIndexed { idx, col ->
            var maxW = 0
            // header width
            val headerW = headerPaintSmall.measureText(if (col.confirmed) col.name else "✅ ${col.name}").toInt()
            maxW = maxOf(maxW, headerW)
            // cells width
            for (r in tableData.indices) {
                val raw = if (col.confirmed && col.cells.getOrElse(r) { "" }.isEmpty()) "-" else displayCellText(col.cells.getOrElse(r) { "" })
                val cellText = raw.replace('\n', ' ')
                val measured = cellPaint.measureText(cellText).toInt()
                maxW = maxOf(maxW, measured)
            }
            scrollColWidths.add(maxW + dp(8))
        }

        // apply fixed widths to header fixed cells (first three children of fixedHeaderContainer)
        try {
            for (i in 0 until minOf(3, fixedHeaderContainer.childCount)) {
                val lp = fixedHeaderContainer.getChildAt(i).layoutParams as? LinearLayout.LayoutParams
                if (lp != null) {
                    lp.width = fixedWidths[i]
                    lp.height = rh
                    fixedHeaderContainer.getChildAt(i).layoutParams = lp
                }
            }
        } catch (e: Exception) { }

        // add header views for scrollable columns
        extraColumns.forEachIndexed { idx, col ->
            val hCell = TextView(this).apply {
                text = if (col.confirmed) col.name else "✅ ${col.name}"
                textSize = 13f; setTextColor(if (col.confirmed) textColor else if (isDarkTheme()) Color.WHITE else Color.BLACK); setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER; setPadding(dp(4), dp(4), dp(4), dp(4))
                if (!col.confirmed) setOnClickListener {
                    extraColumns[idx] = col.copy(cells = col.cells.map { it.ifEmpty { "-" } }.toMutableList(), confirmed = true)
                    saveExtraColumns(); refreshTable()
                } else setOnClickListener { showColumnDetails(col) }
                setOnLongClickListener {
                    if (col.confirmed && canDeleteColumn(idx))
                        MaterialAlertDialogBuilder(this@ManageActivity).setTitle("حذف ستون").setMessage("ستون ${col.name} حذف شود؟")
                            .setPositiveButton("بله") { _, _ -> extraColumns.removeAt(idx); saveExtraColumns(); refreshTable() }
                            .setNegativeButton("خیر", null).show()
                    else if (col.confirmed) Toast.makeText(this@ManageActivity, "فقط آخرین ستون قابل حذف است", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            scrollHeadersRow.addView(hCell, LinearLayout.LayoutParams(scrollColWidths[idx], rh))
        }

        for ((rowIdx, row) in tableData.withIndex()) {
            val isDead = row.lives == 0; val isRaees = row.roleName == "رییس"
            val bg = rowBg(row.side)

            val fixedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setBackgroundColor(bg) }
            fixedRow.addView(TextView(this).apply {
                text = if (isDead) "🖤" else "❤️"; textSize = 18f; gravity = Gravity.CENTER
                if (isEditMode) setOnClickListener { tableData[rowIdx] = tableData[rowIdx].copy(lives = if (isDead) 1 else 0); saveTableData(); refreshTable() }
            }, LinearLayout.LayoutParams(fixedWidths.getOrNull(0) ?: dp(50), rh))
            fixedRow.addView(dataCell(displayRoleName(row.roleName), isDead), LinearLayout.LayoutParams(fixedWidths.getOrNull(1) ?: dp(80), rh))
            fixedRow.addView(dataCell(row.playerName, isDead), LinearLayout.LayoutParams(fixedWidths.getOrNull(2) ?: dp(90), rh))
            fixedBodyLayout.addView(fixedRow)

            val scrollRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setBackgroundColor(bg) }
            extraColumns.forEachIndexed { colIdx, col ->
                val isDayColumn = col.name.startsWith("نیمروز"); val nightNum = getNightNumber(col.name)
                val cellBlack = when {
                    neverActRoles.contains(row.roleName) -> true
                    row.roleName == "جادوگر" -> isDead || hasWizardWorked(colIdx, isDayColumn, rowIdx)
                    row.roleName == "جلب" -> when {
                        isDead -> true
                        isDayColumn -> false
                        else -> hasJalebWorkedInPreviousNights(colIdx, rowIdx)
                    }
                    row.roleName == "قهرمان" -> isDead || isDayColumn || (nightNum % 2 != 0)
                    row.roleName == "رمال" -> isDead || isDayColumn || !canRammalAct(colIdx, rowIdx, nightNum)
                    row.roleName == "پزشک" -> isDead || isDayColumn
                    row.roleName == "کشیش" -> isDead || isDayColumn
                    row.roleName in onceOnlyRoles -> isDead || isDayColumn || hasOnceOnlyWorked(colIdx, rowIdx)
                    isDayColumn -> isDead || !dayRoles.contains(row.roleName)
                    row.roleName in listOf("قاضی", "شهردار") -> true
                    else -> isDead && !isRaees
                }
                val activeCellBg = ColorUtils.blendARGB(primaryColor, Color.WHITE, 0.5f)
                val cellBg = when {
                    col.confirmed && row.side == "مافیا" -> if (isDarkTheme()) 0xFF5C1A1A.toInt() else 0xFFFFE3E5.toInt()
                    col.confirmed && row.side == "شهروند" -> if (isDarkTheme()) 0xFF0D2D4A.toInt() else 0xFFD2E6FA.toInt()
                    col.confirmed && row.side == "مستقل" -> if (isDarkTheme()) 0xFF3A2800.toInt() else 0xFFFFF3E0.toInt()
                    col.confirmed -> Color.TRANSPARENT
                    cellBlack -> if (isDarkTheme()) 0xFF111111.toInt() else Color.BLACK
                    else -> activeCellBg
                }
                val txt = if (col.confirmed && col.cells.getOrElse(rowIdx) { "" }.isEmpty()) "-" else displayCellText(col.cells.getOrElse(rowIdx) { "" })
                val cellText = TextView(this).apply {
                    text = txt.replace('\n', ' ')
                    textSize = 14f
                    setTextColor(if (col.confirmed) textColor else Color.BLACK)
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    setBackgroundColor(cellBg)
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    if (!col.confirmed && !cellBlack) setOnClickListener { handleCellClick(colIdx, rowIdx, row, isDayColumn) }
                }
                val w = scrollColWidths.getOrNull(colIdx) ?: dp(cellWidthDp)
                scrollRow.addView(cellText, LinearLayout.LayoutParams(w, rh))
            }
            scrollDataLayout.addView(scrollRow)
        }
        bodyHorizontalScroll.post { bodyHorizontalScroll.scrollX = lastScrollX; horizontalScroll.scrollX = lastScrollX }
    }

    // ── cell click handlers (logic unchanged) ────────────────────────────

    private fun handleCellClick(cIdx: Int, rIdx: Int, row: TableRow, isDay: Boolean) {
        val allKnownRoles = dayRoles + neverActRoles + onceOnlyRoles + counterRoles + selfOnceRoles +
            listOf("جلب", "قاضی", "شهردار", "جادوگر", "شاه کش", "قهرمان", "بازپرس", "رمال", "شب خسب", "رییس", "افشاگر", "پزشک", "کشیش")
        val aliveCount = tableData.count { it.lives > 0 }
        val isCustomRole = row.roleName !in allKnownRoles
        when {
            row.roleName == "جلب" -> if (isDay) showYesNoDialog(cIdx, rIdx, "جلب") { extraColumns[cIdx].cells[rIdx] = "✅" } else showJalbNightPicker(cIdx, rIdx, row)
            row.roleName == "پزشک" -> if (aliveCount >= 10) showTwoSelectPicker(cIdx, rIdx, row) else showPlayerPicker(cIdx, rIdx, row)
            row.roleName == "کشیش" -> showTwoSelectPicker(cIdx, rIdx, row)
            row.roleName in listOf("قاضی", "شهردار") -> showYesNoDialog(cIdx, rIdx, row.roleName) { extraColumns[cIdx].cells[rIdx] = (findMaxNumber(cIdx, rIdx) + 1).toString() }
            row.roleName in counterRoles -> showYesNoDialog(cIdx, rIdx, row.roleName) { extraColumns[cIdx].cells[rIdx] = (findMaxNumber(cIdx, rIdx) + 1).toString() }
            row.roleName == "شاه کش" -> showShahKeshPicker(cIdx, rIdx, row)
            row.roleName == "بازپرس" -> showBazporsPicker(cIdx, rIdx, row)
            row.roleName == "افشاگر" -> showAfshagarPicker(cIdx, rIdx, row)
            row.roleName == "رمال" -> showRammalPicker(cIdx, rIdx, row)
            row.roleName == "شب خسب" -> { val prev = extraColumns.take(cIdx).findLast { it.name.startsWith("شب") }?.cells?.getOrElse(rIdx) { "" } ?: ""; showFilteredPlayerPicker(cIdx, rIdx, row) { p -> p.side != "مافیا" && p.playerName != prev } }
            row.roleName in selfOnceRoles -> { val sn = row.playerName; val hp = extraColumns.any { col -> col.cells.getOrElse(rIdx) { "" } == sn }; showFilteredPlayerPicker(cIdx, rIdx, row) { p -> !(p.playerName == sn && hp) } }
            isCustomRole && row.selectionType == RoleActivity.SELECT_TWO -> showTwoSelectPicker(cIdx, rIdx, row)
            isCustomRole && row.selectionType == RoleActivity.SELECT_MULTI -> showMultiSelectPicker(cIdx, rIdx, row)
            else -> showPlayerPicker(cIdx, rIdx, row)
        }
    }

    private fun showYesNoDialog(cIdx: Int, rIdx: Int, roleName: String, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(this).setTitle(roleName).setMessage("$roleName کار میکند؟")
            .setPositiveButton("بله") { _, _ -> onYes(); saveExtraColumns(); refreshTable() }
            .setNegativeButton("خیر") { _, _ -> extraColumns[cIdx].cells[rIdx] = ""; saveExtraColumns(); refreshTable() }.show()
    }

    private fun showJalbNightPicker(cIdx: Int, rIdx: Int, cr: TableRow) {
        val a = tableData.filter { it.lives > 0 }.map { it.playerName }.sortedWith(persianCollator)
        MaterialAlertDialogBuilder(this).setTitle("${cr.roleName}: ${cr.playerName}")
            .setItems((a + listOf("پاکسازی")).toTypedArray()) { _, w ->
                if (w < a.size) extraColumns[cIdx].cells[rIdx] = a[w] else extraColumns[cIdx].cells[rIdx] = ""
                saveExtraColumns(); refreshTable()
            }
            .setNegativeButton("بستن", null).show()
    }

    private fun showShahKeshPicker(cIdx: Int, rIdx: Int, cr: TableRow) {
        val a = tableData.filter { it.lives > 0 }.map { it.playerName }.sortedWith(persianCollator)
        val ct = shahKeshCount(cIdx, rIdx)
        MaterialAlertDialogBuilder(this).setTitle("${cr.roleName}: ${cr.playerName}")
            .setItems((a + listOf("پاکسازی")).toTypedArray()) { _, w -> if (w < a.size) extraColumns[cIdx].cells[rIdx] = "$ct.${a[w]}" else extraColumns[cIdx].cells[rIdx] = ""; saveExtraColumns(); refreshTable() }
            .setNegativeButton("بستن", null).show()
    }

    private fun showAfshagarPicker(cIdx: Int, rIdx: Int, cr: TableRow) {
        val allPlayers = tableData.filter { it.lives > 0 }.map { it.playerName }.sortedWith(persianCollator)
        val prev = extraColumns[cIdx].cells.getOrElse(rIdx) { "" }.split(" و ").filter { it.isNotBlank() }
        val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        lo.addView(TextView(this).apply { text = "${cr.roleName}: ${cr.playerName}"; textSize = 16f; setPadding(0, 0, 0, dp(16)); setTextColor(textColor) })
        val lv = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = object : ArrayAdapter<String>(this@ManageActivity, android.R.layout.simple_list_item_multiple_choice, allPlayers) {
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val v = super.getView(position, convertView, parent)
                    (v as? android.widget.CheckedTextView)?.setTextColor(textColor)
                    return v
                }
            }
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val listHeight = (resources.displayMetrics.heightPixels * 0.35).toInt()
        lo.addView(lv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, listHeight))
        lo.addView(TextView(this).apply { text = "حداکثر ۲ نفر قابل انتخاب است"; textSize = 13f; setPadding(0, dp(8), 0, 0); setTextColor(textColor) })
        val br = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
        val clr = MaterialButton(this).apply { text = "پاکسازی"; minimumHeight = dp(48) }
        val confirm = MaterialButton(this).apply { text = "تایید"; minimumHeight = dp(48) }
        br.addView(clr, LinearLayout.LayoutParams(0, dp(48), 1f))
        br.addView(confirm, LinearLayout.LayoutParams(0, dp(48), 1f))
        lo.addView(br)
        val d = MaterialAlertDialogBuilder(this).setView(lo).create(); d.show()
        prev.forEach { p -> allPlayers.indexOf(p).takeIf { it >= 0 }?.let { lv.setItemChecked(it, true) } }
        confirm.setOnClickListener {
            val s = (0 until allPlayers.size).filter { lv.isItemChecked(it) }.map { allPlayers[it] }
            if (s.size > 2) { Toast.makeText(this, "حداکثر ۲ نفر", Toast.LENGTH_SHORT).show() }
            else { extraColumns[cIdx].cells[rIdx] = s.joinToString(" و "); saveExtraColumns(); d.dismiss(); refreshTable() }
        }
        clr.setOnClickListener { extraColumns[cIdx].cells[rIdx] = ""; saveExtraColumns(); d.dismiss(); refreshTable() }
        lv.setOnItemClickListener { _, _, position, _ ->
            val s = (0 until allPlayers.size).filter { lv.isItemChecked(it) }.map { allPlayers[it] }
            if (s.size > 2) { lv.setItemChecked(position, false); Toast.makeText(this, "حداکثر ۲ نفر", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun showRammalPicker(cIdx: Int, rIdx: Int, cr: TableRow) {
        val a = tableData.filter { it.lives > 0 }.map { it.playerName }.sortedWith(persianCollator)
        val items = a + listOf("گورکنی", "پاکسازی")
        MaterialAlertDialogBuilder(this).setTitle("${cr.roleName}: ${cr.playerName}")
            .setItems(items.toTypedArray()) { _, w -> when { w < a.size -> extraColumns[cIdx].cells[rIdx] = a[w]; w == a.size -> extraColumns[cIdx].cells[rIdx] = "گورکنی"; else -> extraColumns[cIdx].cells[rIdx] = "" }; saveExtraColumns(); refreshTable() }
            .setNegativeButton("بستن", null).show()
    }

    private fun showBazporsPicker(cIdx: Int, rIdx: Int, cr: TableRow) {
        val a = tableData.filter { it.lives > 0 }.map { it.playerName }.sortedWith(persianCollator)
        val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        lo.addView(TextView(this).apply { text = "${cr.roleName}: ${cr.playerName}"; textSize = 16f; setPadding(0, 0, 0, dp(16)); setTextColor(textColor) })
        val lv = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = object : ArrayAdapter<String>(this@ManageActivity, android.R.layout.simple_list_item_multiple_choice, a) {
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val v = super.getView(position, convertView, parent)
                    (v as? android.widget.CheckedTextView)?.setTextColor(textColor)
                    return v
                }
            }
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        lo.addView(lv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.35).toInt()))
        val br = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
        val clr = MaterialButton(this).apply { text = "پاکسازی"; minimumHeight = dp(48) }
        val cls = MaterialButton(this).apply { text = "بستن"; minimumHeight = dp(48) }
        br.addView(clr, LinearLayout.LayoutParams(0, dp(48), 1f))
        br.addView(cls, LinearLayout.LayoutParams(0, dp(48), 1f))
        lo.addView(br)
        val d = MaterialAlertDialogBuilder(this).setView(lo).create(); d.show()
        cls.setOnClickListener { d.dismiss() }
        clr.setOnClickListener { extraColumns[cIdx].cells[rIdx] = ""; saveExtraColumns(); d.dismiss(); refreshTable() }
        lv.setOnItemClickListener { _, _, p, _ -> val s = (0 until a.size).filter { lv.isItemChecked(it) }.map { a[it] }; if (s.size <= 2) { extraColumns[cIdx].cells[rIdx] = s.joinToString(" و "); saveExtraColumns(); if (s.size == 2) { d.dismiss(); refreshTable() } } }
    }

    private fun showFilteredPlayerPicker(cIdx: Int, rIdx: Int, cr: TableRow, fl: (TableRow) -> Boolean) {
        val a = tableData.filter { it.lives > 0 && fl(it) }.map { it.playerName }.sortedWith(persianCollator)
        MaterialAlertDialogBuilder(this).setTitle("${cr.roleName}: ${cr.playerName}")
            .setItems((a + listOf("پاکسازی")).toTypedArray()) { _, w -> extraColumns[cIdx].cells[rIdx] = if (w < a.size) a[w] else ""; saveExtraColumns(); refreshTable() }
            .setNegativeButton("بستن", null).show()
    }

    private fun showPlayerPicker(cIdx: Int, rIdx: Int, cr: TableRow) {
        val allPlayers = tableData.filter { it.lives > 0 }.map { it.playerName }.sortedWith(persianCollator)
        MaterialAlertDialogBuilder(this).setTitle("${cr.roleName}: ${cr.playerName}")
            .setItems((allPlayers + listOf("پاکسازی")).toTypedArray()) { _, w -> extraColumns[cIdx].cells[rIdx] = if (w < allPlayers.size) allPlayers[w] else ""; saveExtraColumns(); refreshTable() }
            .setNegativeButton("بستن", null).show()
    }

    private fun showTwoSelectPicker(cIdx: Int, rIdx: Int, cr: TableRow) {
        val allPlayers = tableData.filter { it.lives > 0 }.map { it.playerName }.sortedWith(persianCollator)
        val prev = extraColumns[cIdx].cells.getOrElse(rIdx) { "" }.split(" و ").filter { it.isNotBlank() }
        val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        lo.addView(TextView(this).apply { text = "${cr.roleName}: ${cr.playerName}"; textSize = 16f; setPadding(0, 0, 0, dp(16)); setTextColor(textColor) })
        val lv = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = object : ArrayAdapter<String>(this@ManageActivity, android.R.layout.simple_list_item_multiple_choice, allPlayers) {
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val v = super.getView(position, convertView, parent)
                    (v as? android.widget.CheckedTextView)?.setTextColor(textColor)
                    return v
                }
            }
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val listHeight = (resources.displayMetrics.heightPixels * 0.35).toInt()
        lo.addView(lv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, listHeight))
        lo.addView(TextView(this).apply { text = "حداکثر ۲ نفر قابل انتخاب است"; textSize = 13f; setPadding(0, dp(8), 0, 0); setTextColor(textColor) })
        val br = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
        val clr = MaterialButton(this).apply { text = "پاکسازی"; minimumHeight = dp(48) }
        val cls = MaterialButton(this).apply { text = "بستن"; minimumHeight = dp(48) }
        br.addView(clr, LinearLayout.LayoutParams(0, dp(48), 1f))
        br.addView(cls, LinearLayout.LayoutParams(0, dp(48), 1f))
        lo.addView(br)
        val d = MaterialAlertDialogBuilder(this).setView(lo).create(); d.show()
        prev.forEach { p -> allPlayers.indexOf(p).takeIf { it >= 0 }?.let { lv.setItemChecked(it, true) } }
        cls.setOnClickListener { d.dismiss() }
        clr.setOnClickListener { extraColumns[cIdx].cells[rIdx] = ""; saveExtraColumns(); d.dismiss(); refreshTable() }
        lv.setOnItemClickListener { _, _, position, _ ->
            val s = (0 until allPlayers.size).filter { lv.isItemChecked(it) }.map { allPlayers[it] }
            if (s.size > 2) {
                lv.setItemChecked(position, false)
                Toast.makeText(this, "حداکثر ۲ نفر", Toast.LENGTH_SHORT).show()
            } else {
                extraColumns[cIdx].cells[rIdx] = s.joinToString(" و "); saveExtraColumns()
                if (s.size == 2) { d.dismiss(); refreshTable() }
            }
        }
    }

    private fun showMultiSelectPicker(cIdx: Int, rIdx: Int, cr: TableRow) {
        val allPlayers = tableData.filter { it.lives > 0 }.map { it.playerName }.sortedWith(persianCollator)
        val prev = extraColumns[cIdx].cells.getOrElse(rIdx) { "" }.split(" و ").filter { it.isNotBlank() }
        val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        lo.addView(TextView(this).apply { text = "${cr.roleName}: ${cr.playerName}"; textSize = 16f; setPadding(0, 0, 0, dp(16)); setTextColor(textColor) })
        val lv = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = object : ArrayAdapter<String>(this@ManageActivity, android.R.layout.simple_list_item_multiple_choice, allPlayers) {
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val v = super.getView(position, convertView, parent)
                    (v as? android.widget.CheckedTextView)?.setTextColor(textColor)
                    return v
                }
            }
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        lo.addView(lv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.35).toInt()))
        val br = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
        val cancel = MaterialButton(this).apply { text = "لغو"; minimumHeight = dp(48) }
        val finish = MaterialButton(this).apply { text = "پایان"; minimumHeight = dp(48) }
        br.addView(cancel, LinearLayout.LayoutParams(0, dp(48), 1f))
        br.addView(finish, LinearLayout.LayoutParams(0, dp(48), 1f))
        lo.addView(br)
        val d = MaterialAlertDialogBuilder(this).setView(lo).create(); d.show()
        prev.forEach { p -> allPlayers.indexOf(p).takeIf { it >= 0 }?.let { lv.setItemChecked(it, true) } }
        cancel.setOnClickListener { d.dismiss() }
        finish.setOnClickListener {
            val s = (0 until allPlayers.size).filter { lv.isItemChecked(it) }.map { allPlayers[it] }
            extraColumns[cIdx].cells[rIdx] = s.joinToString(" و ")
            saveExtraColumns(); d.dismiss(); refreshTable()
        }
    }

    private fun displayCellText(stored: String): String {
        val names = stored.split(" و ").filter { it.isNotBlank() }
        return when {
            names.size > 2 -> "${toPersianDigits(names.size)} انتخاب"
            else -> stored
        }
    }

    private fun displayRoleName(storedRole: String): String {
        val norm = storedRole.replace('_', ' ').trim()
        if (norm.startsWith("شهروند ساده") || norm.startsWith("مافیا ساده")) return "ساده"
        if (norm.contains("ستاره دار")) return norm.replace("ستاره دار", "ستاره")
        return storedRole
    }

    private fun showColumnDetails(col: ExtraColumn) {
        val details = tableData.indices.mapNotNull { i ->
            val cell = col.cells.getOrElse(i) { "" }
            if (cell.isEmpty() || cell == "-") null
            else "${tableData[i].roleName}(${tableData[i].playerName}): $cell"
        }
        if (details.isEmpty()) { Toast.makeText(this, "اطلاعاتی برای این ستون ثبت نشده", Toast.LENGTH_SHORT).show(); return }
        val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        lo.addView(TextView(this).apply { text = col.name; textSize = 18f; setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(16)); setTextColor(textColor) })
        details.forEach { lo.addView(TextView(this).apply { text = it; textSize = 15f; setPadding(0, dp(4), 0, dp(4)); setTextColor(textColor) }) }
        MaterialAlertDialogBuilder(this).setView(ScrollView(this).apply { addView(lo) }).setPositiveButton("بستن", null).show()
    }

    // ── tool dialogs ─────────────────────────────────────────────────────

    private fun showInquiryDialog() {
        val dead = tableData.filter { it.lives == 0 }
        if (dead.isEmpty()) { Toast.makeText(this, "هیچ بازیکنی کشته نشده", Toast.LENGTH_SHORT).show(); return }
        val mafiaDead = dead.filter { it.side == "مافیا" }; val cityDead = dead.filter { it.side == "شهروند" }; val indepDead = dead.filter { it.side == "مستقل" }
        val sb = StringBuilder()
        sb.appendLine("کشته‌ها: ${toPersianDigits(dead.size)}"); sb.appendLine()
        if (mafiaDead.isNotEmpty()) { sb.appendLine("مافیا: ${toPersianDigits(mafiaDead.size)}"); mafiaDead.forEach { sb.appendLine(it.roleName) }; sb.appendLine() }
        if (cityDead.isNotEmpty()) { sb.appendLine("شهروند: ${toPersianDigits(cityDead.size)}"); cityDead.forEach { sb.appendLine(it.roleName) }; sb.appendLine() }
        if (indepDead.isNotEmpty()) { sb.appendLine("مستقل: ${toPersianDigits(indepDead.size)}"); indepDead.forEach { sb.appendLine(it.roleName) } }
        MaterialAlertDialogBuilder(this).setTitle("استعلام")
            .setMessage(sb.toString().trim()).setPositiveButton("بستن", null).show()
    }

    private fun showTalkDialog() {
        if (tableData.isEmpty()) { Toast.makeText(this, "بازیکنی وجود ندارد", Toast.LENGTH_SHORT).show(); return }
        val r = tableData[Random.nextInt(tableData.size)]
        MaterialAlertDialogBuilder(this).setTitle("سر صحبت")
            .setMessage("سر صحبت: ${r.playerName}\n${if (Random.nextBoolean()) "راست گرد" else "چپ گرد"}")
            .setPositiveButton("باشه", null).show()
    }

    private fun showDeathLotteryDialog() {
        val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(40), dp(20), dp(40), dp(20)) }
        val inp = EditText(this).apply { hint = "تعداد"; inputType = InputType.TYPE_CLASS_NUMBER }
        lo.addView(TextView(this).apply { text = "تعداد؟" }); lo.addView(inp)
        MaterialAlertDialogBuilder(this).setTitle("قرعه مرگ").setView(lo)
            .setPositiveButton("ثبت") { _, _ ->
                val c = inp.text.toString().toIntOrNull() ?: 0
                if (c <= 0) { Toast.makeText(this, "عدد باید بزرگتر از صفر باشد", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                MaterialAlertDialogBuilder(this).setTitle("قرعه مرگ").setMessage("بازیکن شماره ${Random.nextInt(1, c + 1)}")
                    .setPositiveButton("باشه", null).show()
            }.setNegativeButton("انصراف", null).show()
    }

    private fun showCardDialog() {
        if (cardList.isEmpty()) { MaterialAlertDialogBuilder(this).setTitle("کارت").setMessage("کارت دیگری نداریم").setPositiveButton("باشه", null).show(); return }
        MaterialAlertDialogBuilder(this).setTitle("کارت").setMessage(cardList.removeAt(Random.nextInt(cardList.size))).setPositiveButton("باشه", null).show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
