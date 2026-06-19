package org.fossify.messages.mafia

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.telephony.SmsManager
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ResultActivity : BaseGameActivity() {

    private val gson = Gson()
    private lateinit var resultList: ArrayList<RoleActivity.AssignedRole>
    private val cardList = mutableListOf<String>()
    private var savedGame: SavedGame? = null
    private var gameCode: Int = 1
    val manageColumns = mutableListOf<ColumnData>()

    data class SavedGame(
        val players: List<GameActivity.Player>,
        val results: List<RoleActivity.AssignedRole>,
        val cards: List<String>,
        val columns: List<ColumnData> = emptyList()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadCardsFromAssets()
        gameCode = getCounter()

        val fromSaved = intent.getBooleanExtra("from_saved", false)

        if (fromSaved) {
            loadSavedGame()
        } else {
            resultList = intent.getSerializableExtra("result") as? ArrayList<RoleActivity.AssignedRole> ?: return
            saveGame()
        }

        val rootLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }

        val codeText = TextView(this).apply {
            text = "کد بازی: ${toPersianNumber(gameCode)}"
            textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#0097A7")); setPadding(0, 8, 0, 16)
        }

        val mafiaGroup = resultList.filter { it.role.side == "مافیا" }
        val cityGroup = resultList.filter { it.role.side == "شهروند" }
        val independentGroup = resultList.filter { it.role.side == "مستقل" }

        val contentLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun addSection(title: String, color: Int, group: List<RoleActivity.AssignedRole>) {
            if (group.isNotEmpty()) {
                contentLayout.addView(TextView(this@ResultActivity).apply { text = title; textSize = 20f; setTextColor(resources.getColor(color)); setPadding(0, 16, 0, 8) })
                contentLayout.addView(View(this@ResultActivity).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setBackgroundColor(resources.getColor(color)) } })
                for (assigned in group) contentLayout.addView(TextView(this@ResultActivity).apply { text = "${assigned.role.name}: ${assigned.playerName}"; textSize = 16f; setPadding(0, 4, 0, 4); gravity = Gravity.START })
            }
        }

        addSection("مافیا", android.R.color.holo_red_dark, mafiaGroup)
        addSection("شهروند", android.R.color.holo_green_dark, cityGroup)
        addSection("مستقل", android.R.color.holo_orange_dark, independentGroup)

        val scrollView = ScrollView(this).apply { addView(LinearLayout(this@ResultActivity).apply { orientation = LinearLayout.VERTICAL; addView(codeText); addView(contentLayout) }) }

        val buttonLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }

        val sendSmsBtn = createStyledButton("فرستادن نقش‌ها")
        sendSmsBtn.setOnClickListener { showSendSmsDialog() }

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        val endGameBtn = createStyledButton("پایان بازی")
        val manageBtn = createStyledButton("جدول")
        row3.addView(endGameBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 6, 0) })
        row3.addView(manageBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 0, 0) })

        endGameBtn.setOnClickListener {
            AlertDialog.Builder(this@ResultActivity).setTitle("پایان بازی").setMessage("بازی پایان یابد و اطلاعات حذف شود؟")
                .setPositiveButton("بله") { _, _ -> deleteSavedGame(); finish() }.setNegativeButton("خیر", null).show()
        }

        manageBtn.setOnClickListener {
            val intent = Intent(this@ResultActivity, ManageActivity::class.java)
            intent.putExtra("result", gson.toJson(resultList.toList()))
            intent.putExtra("columns", gson.toJson(manageColumns.map { col -> mapOf("title" to col.title, "values" to col.values) }))
            startActivity(intent)
        }

        buttonLayout.addView(sendSmsBtn)
        buttonLayout.addView(row3)

        rootLayout.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        rootLayout.addView(buttonLayout)

        setContentView(rootLayout)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (fromSaved) "بازی ذخیره شده" else "نتیجه نقش‌دهی"
    }

    override fun onResume() { super.onResume(); if (::resultList.isInitialized) saveGame() }

    private fun toPersianNumber(num: Int): String { val p = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"); return num.toString().map { if (it.isDigit()) p[it - '0'] else it.toString() }.joinToString("") }
    private fun getCounter(): Int { val prefs = getSharedPreferences("mafia_counter", Context.MODE_PRIVATE); return prefs.getInt("game_counter", 1) }

    private fun createStyledButton(text: String): Button = Button(this).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = 12f
        background = GradientDrawable().apply { setColor(Color.parseColor("#0097A7")); cornerRadius = 24f }
        setPadding(4, 12, 4, 12); gravity = Gravity.CENTER
    }

    private fun loadCardsFromAssets() {
        try { val inputStream = assets.open("cards.json"); val json = inputStream.bufferedReader().readText(); inputStream.close(); val type = object : TypeToken<Map<String, List<String>>>() {}.type; val data: Map<String, List<String>> = gson.fromJson(json, type); cardList.clear(); cardList.addAll(data["CARDS"] ?: emptyList()) } catch (e: Exception) { }
    }

    private fun saveGame() {
        val savedGame = SavedGame(players = resultList.map { GameActivity.Player(it.playerName, it.playerPhone) }, results = resultList, cards = cardList, columns = manageColumns.map { ColumnData(it.title, it.values.toMutableList()) })
        java.io.File(filesDir, "game.json").writeText(gson.toJson(savedGame))
    }

    private fun loadSavedGame() {
        val file = java.io.File(filesDir, "game.json")
        if (file.exists()) { val savedGame: SavedGame = gson.fromJson(file.readText(), object : TypeToken<SavedGame>() {}.type); resultList = ArrayList(savedGame.results); cardList.clear(); cardList.addAll(savedGame.cards); manageColumns.clear(); manageColumns.addAll(savedGame.columns.map { ColumnData(it.title, it.values.toMutableList()) }) }
    }

    private fun deleteSavedGame() { java.io.File(filesDir, "extra_columns.json").delete(); java.io.File(filesDir, "game.json").delete() }

    private fun showSendSmsDialog() {
        val withPhone = resultList.filter { it.playerPhone.isNotBlank() }; val withoutPhone = resultList.filter { it.playerPhone.isBlank() }
        if (withPhone.isEmpty()) { Toast.makeText(this, "هیچ بازیکنی شماره تلفن ندارد", Toast.LENGTH_SHORT).show(); return }
        var msg = "نقش‌ها برای ${withPhone.size} بازیکن فرستاده شود؟"
        if (withoutPhone.isNotEmpty()) msg += "\n\n${withoutPhone.size} بازیکن شماره تلفن ندارند:\n" + withoutPhone.joinToString("\n") { "- ${it.playerName}" }
        AlertDialog.Builder(this).setTitle("فرستادن نقش‌ها").setMessage(msg).setPositiveButton("بله") { _, _ -> sendSmsToPlayers(withPhone) }.setNegativeButton("خیر", null).show()
    }

    private fun sendSmsToPlayers(players: List<RoleActivity.AssignedRole>) {
        val sms = SmsManager.getDefault(); var sent = 0; var fail = 0
        for (p in players) { try { sms.sendTextMessage(p.playerPhone, null, "${toPersianNumber(gameCode)}. ${p.role.name}", null, null); sent++ } catch (e: Exception) { fail++ } }
        AlertDialog.Builder(this).setTitle("نتیجه ارسال").setMessage(if (fail == 0) "پیامک‌ها با موفقیت ارسال شدند ($sent عدد)" else "$sent پیامک ارسال شد، $fail خطا داشت").setPositiveButton("باشه", null).show()
    }

    override fun onBackPressed() { val i = Intent(this, dev.octoshrimpy.quik.feature.main.MainActivity::class.java); i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); finish() }
    override fun onSupportNavigateUp(): Boolean { val i = Intent(this, dev.octoshrimpy.quik.feature.main.MainActivity::class.java); i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); finish(); return true }
}


