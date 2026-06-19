package org.fossify.messages.mafia

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.Collator
import java.util.Locale

class GameActivity : BaseGameActivity() {

    data class Player(val name: String, val phone: String = "") : java.io.Serializable

    private val players = mutableListOf<Player>()
    private val selectedPlayers = mutableSetOf<Player>()
    private lateinit var playersListView: LinearLayout
    private lateinit var countText: TextView
    private val gson = Gson()
    private val persianCollator = Collator.getInstance(Locale("fa"))

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { savePlayersToUri(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadPlayersFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check terms acceptance
        val prefs = getSharedPreferences("mafia_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("terms_accepted", false)) {
            showTermsDialog()
            return
        }

        val savedGameFile = java.io.File(filesDir, "game.json")
        if (savedGameFile.exists()) {
            val intent = Intent(this, ResultActivity::class.java)
            intent.putExtra("from_saved", true)
            startActivity(intent)
            finish()
            return
        }

        setupMainUI()
    }

    private fun showTermsDialog() {
        var termsText: String
        try {
            val inputStream = assets.open("terms.json")
            val json = inputStream.bufferedReader().readText()
            inputStream.close()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(json, type)
            val terms = data["terms"] as? Map<String, Any>
            val title = terms?.get("title")?.toString() ?: "شرایط استفاده"
            val body = terms?.get("body")?.toString() ?: ""
            termsText = "$title\n\n$body"
        } catch (e: Exception) {
            termsText = "با استفاده از این برنامه، شما می‌پذیرید که:\n\n" +
                    "۱. این برنامه تنها برای مدیریت بازی مافیا طراحی شده است.\n" +
                    "۲. مسئولیت استفاده از برنامه بر عهده کاربر است.\n" +
                    "۳. توسعه‌دهنده هیچ مسئولیتی در قبال سوءاستفاده از برنامه ندارد."
        }

        val scrollView = ScrollView(this).apply {
            setPadding(24, 16, 24, 16)
        }
        val textView = TextView(this).apply {
            text = termsText
            textSize = 15f
            setTextColor(Color.BLACK)
            gravity = Gravity.START
        }
        scrollView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("شرایط استفاده")
            .setView(scrollView)
            .setCancelable(false)
            .setPositiveButton("می‌پذیرم") { _, _ ->
                val prefs = getSharedPreferences("mafia_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("terms_accepted", true).apply()
                setupMainUI()
            }
            .setNegativeButton("نمی‌پذیرم") { _, _ -> finish() }
            .show()
    }

    private fun setupMainUI() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        countText = TextView(this).apply {
            text = "تعداد بازیکنان: ۰"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }

        playersListView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(playersListView)
        }

        val importExportLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 6, 0, 6)
        }

        val importBtn = createStyledButton("بارگذاری")
        importBtn.setOnClickListener { importLauncher.launch(arrayOf("application/json")) }

        val exportBtn = createStyledButton("ذخیره")
        exportBtn.setOnClickListener {
            if (players.isEmpty()) {
                Toast.makeText(this@GameActivity, "هیچ بازیکنی برای ذخیره وجود ندارد", Toast.LENGTH_SHORT).show()
            } else {
                exportLauncher.launch("players.json")
            }
        }

        importBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 6, 0) }
        importExportLayout.addView(importBtn)
        exportBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 0, 0) }
        importExportLayout.addView(exportBtn)

        val bottomLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        val addPlayerBtn = createStyledButton("ثبت بازیکن")
        addPlayerBtn.setOnClickListener { showAddPlayerDialog() }

        val continueBtn = createStyledButton("ادامه")
        continueBtn.setOnClickListener {
            if (selectedPlayers.isEmpty()) {
                Toast.makeText(this@GameActivity, "هیچ بازیکنی انتخاب نشده", Toast.LENGTH_SHORT).show()
            } else {
                startScenarioScreen()
            }
        }

        addPlayerBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 6, 0) }
        bottomLayout.addView(addPlayerBtn)
        continueBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 0, 0) }
        bottomLayout.addView(continueBtn)

        rootLayout.addView(countText)
        rootLayout.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        rootLayout.addView(importExportLayout)
        rootLayout.addView(bottomLayout)

        setContentView(rootLayout)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "انتخاب بازیکنان"

        loadPlayers()
    }

    private fun createStyledButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 13f
            val drawable = GradientDrawable().apply {
                setColor(Color.parseColor("#0097A7"))
                cornerRadius = 24f
            }
            background = drawable
            setPadding(8, 12, 8, 12)
            gravity = Gravity.CENTER
        }
    }

    private fun loadPlayers() {
        val prefs = getSharedPreferences("mafia_players", Context.MODE_PRIVATE)
        val json = prefs.getString("players", "[]") ?: "[]"
        val type = object : TypeToken<List<Player>>() {}.type
        players.clear()
        players.addAll(gson.fromJson(json, type))
        players.sortWith { a, b -> persianCollator.compare(a.name, b.name) }
        refreshList()
    }

    private fun savePlayersToPrefs() {
        val prefs = getSharedPreferences("mafia_players", Context.MODE_PRIVATE)
        prefs.edit().putString("players", gson.toJson(players)).apply()
    }

    private fun savePlayersToUri(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(gson.toJson(players).toByteArray())
            }
            Toast.makeText(this, "بازیکنان با موفقیت ذخیره شدند", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ذخیره: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPlayersFromUri(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            val type = object : TypeToken<List<Player>>() {}.type
            val importedPlayers: List<Player> = gson.fromJson(json, type)
            
            var addedCount = 0
            for (player in importedPlayers) {
                if (players.none { it.name == player.name }) {
                    players.add(player)
                    addedCount++
                }
            }
            players.sortWith { a, b -> persianCollator.compare(a.name, b.name) }
            savePlayersToPrefs()
            refreshList()
            Toast.makeText(this, "$addedCount بازیکن جدید اضافه شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در خواندن فایل: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshList() {
        playersListView.removeAllViews()
        selectedPlayers.clear()
        for (player in players) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 8, 8, 8)
            }

            val checkBox = CheckBox(this).apply {
                text = player.name
                if (player.phone.isNotEmpty()) {
                    text = "${player.name}\n${player.phone}"
                }
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedPlayers.add(player) else selectedPlayers.remove(player)
                    countText.text = "تعداد بازیکنان: ${selectedPlayers.size}"
                }
            }

            val deleteBtn = Button(this).apply {
                text = "❌"
                setBackgroundColor(Color.TRANSPARENT)
                setMinWidth(0)
                setMinHeight(0)
                setPadding(8, 8, 8, 8)
                setOnClickListener {
                    AlertDialog.Builder(this@GameActivity)
                        .setTitle("حذف بازیکن")
                        .setMessage("${player.name} حذف شود؟")
                        .setPositiveButton("بله") { _, _ ->
                            players.remove(player)
                            savePlayersToPrefs()
                            refreshList()
                        }
                        .setNegativeButton("خیر", null)
                        .show()
                }
            }

            row.addView(checkBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(deleteBtn)
            playersListView.addView(row)
        }
        countText.text = "تعداد بازیکنان: ۰"
    }

    private fun showAddPlayerDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val nameInput = EditText(this).apply {
            hint = "نام (اجباری)"
            textDirection = View.TEXT_DIRECTION_RTL
        }
        val phoneInput = EditText(this).apply {
            hint = "شماره تلفن (اختیاری)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            textDirection = View.TEXT_DIRECTION_LTR
        }
        layout.addView(nameInput)
        layout.addView(phoneInput)

        AlertDialog.Builder(this)
            .setTitle("ثبت بازیکن جدید")
            .setView(layout)
            .setPositiveButton("ثبت") { _, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "نام نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (players.any { it.name == name }) {
                    Toast.makeText(this, "این نام قبلاً ثبت شده", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                players.add(Player(name, phone))
                players.sortWith { a, b -> persianCollator.compare(a.name, b.name) }
                savePlayersToPrefs()
                refreshList()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun startScenarioScreen() {
        val intent = Intent(this, ScenarioActivity::class.java)
        intent.putExtra("players", gson.toJson(selectedPlayers.toList()))
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}



