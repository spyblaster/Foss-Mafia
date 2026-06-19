package org.fossify.messages.mafia

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.messages.R
import java.io.File
import java.io.InputStreamReader

class ScenarioActivity : BaseGameActivity() {

    data class Role(val name: String, val side: String)
    data class Scenario(val name: String, val roles: List<Role>, val playerCount: Int, val isCustom: Boolean = false)

    private val gson = Gson()
    private val players = mutableListOf<GameActivity.Player>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val playersJson = intent.getStringExtra("players") ?: "[]"
        val type = object : TypeToken<List<GameActivity.Player>>() {}.type
        players.addAll(gson.fromJson(playersJson, type))

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val titleText = TextView(this).apply {
            text = "تعداد بازیکنان: ${players.size}"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }

        val allScenarios = loadAllScenarios() + loadCustomScenarios()
        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        var checkedRadioButton: RadioButton? = null

        val groupedScenarios = allScenarios.groupBy { it.name }

        for ((scenarioName, scenarios) in groupedScenarios) {
            val isCustom = scenarios.first().isCustom

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 16, 0, 4)
            }

            val header = TextView(this).apply {
                text = scenarioName
                textSize = 16f
                setTextColor(0xFFCC0000.toInt())
                gravity = Gravity.START
            }
            headerRow.addView(header, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            if (isCustom) {
                val deleteBtn = Button(this).apply {
                    text = "❌"
                    setBackgroundColor(Color.TRANSPARENT)
                    setMinWidth(0)
                    setMinHeight(0)
                    setPadding(8, 8, 8, 8)
                    setOnClickListener {
                        AlertDialog.Builder(this@ScenarioActivity)
                            .setTitle("حذف سناریو")
                            .setMessage("سناریو \"$scenarioName\" حذف شود؟")
                            .setPositiveButton("بله") { _, _ ->
                                deleteCustomScenario(scenarioName)
                                recreate()
                            }
                            .setNegativeButton("خیر", null)
                            .show()
                    }
                }
                headerRow.addView(deleteBtn)
            }

            mainLayout.addView(headerRow)

            for (scenario in scenarios) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(32, 4, 8, 4)
                }

                val radio = RadioButton(this).apply {
                    text = "${scenario.playerCount}"
                    textSize = 14f
                    tag = scenario
                    setOnClickListener {
                        checkedRadioButton?.isChecked = false
                        checkedRadioButton = this
                        this.isChecked = true
                    }
                }

                row.addView(radio)
                mainLayout.addView(row)
            }
        }

        scrollView.addView(mainLayout)

        val continueBtn = createStyledButton("ادامه")
        continueBtn.setOnClickListener {
            val selectedScenario = checkedRadioButton?.tag as? Scenario
            if (selectedScenario == null) {
                Toast.makeText(this@ScenarioActivity, "یک سناریو انتخاب کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startRoleScreen(selectedScenario)
        }

        rootLayout.addView(titleText)
        rootLayout.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        rootLayout.addView(continueBtn)

        setContentView(rootLayout)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "انتخاب سناریو"
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

    private fun loadAllScenarios(): List<Scenario> {
        val scenarios = mutableListOf<Scenario>()
        try {
            val inputStream = assets.open("scenarios.json")
            val reader = InputStreamReader(inputStream)
            val json = reader.readText()
            reader.close()

            val rootType = object : TypeToken<Map<String, Any>>() {}.type
            val scenariosData: Map<String, Any> = gson.fromJson(json, rootType)

            for ((scenarioName, playerCountsAny) in scenariosData) {
                val playerCounts = playerCountsAny as Map<String, Any>
                for ((count, rolesAny) in playerCounts) {
                    val rolesMap = rolesAny as Map<String, List<String>>
                    val rolesList = mutableListOf<Role>()
                    rolesMap["مافیا"]?.forEach { rolesList.add(Role(it, "مافیا")) }
                    rolesMap["شهروند"]?.forEach { rolesList.add(Role(it, "شهروند")) }
                    rolesMap["مستقل"]?.forEach { rolesList.add(Role(it, "مستقل")) }
                    scenarios.add(Scenario(scenarioName, rolesList, count.toInt()))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در بارگذاری سناریوها", Toast.LENGTH_SHORT).show()
        }
        return scenarios
    }

    private fun loadCustomScenarios(): List<Scenario> {
        val file = File(filesDir, "custom_scenarios.json")
        if (!file.exists()) return emptyList()
        try {
            val json = file.readText()
            val type = object : TypeToken<List<Scenario>>() {}.type
            val list: List<Scenario> = gson.fromJson(json, type)
            return list.map { it.copy(isCustom = true) }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun deleteCustomScenario(name: String) {
        val file = File(filesDir, "custom_scenarios.json")
        if (!file.exists()) return
        val json = file.readText()
        val type = object : TypeToken<List<Scenario>>() {}.type
        val list: MutableList<Scenario> = gson.fromJson(json, type)
        list.removeAll { it.name == name }
        file.writeText(gson.toJson(list))
    }

    private fun startRoleScreen(scenario: Scenario) {
        val intent = Intent(this, RoleActivity::class.java)
        intent.putExtra("players", gson.toJson(players))
        intent.putExtra("scenario", gson.toJson(scenario))
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}


