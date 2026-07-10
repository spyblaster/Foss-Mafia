package org.fossify.messages.mafia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityMafiaResultBinding
import org.fossify.messages.mafia.adapters.ResultAdapter
import org.fossify.messages.mafia.adapters.ResultItem

class ResultActivity : BaseGameActivity() {

    private val binding by viewBinding(ActivityMafiaResultBinding::inflate)
    private val resultAdapter = ResultAdapter()

    private lateinit var resultList: ArrayList<RoleActivity.AssignedRole>
    private val cardList = mutableListOf<String>()
    val manageColumns = mutableListOf<ColumnData>()
    private var gameCode: Int = 1

    data class SavedGame(
        val players: List<GameActivity.Player>,
        val results: List<RoleActivity.AssignedRole>,
        val cards: List<String>,
        val columns: List<ColumnData> = emptyList()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        loadCardsFromAssets()
        gameCode = getCounter()

        val fromSaved = intent.getBooleanExtra("from_saved", false)
        if (fromSaved) {
            loadSavedGame()
        } else {
            @Suppress("UNCHECKED_CAST")
            resultList = intent.getSerializableExtra("result") as? ArrayList<RoleActivity.AssignedRole> ?: return
            saveGame()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (fromSaved) getString(R.string.mafia_saved_game_title)
        else getString(R.string.mafia_result_title)

        binding.resultGameCodeChip.text = getString(R.string.mafia_game_code, gameCode.toPersianDigits())

        binding.resultRolesList.adapter = resultAdapter
        refreshResultList()

        binding.resultSendSmsBtn.setOnClickListener { showSendSmsDialog() }
        binding.resultSendSmsOnlyBtn.setOnClickListener { showSelectSmsDialog() }
        binding.resultEndGameBtn.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.mafia_end_game)
                .setMessage(R.string.mafia_end_game_confirm)
                .setPositiveButton(R.string.mafia_yes) { _, _ -> deleteSavedGame(); finish() }
                .setNegativeButton(R.string.mafia_no, null)
                .show()
        }
        binding.resultManageBtn.setOnClickListener {
            val intent = Intent(this, ManageActivity::class.java)
            intent.putExtra("result", mafiaGson.toJson(resultList.toList()))
            intent.putExtra(
                "columns",
                mafiaGson.toJson(manageColumns.map { mapOf("title" to it.title, "values" to it.values) })
            )
            startActivity(intent)
        }
        binding.resultShareBtn.setOnClickListener { shareTableAsImage() }
    }

    override fun onResume() {
        super.onResume()
        if (::resultList.isInitialized) {
            val f = java.io.File(filesDir, "game.json")
            if (f.exists()) {
                try {
                    val savedGame: SavedGame = mafiaGson.fromJson(f.readText(), object : TypeToken<SavedGame>() {}.type)
                    resultList.clear()
                    resultList.addAll(savedGame.results)
                    refreshResultList()
                } catch (e: Exception) {
                    android.util.Log.e("ResultActivity", "Error reloading game", e)
                }
            }
            saveGame()
        }
    }

    private fun refreshResultList() {
        val sides = listOf(
            "مافیا" to ContextCompat.getColor(this, android.R.color.holo_red_dark),
            "شهروند" to ContextCompat.getColor(this, android.R.color.holo_green_dark),
            "مستقل" to ContextCompat.getColor(this, android.R.color.holo_orange_dark)
        )
        val sideLabels = mapOf(
            "مافیا" to getString(R.string.mafia_side_mafia),
            "شهروند" to getString(R.string.mafia_side_city),
            "مستقل" to getString(R.string.mafia_side_independent)
        )
        val items = mutableListOf<ResultItem>()
        for ((side, color) in sides) {
            val group = resultList.filter { it.role.side == side }
            if (group.isNotEmpty()) {
                items += ResultItem.Header(sideLabels[side] ?: side, color)
                group.forEach { items += ResultItem.Row(it, color) }
            }
        }
        resultAdapter.submitItems(items)
    }

    private fun getCounter(): Int =
        getSharedPreferences("mafia_counter", Context.MODE_PRIVATE).getInt("game_counter", 1)

    private fun loadCardsFromAssets() {
        try {
            val json = assets.open("cards.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            val data: Map<String, List<String>> = mafiaGson.fromJson(json, type)
            cardList.clear()
            cardList.addAll(data["CARDS"] ?: emptyList())
        } catch (e: Exception) { /* no cards asset — that's fine */ }
    }

    private fun saveGame() {
        val saved = SavedGame(
            players = resultList.map { GameActivity.Player(it.playerName, it.playerPhone) },
            results = resultList,
            cards = cardList,
            columns = manageColumns.map { ColumnData(it.title, it.values.toMutableList()) }
        )
        java.io.File(filesDir, "game.json").writeText(mafiaGson.toJson(saved))
    }

    private fun loadSavedGame() {
        val file = java.io.File(filesDir, "game.json")
        if (file.exists()) {
            val saved: SavedGame = mafiaGson.fromJson(file.readText(), SavedGame::class.java)
            resultList = ArrayList(saved.results)
            cardList.clear()
            cardList.addAll(saved.cards)
            manageColumns.clear()
            manageColumns.addAll(saved.columns.map { ColumnData(it.title, it.values.toMutableList()) })
        }
    }

    private fun deleteSavedGame() {
        java.io.File(filesDir, "extra_columns.json").delete()
        java.io.File(filesDir, "game.json").delete()
    }

    private fun showSendSmsDialog() {
        val withPhone = resultList.filter { it.playerPhone.isNotBlank() }
        val withoutContact = resultList.filter { it.playerPhone.isBlank() }

        if (withPhone.isEmpty() && withoutContact.isNotEmpty()) {
            toast(R.string.mafia_no_phone_numbers)
            return
        }

        val message = buildString {
            if (withPhone.isNotEmpty()) {
                append("${withPhone.size} پلیر از طریق پیامک\n")
            }
            if (withoutContact.isNotEmpty()) {
                append("\n${withoutContact.size} پلیر بدون اطلاعات تماس:\n")
                withoutContact.forEach { append("- ${it.playerName}\n") }
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mafia_send_roles)
            .setMessage(message.trim())
            .setPositiveButton(R.string.mafia_yes) { _, _ ->
                sendRolesViaSms(withPhone)
            }
            .setNegativeButton(R.string.mafia_no, null)
            .show()
    }

    private fun sendRolesViaSms(smsPlayers: List<RoleActivity.AssignedRole>) {
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setTitle("در حال ارسال نقش‌ها...")
            .setView(com.google.android.material.progressindicator.CircularProgressIndicator(this))
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            var smsSent = 0
            var smsFailed = 0

            @Suppress("DEPRECATION")
            val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                getSystemService(android.telephony.SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            val shuffledPlayers = smsPlayers.shuffled()
            shuffledPlayers.chunked(10).forEachIndexed { batchIndex, batch ->
                if (batchIndex > 0) {
                    delay(1100)
                }

                val results = kotlinx.coroutines.coroutineScope {
                    batch.map { p ->
                        async(Dispatchers.IO) {
                            try {
                                sms.sendTextMessage(p.playerPhone, null, "${gameCode.toPersianDigits()}. ${p.role.name}", null, null)
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }
                    }.map { it.await() }
                }

                results.forEach { success ->
                    if (success) smsSent++ else smsFailed++
                }
            }

            val resultMsg = buildString {
                if (smsSent > 0) append("پیامک: موفق $smsSent")
                if (smsFailed > 0) append("، ناموفق $smsFailed")
            }

            withContext(Dispatchers.Main) {
                loadingDialog.dismiss()
                MaterialAlertDialogBuilder(this@ResultActivity)
                    .setTitle(R.string.mafia_send_result_title)
                    .setMessage(resultMsg)
                    .setPositiveButton(R.string.mafia_ok, null)
                    .show()
            }
        }
    }

    private fun showSelectSmsDialog() {
        val withPhone = resultList.filter { it.playerPhone.isNotBlank() }

        if (withPhone.isEmpty()) {
            toast("هیچ بازیکنی با شماره تلفن ثبت نشده است")
            return
        }

        val checkBoxes = withPhone.map { role ->
            android.widget.CheckBox(this).apply {
                text = role.playerName
            }
        }
        val checkedStates = mutableSetOf<Int>()

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)

            val buttonLayout = android.widget.LinearLayout(this@ResultActivity).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val selectAllBtn = com.google.android.material.button.MaterialButton(this@ResultActivity).apply {
                text = "انتخاب همه"
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    checkBoxes.forEachIndexed { index, _ -> checkedStates.add(index) }
                    checkBoxes.forEach { it.isChecked = true }
                }
            }

            val clearAllBtn = com.google.android.material.button.MaterialButton(this@ResultActivity).apply {
                text = "پاک کردن همه"
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    checkedStates.clear()
                    checkBoxes.forEach { it.isChecked = false }
                }
            }

            buttonLayout.addView(selectAllBtn)
            buttonLayout.addView(clearAllBtn)
            addView(buttonLayout)

            val scrollView = android.widget.ScrollView(this@ResultActivity).apply {
                val screenHeight = resources.displayMetrics.heightPixels
                val maxHeight = (screenHeight * 0.5).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    maxHeight
                )
            }
            val checkBoxLayout = android.widget.LinearLayout(this@ResultActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
            }
            checkBoxes.forEachIndexed { index, checkBox ->
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) checkedStates.add(index) else checkedStates.remove(index)
                }
                checkBoxLayout.addView(checkBox)
            }
            scrollView.addView(checkBoxLayout)
            addView(scrollView)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب بازیکنان برای پیامک")
            .setView(layout)
            .setPositiveButton("ارسال") { _, _ ->
                val selectedPlayers = checkedStates.map { withPhone[it] }
                if (selectedPlayers.isEmpty()) {
                    toast("هیچ بازیکنی انتخاب نشده است")
                } else {
                    sendSmsToSelectedPlayers(selectedPlayers)
                }
            }
            .setNegativeButton(R.string.mafia_cancel, null)
            .show()
    }

    private fun sendSmsToSelectedPlayers(players: List<RoleActivity.AssignedRole>) {
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setTitle("در حال ارسال پیامک‌ها...")
            .setView(com.google.android.material.progressindicator.CircularProgressIndicator(this))
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            var sent = 0
            var failed = 0

            @Suppress("DEPRECATION")
            val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                getSystemService(android.telephony.SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            val shuffledSelected = players.shuffled()
            shuffledSelected.chunked(10).forEachIndexed { batchIndex, batch ->
                if (batchIndex > 0) {
                    delay(1100)
                }

                val results = kotlinx.coroutines.coroutineScope {
                    batch.map { p ->
                        async(Dispatchers.IO) {
                            try {
                                sms.sendTextMessage(p.playerPhone, null, "${gameCode.toPersianDigits()}. ${p.role.name}", null, null)
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }
                    }.map { it.await() }
                }

                results.forEach { success ->
                    if (success) sent++ else failed++
                }
            }

            val msg = if (failed == 0) "پیامک: موفق $sent"
            else "پیامک: موفق $sent، ناموفق $failed"

            withContext(Dispatchers.Main) {
                loadingDialog.dismiss()
                MaterialAlertDialogBuilder(this@ResultActivity)
                    .setTitle(R.string.mafia_send_result_title)
                    .setMessage(msg)
                    .setPositiveButton(R.string.mafia_ok, null)
                    .show()
            }
        }
    }

    private fun shareTableAsImage() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val extraColumnsFile = java.io.File(filesDir, "extra_columns.json")
                val extraColumns = if (extraColumnsFile.exists()) {
                    try {
                        val type = object : TypeToken<List<ManageActivity.ExtraColumn>>() {}.type
                        mafiaGson.fromJson<List<ManageActivity.ExtraColumn>>(extraColumnsFile.readText(), type)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                val livesFromSave = mutableMapOf<String, Int>()
                val savedGameFile2 = java.io.File(filesDir, "game.json")
                if (savedGameFile2.exists()) {
                    try {
                        val sg: SavedGame = mafiaGson.fromJson(savedGameFile2.readText(), SavedGame::class.java)
                        sg.results.forEach { livesFromSave[it.playerName] = it.lives }
                    } catch (e: Exception) { }
                }
                val tableData = resultList.map {
                    ManageActivity.TableRow(it.playerName, it.role.name, it.role.side, livesFromSave[it.playerName] ?: 1)
                }

                val bitmap = createTableBitmap(tableData, extraColumns)

                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                val fileName = "game_table_$dateStr.png"
                val imageFile = java.io.File(this@ResultActivity.cacheDir, fileName)

                imageFile.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this@ResultActivity,
                    "${applicationContext.packageName}.provider",
                    imageFile
                )

                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "اشتراکگذاری جدول بازی"))
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toast("خطا در ایجاد تصویر: ${e.message}")
                }
            }
        }
    }

    private fun createTableBitmap(
        tableData: List<ManageActivity.TableRow>,
        extraColumns: List<ManageActivity.ExtraColumn>
    ): android.graphics.Bitmap {
        val density = resources.displayMetrics.density
        val textSizePx = 14f * density
        val cellPaddingH = 16f * density
        val cellHeight = (52f * density).toInt()
        val headerHeight = (60f * density).toInt()
        val outerPad = (12f * density).toInt()

        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.textSize = textSizePx
            typeface = android.graphics.Typeface.DEFAULT
        }
        val strikePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.textSize = textSizePx
            typeface = android.graphics.Typeface.DEFAULT
            flags = android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        }

        val confirmedCols = extraColumns.filter { it.confirmed }.reversed()

        data class Col(val header: String, val cells: List<String>)
        val cols = mutableListOf<Col>()
        for (ec in confirmedCols) {
            cols.add(Col(ec.name, tableData.indices.map { ec.cells.getOrElse(it) { "" } }))
        }
        cols.add(Col("نام", tableData.map { it.playerName }))
        cols.add(Col("نقش", tableData.map { it.roleName }))

        fun textW(s: String) = textPaint.measureText(s)
        val colWidths = cols.map { col ->
            val maxW = maxOf(textW(col.header), col.cells.maxOfOrNull { textW(it) } ?: 0f)
            (maxW + cellPaddingH * 2).toInt()
        }

        val totalWidth = outerPad * 2 + colWidths.sum()
        val totalHeight = outerPad * 2 + headerHeight + tableData.size * cellHeight

        val bitmap = android.graphics.Bitmap.createBitmap(totalWidth, totalHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val headerBgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#37474F")
            style = android.graphics.Paint.Style.FILL
        }
        val borderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#B0BEC5")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }

        val headerTop = outerPad.toFloat()
        val headerBottom = headerTop + headerHeight
        canvas.drawRect(outerPad.toFloat(), headerTop, (totalWidth - outerPad).toFloat(), headerBottom, headerBgPaint)

        val headerTextPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.textSize = textSizePx
            color = android.graphics.Color.WHITE
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
        }
        var colX = outerPad
        val headerTextY = headerTop + (headerHeight - textPaint.ascent() - textPaint.descent()) / 2
        for ((i, col) in cols.withIndex()) {
            val cx = colX + colWidths[i] / 2f
            canvas.drawText(col.header, cx, headerTextY, headerTextPaint)
            colX += colWidths[i]
        }

        for ((rowIdx, row) in tableData.withIndex()) {
            val rowTop = outerPad + headerHeight + rowIdx * cellHeight
            val rowBottom = rowTop + cellHeight

            val rowBgColor = when (row.side) {
                "مافیا" -> android.graphics.Color.parseColor("#FFCDD2")
                "شهروند" -> android.graphics.Color.parseColor("#BBDEFB")
                "مستقل" -> android.graphics.Color.parseColor("#FFE0B2")
                else -> if (rowIdx % 2 == 0) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#F5F5F5")
            }
            val rowBgPaint = android.graphics.Paint().apply {
                color = rowBgColor
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawRect(outerPad.toFloat(), rowTop.toFloat(), (totalWidth - outerPad).toFloat(), rowBottom.toFloat(), rowBgPaint)

            val isDead = row.lives == 0
            val normalPaint = textPaint.apply {
                color = android.graphics.Color.parseColor("#212121")
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val deadPaint = strikePaint.apply {
                color = android.graphics.Color.parseColor("#9E9E9E")
                textAlign = android.graphics.Paint.Align.CENTER
            }

            val textY = rowTop + (cellHeight - normalPaint.ascent() - normalPaint.descent()) / 2

            val fixedColIndices = setOf(cols.size - 2, cols.size - 1)

            colX = outerPad
            for ((i, col) in cols.withIndex()) {
                val cx = colX + colWidths[i] / 2f
                val cellVal = col.cells.getOrElse(rowIdx) { "" }
                val paint = if (isDead && fixedColIndices.contains(i)) deadPaint else normalPaint
                canvas.drawText(cellVal, cx, textY, paint)

                if (i > 0) canvas.drawLine(colX.toFloat(), headerTop, colX.toFloat(), (outerPad + headerHeight + tableData.size * cellHeight).toFloat(), borderPaint)
                colX += colWidths[i]
            }

            canvas.drawLine(outerPad.toFloat(), rowBottom.toFloat(), (totalWidth - outerPad).toFloat(), rowBottom.toFloat(), borderPaint)
        }

        val outerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#546E7A")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        canvas.drawRect(
            outerPad.toFloat(), outerPad.toFloat(),
            (totalWidth - outerPad).toFloat(),
            (outerPad + headerHeight + tableData.size * cellHeight).toFloat(),
            outerPaint
        )

        return bitmap
    }

    private fun goHome() {
        val i = Intent(this, org.fossify.messages.activities.MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goHome()

    override fun onSupportNavigateUp(): Boolean { goHome(); return true }
}