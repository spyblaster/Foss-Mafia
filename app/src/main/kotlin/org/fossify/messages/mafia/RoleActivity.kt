package org.fossify.messages.mafia

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.Serializable

class RoleActivity : BaseGameActivity() {

    data class Role(val name: String, val side: String) : Serializable
    data class Scenario(val name: String, val roles: MutableList<Role>, val playerCount: Int)
    data class AssignedRole(val playerName: String, val playerPhone: String, val role: Role) : Serializable

    private val gson = Gson()
    private lateinit var scenario: Scenario
    private lateinit var players: List<GameActivity.Player>
    private lateinit var mafiaText: TextView
    private lateinit var cityText: TextView
    private lateinit var independentText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val playersJson = intent.getStringExtra("players") ?: "[]"
        val scenarioJson = intent.getStringExtra("scenario") ?: return

        val playerType = object : TypeToken<List<GameActivity.Player>>() {}.type
        players = gson.fromJson(playersJson, playerType)

        val scenarioType = object : TypeToken<Scenario>() {}.type
        scenario = gson.fromJson(scenarioJson, scenarioType)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        mafiaText = TextView(this).apply { textSize = 16f; setPadding(0, 8, 0, 8) }
        cityText = TextView(this).apply { textSize = 16f; setPadding(0, 8, 0, 8) }
        independentText = TextView(this).apply { textSize = 16f; setPadding(0, 8, 0, 8) }

        refreshRoleTexts()

        val scrollView = ScrollView(this).apply {
            addView(LinearLayout(this@RoleActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(mafiaText)
                addView(cityText)
                addView(independentText)
            })
        }

        val topButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 8)
        }

        val addRoleBtn = createStyledButton("افزودن نقش")
        addRoleBtn.setOnClickListener { showAddRoleDialog() }

        val deleteRoleBtn = createStyledButton("حذف نقش")
        deleteRoleBtn.setOnClickListener { showDeleteRoleDialog() }

        val saveScenarioBtn = createStyledButton("ذخیره سناریو")
        saveScenarioBtn.setOnClickListener { showSaveScenarioDialog() }

        topButtons.addView(addRoleBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 6, 0) })
        topButtons.addView(deleteRoleBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 6, 0) })
        topButtons.addView(saveScenarioBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 0, 0) })

        val assignBtn = createStyledButton("نقش‌دهی")
        assignBtn.setOnClickListener {
            if (scenario.roles.size != players.size) {
                AlertDialog.Builder(this@RoleActivity)
                    .setTitle("خطا")
                    .setMessage("تعداد بازیکنان با نقش‌ها برابر نیست.\nبازیکنان: ${players.size}\nنقش‌ها: ${scenario.roles.size}")
                    .setPositiveButton("باشه", null)
                    .show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this@RoleActivity)
                .setTitle("نقش‌دهی")
                .setMessage("نقش‌دهی انجام شود؟")
                .setPositiveButton("بله") { _, _ -> assignRoles() }
                .setNegativeButton("خیر", null)
                .show()
        }

        rootLayout.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        rootLayout.addView(topButtons)
        rootLayout.addView(assignBtn)

        setContentView(rootLayout)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "نقش‌های بازی"
    }

    private fun createStyledButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 11f
            val drawable = GradientDrawable().apply {
                setColor(Color.parseColor("#0097A7"))
                cornerRadius = 24f
            }
            background = drawable
            setPadding(4, 12, 4, 12)
            gravity = Gravity.CENTER
        }
    }

    private fun refreshRoleTexts() {
        val mafia = scenario.roles.filter { it.side == "مافیا" }
        val city = scenario.roles.filter { it.side == "شهروند" }
        val independent = scenario.roles.filter { it.side == "مستقل" }
        mafiaText.text = "مافیا:\n${mafia.joinToString("، ") { it.name }}"
        cityText.text = "شهروند:\n${city.joinToString("، ") { it.name }}"
        independentText.text = "مستقل:\n${independent.joinToString("، ") { it.name }}"
    }

    private fun showAddRoleDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val sideSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@RoleActivity, android.R.layout.simple_spinner_dropdown_item, listOf("شهروند", "مافیا", "مستقل"))
        }
        val nameInput = EditText(this).apply { hint = "نام نقش"; textDirection = View.TEXT_DIRECTION_RTL }
        layout.addView(TextView(this).apply { text = "ساید:" })
        layout.addView(sideSpinner)
        layout.addView(nameInput)

        AlertDialog.Builder(this)
            .setTitle("افزودن نقش")
            .setView(layout)
            .setPositiveButton("ثبت") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "نام نقش نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val side = sideSpinner.selectedItem.toString()
                scenario.roles.add(Role(name, side))
                refreshRoleTexts()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showDeleteRoleDialog() {
        val checkBoxes = scenario.roles.map { role ->
            CheckBox(this).apply { text = "${role.name} (${role.side})" }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            for (cb in checkBoxes) addView(cb)
        }
        AlertDialog.Builder(this)
            .setTitle("حذف نقش‌ها")
            .setView(layout)
            .setPositiveButton("حذف") { _, _ ->
                val toRemove = mutableListOf<Role>()
                for ((index, cb) in checkBoxes.withIndex()) {
                    if (cb.isChecked) toRemove.add(scenario.roles[index])
                }
                scenario.roles.removeAll(toRemove)
                refreshRoleTexts()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showSaveScenarioDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val nameInput = EditText(this).apply {
            hint = "نام سناریو"
            textDirection = View.TEXT_DIRECTION_RTL
        }
        layout.addView(TextView(this).apply { text = "نام سناریو را وارد کنید:" })
        layout.addView(nameInput)

        AlertDialog.Builder(this)
            .setTitle("ذخیره سناریو")
            .setView(layout)
            .setPositiveButton("تایید") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "نام سناریو نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveCustomScenario(name)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun saveCustomScenario(name: String) {
        val file = File(filesDir, "custom_scenarios.json")
        val type = object : TypeToken<MutableList<ScenarioActivity.Scenario>>() {}.type
        val list: MutableList<ScenarioActivity.Scenario> = if (file.exists()) {
            gson.fromJson(file.readText(), type)
        } else {
            mutableListOf()
        }

        val newScenario = ScenarioActivity.Scenario(
            name = name,
            roles = scenario.roles.map { ScenarioActivity.Role(it.name, it.side) },
            playerCount = scenario.roles.size
        )
        list.add(newScenario)
        file.writeText(gson.toJson(list))
        Toast.makeText(this, "سناریو ذخیره شد", Toast.LENGTH_SHORT).show()
    }

    private fun assignRoles() {
        val prefs = getSharedPreferences("mafia_counter", android.content.Context.MODE_PRIVATE)
        var counter = prefs.getInt("game_counter", 0) + 1
        if (counter > 50) counter = 1
        prefs.edit().putInt("game_counter", counter).apply()

        val shuffledPlayers = players.toMutableList().apply { shuffle() }
        val result = mutableListOf<AssignedRole>()
        for (i in shuffledPlayers.indices) {
            result.add(AssignedRole(
                playerName = shuffledPlayers[i].name,
                playerPhone = shuffledPlayers[i].phone,
                role = scenario.roles[i]
            ))
        }
        val resultIntent = Intent(this, ResultActivity::class.java)
        resultIntent.putExtra("result", ArrayList(result))
        startActivity(resultIntent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}


