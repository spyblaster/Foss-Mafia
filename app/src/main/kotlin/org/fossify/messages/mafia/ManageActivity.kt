package org.fossify.messages.mafia

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.random.Random

class ManageActivity : BaseGameActivity() {

    data class TableRow(val playerName: String, val roleName: String, val side: String, val lives: Int = 1)
    data class ExtraColumn(val name: String, val cells: MutableList<String>, val confirmed: Boolean = false)

    private val gson = Gson()
    private lateinit var scrollHeadersRow: LinearLayout
    private lateinit var scrollDataLayout: LinearLayout
    private lateinit var scrollRoot: LinearLayout
    private lateinit var horizontalScroll: HorizontalScrollView
    private lateinit var editSwitch: Switch
    private lateinit var editButtonsLayout: LinearLayout
    private lateinit var fixedBodyLayout: LinearLayout
    private lateinit var bodyHorizontalScroll: HorizontalScrollView
    private lateinit var timerLayout: FrameLayout
    private lateinit var timerDisplay: Button
    private lateinit var toolsLayout: LinearLayout
    private lateinit var fixedHeaderContainer: LinearLayout
    private var tableData = mutableListOf<TableRow>()
    private var extraColumns = mutableListOf<ExtraColumn>()
    private var isEditMode = false
    private var lastScrollX = 0
    private val cellWidthDp = 90; private val rowHeightDp = 52
    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private val dayRoles = listOf("جلب", "قاضی", "شهردار", "جادوگر", "شاه_کش")
    private val neverActRoles = listOf("شهروند_ساده1", "شهروند_ساده2", "شهروند_ساده3", "شهروند_ساده4", "رویین_تن", "مافیای_ساده", "یاغی", "بمب_ساز", "فدایی", "کابوس", "معشوقه")
    private val onceOnlyRoles = listOf("ناتو", "اسنایپر", "بازپرس", "سرباز", "مین_گذار", "تفنگ_ساز", "دستکج")
    private val counterRoles = listOf("گورکن", "گورکن_ستاره_دار")
    private val selfOnceRoles = listOf("جراح", "دکتر", "دکتر_ستاره_دار")

    private var timerHandler = android.os.Handler()
    private var timerRunnable: Runnable? = null
    private var timerRunning = false
    private var totalSeconds = 0
    private var remainingSeconds = 0
    private val cardList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadTableData(); loadExtraColumns(); loadCardsFromAssets()

        val rootLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 8, 8, 8) }

        val switchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 8, 0, 16) }
        editSwitch = Switch(this).apply {
            textOff = "خواب"; textOn = "بیدار"; isChecked = false; setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, isChecked -> isEditMode = isChecked; refreshEditButtons(); refreshTable(); refreshTimerVisibility(); refreshToolsVisibility() }
        }
        switchRow.addView(TextView(this).apply { text = "بیدار"; textSize = 14f; setTextColor(Color.WHITE); setPadding(0, 0, 8, 0) })
        switchRow.addView(editSwitch)
        switchRow.addView(TextView(this).apply { text = "خواب"; textSize = 14f; setTextColor(Color.WHITE); setPadding(8, 0, 0, 0) })

        fixedHeaderContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setBackgroundColor(Color.parseColor("#E0E0E0")) }
        fixedHeaderContainer.addView(headerCell("جان"), LinearLayout.LayoutParams(dp(50), dp(rowHeightDp)))
        fixedHeaderContainer.addView(headerCell("نقش"), LinearLayout.LayoutParams(dp(80), dp(rowHeightDp)))
        fixedHeaderContainer.addView(headerCell("نام"), LinearLayout.LayoutParams(dp(90), dp(rowHeightDp)))

        horizontalScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        scrollHeadersRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        horizontalScroll.addView(scrollHeadersRow)
        fixedHeaderContainer.addView(horizontalScroll, LinearLayout.LayoutParams(0, dp(rowHeightDp), 1f))

        val bodyVerticalScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val bodyContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        fixedBodyLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; bodyContainer.addView(fixedBodyLayout)

        bodyHorizontalScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        scrollRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollDataLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; scrollRoot.addView(scrollDataLayout)
        bodyHorizontalScroll.addView(scrollRoot)
        bodyContainer.addView(bodyHorizontalScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bodyVerticalScroll.addView(bodyContainer)

        horizontalScroll.setOnScrollChangeListener { _, _, _, _, _ -> bodyHorizontalScroll.scrollX = horizontalScroll.scrollX }
        bodyHorizontalScroll.setOnScrollChangeListener { _, _, _, _, _ -> horizontalScroll.scrollX = bodyHorizontalScroll.scrollX }

        val hasBazporsOrNamayandeh = tableData.any { it.roleName == "رویین_تن" || it.roleName == "یاغی" }

        toolsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 0); visibility = if (isEditMode) View.GONE else View.VISIBLE }
        if (!hasBazporsOrNamayandeh) toolsLayout.addView(createSmallButton("کارت") { showCardDialog() })
        val inquiryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6, 0, 0) }
        inquiryRow.addView(createSmallButton("استعلام") { showInquiryDialog() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toolsLayout.addView(inquiryRow)
        val toolsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6, 0, 0) }
        toolsRow.addView(createSmallButton("سر صحبت") { showTalkDialog() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 6, 0) })
        if (!hasBazporsOrNamayandeh) toolsRow.addView(createSmallButton("قرعه مرگ") { showDeathLotteryDialog() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 0, 0) })
        toolsLayout.addView(toolsRow)

        // تایمر دایره‌ای در مرکز صفحه با GestureDetector
        val timerCircleSize = dp(180)
        timerDisplay = Button(this).apply {
            text = "۰۰:۰۰"; setTextColor(Color.WHITE); textSize = 30f
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#0097A7")) }
            setPadding(0, 0, 0, 0)
            setOnLongClickListener {
                showTimerInputDialog()
                true
            }
        }

        val gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                stopTimer()
                remainingSeconds = 0
                startTimer()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!timerRunning) startTimer() else stopTimer()
                return true
            }
        })

        timerDisplay.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        timerLayout = FrameLayout(this).apply {
            visibility = if (isEditMode) View.GONE else View.VISIBLE
        }
        timerLayout.addView(timerDisplay, FrameLayout.LayoutParams(timerCircleSize, timerCircleSize, Gravity.CENTER))

        editButtonsLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0); visibility = if (isEditMode) View.VISIBLE else View.GONE }
        editButtonsLayout.addView(createSmallButton("شب") { addColumn("شب") }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 6, 0) })
        if (!hasBazporsOrNamayandeh) editButtonsLayout.addView(createSmallButton("نیمروز") { addColumn("نیمروز") }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 0, 0) })

        rootLayout.addView(switchRow)
        rootLayout.addView(fixedHeaderContainer)
        rootLayout.addView(bodyVerticalScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        rootLayout.addView(timerLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        rootLayout.addView(toolsLayout)
        rootLayout.addView(editButtonsLayout)

        setContentView(rootLayout)
        supportActionBar?.setDisplayHomeAsUpEnabled(true); supportActionBar?.title = "جدول بازی"
        refreshTable()
    }

    private fun refreshTimerVisibility() { timerLayout.visibility = if (!isEditMode) View.VISIBLE else View.GONE }
    private fun refreshToolsVisibility() { toolsLayout.visibility = if (!isEditMode) View.VISIBLE else View.GONE }

    private fun showTimerInputDialog() {
        val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 20) }
        val inp = EditText(this).apply { hint = "ثانیه"; inputType = InputType.TYPE_CLASS_NUMBER; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        lo.addView(TextView(this).apply { text = "زمان را وارد کنید (ثانیه):"; setTextColor(Color.WHITE); setPadding(0, 0, 0, 16) })
        lo.addView(inp)
        AlertDialog.Builder(this).setTitle("تنظیم زمان").setView(lo)
            .setPositiveButton("تایید") { _, _ ->
                val sec = inp.text.toString().toIntOrNull() ?: 0
                if (sec > 0) {
                    stopTimer()
                    totalSeconds = sec
                    remainingSeconds = 0
                    timerDisplay.text = "${toPersianDigits(sec / 60)}:${toPersianDigits(sec % 60)}"
                    timerDisplay.setTextColor(Color.WHITE)
                }
            }.setNegativeButton("انصراف", null).show()
    }

    private fun loadCardsFromAssets() { try { val i = assets.open("cards.json"); val j = i.bufferedReader().readText(); i.close(); val t = object : TypeToken<Map<String, List<String>>>() {}.type; val d: Map<String, List<String>> = gson.fromJson(j, t); cardList.clear(); cardList.addAll(d["CARDS"] ?: emptyList()) } catch (e: Exception) { } }

    private fun toPersianDigits(n: Int): String { val p = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"); return n.toString().padStart(2, '0').map { if (it.isDigit()) p[it - '0'] else it.toString() }.joinToString("") }

    private fun startTimer() {
        if (totalSeconds <= 0) {
            showTimerInputDialog()
            return
        }
        if (remainingSeconds <= 0) remainingSeconds = totalSeconds
        timerRunning = true
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = object : Runnable {
            override fun run() {
                remainingSeconds--
                val abs = kotlin.math.abs(remainingSeconds); val mins = abs / 60; val secs = abs % 60
                val prefix = if (remainingSeconds < 0) "-" else ""
                timerDisplay.text = "$prefix${toPersianDigits(mins)}:${toPersianDigits(secs)}"
                if (remainingSeconds <= 0) timerDisplay.setTextColor(Color.RED) else timerDisplay.setTextColor(Color.WHITE)
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.postDelayed(timerRunnable!!, 1000)
    }
    private fun stopTimer() { timerRunnable?.let { timerHandler.removeCallbacks(it) }; timerRunning = false }

    private fun createSmallButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = 12f
        background = GradientDrawable().apply { setColor(Color.parseColor("#0097A7")); cornerRadius = 24f }
        setPadding(4, 12, 4, 12); setOnClickListener { onClick() }
    }

    private fun refreshEditButtons() { editButtonsLayout.visibility = if (isEditMode) View.VISIBLE else View.GONE }
    private fun getNextNightNumber() = (extraColumns.filter { it.name.startsWith("شب") }.mapNotNull { it.name.removePrefix("شب").toIntOrNull() }.maxOrNull() ?: 0) + 1

    private fun addColumn(prefix: String) {
        if (prefix == "نیمروز" && extraColumns.isNotEmpty() && extraColumns.last().name.startsWith("نیمروز")) { Toast.makeText(this, "نیمروز قبلاً ثبت شده", Toast.LENGTH_SHORT).show(); return }
        if (extraColumns.any { !it.confirmed }) { Toast.makeText(this, "ابتدا ستون فعلی را تایید کنید", Toast.LENGTH_SHORT).show(); return }
        extraColumns.add(ExtraColumn(if (prefix == "شب") "شب${getNextNightNumber()}" else "نیمروز", MutableList(tableData.size) { "" }, false))
        saveExtraColumns(); refreshTable()
        bodyHorizontalScroll.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() { bodyHorizontalScroll.viewTreeObserver.removeOnGlobalLayoutListener(this); bodyHorizontalScroll.fullScroll(View.FOCUS_LEFT); horizontalScroll.fullScroll(View.FOCUS_LEFT) }
        })
    }

    private fun loadTableData() { val f = java.io.File(filesDir, "game.json"); if (f.exists()) try { val s: ResultActivity.SavedGame = gson.fromJson(f.readText(), object : TypeToken<ResultActivity.SavedGame>() {}.type); tableData.clear(); s.results.forEach { tableData.add(TableRow(it.playerName, it.role.name, it.role.side)) } } catch (e: Exception) { } }
    private fun loadExtraColumns() { val f = java.io.File(filesDir, "extra_columns.json"); if (f.exists()) try { extraColumns.clear(); extraColumns.addAll(gson.fromJson(f.readText(), object : TypeToken<List<ExtraColumn>>() {}.type)) } catch (e: Exception) { } }
    private fun saveExtraColumns() { java.io.File(filesDir, "extra_columns.json").writeText(gson.toJson(extraColumns)) }

    private fun hasJalebWorkedBefore(cIdx: Int) = extraColumns.take(cIdx).any { it.cells.any { it == "✅" } }
    private fun hasWizardWorked(cIdx: Int, isDay: Boolean, rIdx: Int) = extraColumns.take(cIdx).any { (it.name.startsWith("نیمروز") == isDay) && it.cells.getOrElse(rIdx) { "" }.isNotEmpty() && it.cells[rIdx] != "-" }
    private fun hasOnceOnlyWorked(cIdx: Int, rIdx: Int) = extraColumns.take(cIdx).any { it.cells.getOrElse(rIdx) { "" }.isNotEmpty() && it.cells[rIdx] != "-" }
    private fun canRammalAct(cIdx: Int, rIdx: Int, nightNum: Int): Boolean {
        val nights = extraColumns.take(cIdx).filter { it.name.startsWith("شب") }
        val worked = nights.any { it.cells.getOrElse(rIdx) { "" }.isNotEmpty() && it.cells[rIdx] != "-" }
        if (!worked) return true
        val first = nights.indexOfFirst { it.cells.getOrElse(rIdx) { "" }.isNotEmpty() && it.cells[rIdx] != "-" }
        if (first == -1) return false
        if (getNightNumber(nights[first].name) % 2 != 0) return false
        val cnt = nights.count { getNightNumber(it.name) % 2 == 0 && it.cells.getOrElse(rIdx) { "" }.isNotEmpty() && it.cells[rIdx] != "-" }
        return cnt < 2 && nightNum % 2 == 0
    }
    private fun getNightNumber(colName: String): Int = colName.removePrefix("شب").toIntOrNull() ?: 0
    private fun findMaxNumber(colIdx: Int, rowIdx: Int): Int { var max = 0; for (c in 0 until colIdx) { val n = extraColumns.getOrNull(c)?.cells?.getOrElse(rowIdx) { "" }?.toIntOrNull() ?: continue; if (n > max) max = n }; return max }
    private fun shahKeshCount(colIdx: Int, rowIdx: Int): Int { var c = 0; for (i in 0 until colIdx) { val cell = extraColumns.getOrNull(i)?.cells?.getOrElse(rowIdx) { "" } ?: ""; if (cell.isNotEmpty() && cell != "-") c++ }; return c + 1 }
    private fun canDeleteColumn(colIdx: Int) = colIdx == extraColumns.size - 1

    private fun refreshTable() {
        if (!isEditMode) { scrollHeadersRow.removeAllViews(); scrollDataLayout.removeAllViews(); fixedBodyLayout.removeAllViews(); fixedHeaderContainer.visibility = View.GONE; bodyHorizontalScroll.visibility = View.GONE; horizontalScroll.visibility = View.GONE; return }
        fixedHeaderContainer.visibility = View.VISIBLE; bodyHorizontalScroll.visibility = View.VISIBLE; horizontalScroll.visibility = View.VISIBLE
        lastScrollX = bodyHorizontalScroll.scrollX
        scrollHeadersRow.removeAllViews(); scrollDataLayout.removeAllViews(); fixedBodyLayout.removeAllViews()
        val rh = dp(rowHeightDp)

        extraColumns.forEachIndexed { idx, col ->
            scrollHeadersRow.addView(TextView(this).apply {
                text = if (col.confirmed) col.name else "✅ ${col.name}"
                textSize = 13f; setTextColor(Color.BLACK); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(4, 4, 4, 4)
                if (!col.confirmed) setOnClickListener { extraColumns[idx] = col.copy(cells = col.cells.map { it.ifEmpty { "-" } }.toMutableList(), confirmed = true); saveExtraColumns(); refreshTable() }
                else setOnClickListener { showColumnDetails(col) }
                setOnLongClickListener {
                    if (col.confirmed && canDeleteColumn(idx)) AlertDialog.Builder(this@ManageActivity).setTitle("حذف ستون").setMessage("ستون ${col.name} حذف شود؟").setPositiveButton("بله") { _, _ -> extraColumns.removeAt(idx); saveExtraColumns(); refreshTable() }.setNegativeButton("خیر", null).show()
                    else if (col.confirmed && !canDeleteColumn(idx)) Toast.makeText(this@ManageActivity, "فقط آخرین ستون قابل حذف است", Toast.LENGTH_SHORT).show()
                    true
                }
            }, LinearLayout.LayoutParams(dp(cellWidthDp), rh))
        }

        for ((rowIdx, row) in tableData.withIndex()) {
            val isDead = row.lives == 0; val isRaees = row.roleName == "رییس"
            val bg = when (row.side) { "مافیا" -> Color.parseColor("#FFCDD2"); "شهروند" -> Color.parseColor("#BBDEFB"); "مستقل" -> Color.parseColor("#FFE0B2"); else -> Color.TRANSPARENT }

            val fixedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setBackgroundColor(bg) }
            fixedRow.addView(TextView(this).apply { text = if (isDead) "🖤" else "❤️"; textSize = 18f; gravity = Gravity.CENTER; if (isEditMode) setOnClickListener { tableData[rowIdx] = tableData[rowIdx].copy(lives = if (isDead) 1 else 0); refreshTable() } }, LinearLayout.LayoutParams(dp(50), rh))
            fixedRow.addView(dataCell(row.roleName, isDead), LinearLayout.LayoutParams(dp(80), rh))
            fixedRow.addView(dataCell(row.playerName, isDead), LinearLayout.LayoutParams(dp(90), rh))
            fixedBodyLayout.addView(fixedRow)

            val scrollRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setBackgroundColor(bg) }
            extraColumns.forEachIndexed { colIdx, col ->
                val isDayColumn = col.name.startsWith("نیمروز"); val nightNum = getNightNumber(col.name)
                val cellBlack = when {
                    neverActRoles.contains(row.roleName) -> true
                    row.roleName == "جادوگر" -> isDead || hasWizardWorked(colIdx, isDayColumn, rowIdx)
                    row.roleName == "جلب" -> isDead || hasJalebWorkedBefore(colIdx)
                    row.roleName == "قهرمان" -> isDead || isDayColumn || (nightNum % 2 != 0)
                    row.roleName == "رمال" -> isDead || isDayColumn || !canRammalAct(colIdx, rowIdx, nightNum)
                    onceOnlyRoles.contains(row.roleName) -> isDead || hasOnceOnlyWorked(colIdx, rowIdx)
                    isDayColumn -> isDead || !dayRoles.contains(row.roleName)
                    row.roleName in listOf("قاضی", "شهردار") -> true
                    else -> isDead && !isRaees
                }
                val cellBg = when { col.confirmed && row.side == "مافیا" -> Color.parseColor("#FFE3E5"); col.confirmed && row.side == "شهروند" -> Color.parseColor("#D2E6FA"); col.confirmed && row.side == "مستقل" -> Color.parseColor("#FFF3E0"); col.confirmed -> Color.TRANSPARENT; cellBlack -> Color.BLACK; else -> Color.parseColor("#A5D6A7") }
                val txt = if (col.confirmed && col.cells.getOrElse(rowIdx) { "" }.isEmpty()) "-" else col.cells.getOrElse(rowIdx) { "" }
                scrollRow.addView(TextView(this).apply { text = txt; textSize = 14f; setTextColor(if (col.confirmed || !cellBlack) Color.BLACK else Color.WHITE); gravity = Gravity.CENTER; setPadding(4, 4, 4, 4); setBackgroundColor(cellBg); if (!col.confirmed && !cellBlack) setOnClickListener { handleCellClick(colIdx, rowIdx, row, isDayColumn) } }, LinearLayout.LayoutParams(dp(cellWidthDp), rh))
            }
            scrollDataLayout.addView(scrollRow)
        }
        bodyHorizontalScroll.post { bodyHorizontalScroll.scrollX = lastScrollX; horizontalScroll.scrollX = lastScrollX }
    }

    private fun handleCellClick(cIdx: Int, rIdx: Int, row: TableRow, isDay: Boolean) {
        when {
            row.roleName == "جلب" -> showYesNoDialog(cIdx, rIdx, "جلب") { extraColumns[cIdx].cells[rIdx] = "✅" }
            row.roleName in listOf("قاضی", "شهردار") -> showYesNoDialog(cIdx, rIdx, row.roleName) { extraColumns[cIdx].cells[rIdx] = (findMaxNumber(cIdx, rIdx) + 1).toString() }
            row.roleName in counterRoles -> showYesNoDialog(cIdx, rIdx, row.roleName) { extraColumns[cIdx].cells[rIdx] = (findMaxNumber(cIdx, rIdx) + 1).toString() }
            row.roleName == "شاه_کش" -> showShahKeshPicker(cIdx, rIdx, row)
            row.roleName == "بازپرس" -> showBazporsPicker(cIdx, rIdx, row)
            row.roleName == "رمال" -> showRammalPicker(cIdx, rIdx, row)
            row.roleName == "شب_خسب" -> { val prev = extraColumns.take(cIdx).findLast { it.name.startsWith("شب") }?.cells?.getOrElse(rIdx) { "" } ?: ""; showFilteredPlayerPicker(cIdx, rIdx, row) { p -> p.side != "مافیا" && p.playerName != prev } }
            row.roleName in selfOnceRoles -> { val sn = row.playerName; val hp = extraColumns.any { col -> col.cells.getOrElse(rIdx) { "" } == sn }; showFilteredPlayerPicker(cIdx, rIdx, row) { p -> !(p.playerName == sn && hp) } }
            else -> showPlayerPicker(cIdx, rIdx, row)
        }
    }

    private fun showYesNoDialog(cIdx: Int, rIdx: Int, roleName: String, onYes: () -> Unit) { AlertDialog.Builder(this).setTitle(roleName).setMessage("$roleName کار میکند؟").setPositiveButton("بله") { _, _ -> onYes(); saveExtraColumns(); refreshTable() }.setNegativeButton("خیر") { _, _ -> extraColumns[cIdx].cells[rIdx] = ""; saveExtraColumns(); refreshTable() }.show() }
    private fun showShahKeshPicker(cIdx: Int, rIdx: Int, cr: TableRow) { val a = tableData.filter { it.lives > 0 }.map { it.playerName }; val ct = shahKeshCount(cIdx, rIdx); AlertDialog.Builder(this).setTitle("${cr.roleName}: ${cr.playerName}").setItems((a + listOf("پاکسازی")).toTypedArray()) { _, w -> if (w < a.size) extraColumns[cIdx].cells[rIdx] = "$ct.${a[w]}" else extraColumns[cIdx].cells[rIdx] = ""; saveExtraColumns(); refreshTable() }.setNegativeButton("بستن", null).show() }
    private fun showRammalPicker(cIdx: Int, rIdx: Int, cr: TableRow) { val a = tableData.filter { it.lives > 0 }.map { it.playerName }; val items = a + listOf("گورکنی", "پاکسازی"); AlertDialog.Builder(this).setTitle("${cr.roleName}: ${cr.playerName}").setItems(items.toTypedArray()) { _, w -> when { w < a.size -> extraColumns[cIdx].cells[rIdx] = a[w]; w == a.size -> extraColumns[cIdx].cells[rIdx] = "گورکنی"; else -> extraColumns[cIdx].cells[rIdx] = "" }; saveExtraColumns(); refreshTable() }.setNegativeButton("بستن", null).show() }
    private fun showBazporsPicker(cIdx: Int, rIdx: Int, cr: TableRow) { val a = tableData.filter { it.lives > 0 }.map { it.playerName }; val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }; lo.addView(TextView(this).apply { text = "${cr.roleName}: ${cr.playerName}"; textSize = 16f; setTextColor(Color.BLACK); setPadding(0, 0, 0, 16) }); val lv = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE; adapter = ArrayAdapter(this@ManageActivity, android.R.layout.simple_list_item_multiple_choice, a) }; lo.addView(lv); val br = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }; val clr = Button(this).apply { text = "پاکسازی" }; val cls = Button(this).apply { text = "بستن" }; br.addView(clr, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); br.addView(cls, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); lo.addView(br); val d = AlertDialog.Builder(this).setView(lo).create(); d.show(); cls.setOnClickListener { d.dismiss() }; clr.setOnClickListener { extraColumns[cIdx].cells[rIdx] = ""; saveExtraColumns(); d.dismiss(); refreshTable() }; lv.setOnItemClickListener { _, _, p, _ -> val s = (0 until a.size).filter { lv.isItemChecked(it) }.map { a[it] }; if (s.size <= 2) { extraColumns[cIdx].cells[rIdx] = s.joinToString(" و "); saveExtraColumns(); if (s.size == 2) { d.dismiss(); refreshTable() } } } }
    private fun showFilteredPlayerPicker(cIdx: Int, rIdx: Int, cr: TableRow, fl: (TableRow) -> Boolean) { val a = tableData.filter { it.lives > 0 && fl(it) }.map { it.playerName }; AlertDialog.Builder(this).setTitle("${cr.roleName}: ${cr.playerName}").setItems((a + listOf("پاکسازی")).toTypedArray()) { _, w -> extraColumns[cIdx].cells[rIdx] = if (w < a.size) a[w] else ""; saveExtraColumns(); refreshTable() }.setNegativeButton("بستن", null).show() }

    private fun showColumnDetails(col: ExtraColumn) {
        val details = mutableListOf<String>()
        for (i in tableData.indices) { val cell = col.cells.getOrElse(i) { "" }; if (cell.isNotEmpty() && cell != "-") details.add("${tableData[i].roleName}(${tableData[i].playerName}): $cell") }
        if (details.isEmpty()) { Toast.makeText(this, "اطلاعاتی برای این ستون ثبت نشده", Toast.LENGTH_SHORT).show(); return }
        val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        lo.addView(TextView(this).apply { text = col.name; textSize = 18f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, 16) })
        details.forEach { lo.addView(TextView(this).apply { text = it; textSize = 15f; setTextColor(Color.WHITE); setPadding(0, 4, 0, 4) }) }
        AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(lo) }).setPositiveButton("بستن", null).show()
    }

    private fun headerCell(text: String) = TextView(this).apply { this.text = text; textSize = 15f; setTextColor(Color.BLACK); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(4, 4, 4, 4) }
    private fun dataCell(text: String, strike: Boolean) = TextView(this).apply { this.text = text; textSize = 15f; setTextColor(Color.BLACK); gravity = Gravity.CENTER; setPadding(4, 4, 4, 4); if (strike) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG }
    private fun showPlayerPicker(cIdx: Int, rIdx: Int, cr: TableRow) { val a = tableData.filter { it.lives > 0 }.map { it.playerName }; AlertDialog.Builder(this).setTitle("${cr.roleName}: ${cr.playerName}").setItems((a + listOf("پاکسازی")).toTypedArray()) { _, w -> extraColumns[cIdx].cells[rIdx] = if (w < a.size) a[w] else ""; saveExtraColumns(); refreshTable() }.setNegativeButton("بستن", null).show() }

    private fun showInquiryDialog() {
        val dead = tableData.filter { it.lives == 0 }
        if (dead.isEmpty()) { Toast.makeText(this, "هیچ بازیکنی کشته نشده", Toast.LENGTH_SHORT).show(); return }
        val mafiaDead = dead.filter { it.side == "مافیا" }; val cityDead = dead.filter { it.side == "شهروند" }; val indepDead = dead.filter { it.side == "مستقل" }
        val sb = StringBuilder()
        sb.appendLine("کشته‌ها: ${toPersianDigits(dead.size)}"); sb.appendLine()
        if (mafiaDead.isNotEmpty()) { sb.appendLine("مافیا: ${toPersianDigits(mafiaDead.size)}"); mafiaDead.forEach { sb.appendLine(it.roleName) }; sb.appendLine() }
        if (cityDead.isNotEmpty()) { sb.appendLine("شهروند: ${toPersianDigits(cityDead.size)}"); cityDead.forEach { sb.appendLine(it.roleName) }; sb.appendLine() }
        if (indepDead.isNotEmpty()) { sb.appendLine("مستقل: ${toPersianDigits(indepDead.size)}"); indepDead.forEach { sb.appendLine(it.roleName) } }
        val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        lo.addView(TextView(this).apply { text = sb.toString().trim(); textSize = 16f; setTextColor(Color.WHITE); setPadding(0, 4, 0, 4) })
        AlertDialog.Builder(this).setTitle("استعلام").setView(ScrollView(this).apply { addView(lo) }).setPositiveButton("بستن", null).show()
    }

    private fun showTalkDialog() {
        if (tableData.isEmpty()) { Toast.makeText(this, "بازیکنی وجود ندارد", Toast.LENGTH_SHORT).show(); return }
        val r = tableData[Random.nextInt(tableData.size)]
        AlertDialog.Builder(this).setTitle("سر صحبت").setMessage("سر صحبت: ${r.playerName}\n${if (Random.nextBoolean()) "راست گرد" else "چپ گرد"}").setPositiveButton("باشه", null).show()
    }

    private fun showDeathLotteryDialog() {
        val lo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 20) }
        val inp = EditText(this).apply { hint = "تعداد"; inputType = InputType.TYPE_CLASS_NUMBER }
        lo.addView(TextView(this).apply { text = "تعداد؟" }); lo.addView(inp)
        AlertDialog.Builder(this).setTitle("قرعه مرگ").setView(lo).setPositiveButton("ثبت") { _, _ ->
            val c = inp.text.toString().toIntOrNull() ?: 0
            if (c <= 0) { Toast.makeText(this, "عدد باید بزرگتر از صفر باشد", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
            AlertDialog.Builder(this).setTitle("قرعه مرگ").setMessage("بازیکن شماره ${Random.nextInt(1, c + 1)}").setPositiveButton("باشه", null).show()
        }.setNegativeButton("انصراف", null).show()
    }

    private fun showCardDialog() {
        if (cardList.isEmpty()) { AlertDialog.Builder(this).setTitle("کارت").setMessage("کارت دیگری نداریم").setPositiveButton("باشه", null).show(); return }
        AlertDialog.Builder(this).setTitle("کارت").setMessage(cardList.removeAt(Random.nextInt(cardList.size))).setPositiveButton("باشه", null).show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}


