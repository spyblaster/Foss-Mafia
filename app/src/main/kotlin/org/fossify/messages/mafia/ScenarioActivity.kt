package org.fossify.messages.mafia

import android.content.Intent
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.reflect.TypeToken
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityMafiaScenarioBinding
import org.fossify.messages.mafia.adapters.ScenariosAdapter
import java.io.File
import java.io.InputStreamReader

class ScenarioActivity : BaseGameActivity() {

    data class Role(val name: String, val side: String, val selectionType: Int = 0)
    data class Scenario(
        val name: String,
        val roles: List<Role>,
        val playerCount: Int,
        val isCustom: Boolean = false
    )

    private val binding by viewBinding(ActivityMafiaScenarioBinding::inflate)
    private val players = mutableListOf<GameActivity.Player>()
    private lateinit var scenariosAdapter: ScenariosAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.mafia_select_scenario_title)

        val playersJson = intent.getStringExtra("players") ?: "[]"
        val type = object : TypeToken<List<GameActivity.Player>>() {}.type
        players.addAll(mafiaGson.fromJson(playersJson, type))

        binding.scenarioTitleText.text = getString(R.string.mafia_player_count_label, players.size)

        scenariosAdapter = ScenariosAdapter(
            onDeleteGroup = { groupName -> confirmDeleteScenario(groupName) },
            onSelect = { /* selection state is kept inside adapter */ }
        )
        binding.scenarioList.adapter = scenariosAdapter

        loadScenarios()

        binding.scenarioContinueBtn.setOnClickListener {
            val selected = scenariosAdapter.getSelected()
            if (selected == null) {
                toast(R.string.mafia_select_scenario_error)
            } else {
                startRoleScreen(selected)
            }
        }
    }

    private fun loadScenarios() {
        val allScenarios = loadBuiltInScenarios() + loadCustomScenarios()
        scenariosAdapter.submitItems(ScenariosAdapter.buildItems(allScenarios))
    }

    private fun loadBuiltInScenarios(): List<Scenario> {
        val scenarios = mutableListOf<Scenario>()
        try {
            val json = InputStreamReader(assets.open("scenarios.json")).use { it.readText() }
            val rootType = object : TypeToken<Map<String, Any>>() {}.type
            val scenariosData: Map<String, Any> = mafiaGson.fromJson(json, rootType)

            for ((scenarioName, playerCountsAny) in scenariosData) {
                val playerCounts = playerCountsAny as Map<*, *>
                for ((count, rolesAny) in playerCounts) {
                    val rolesMap = rolesAny as Map<*, *>
                    val rolesList = mutableListOf<Role>()
                    (rolesMap["مافیا"] as? List<*>)?.forEach { rolesList.add(Role(it.toString(), "مافیا")) }
                    (rolesMap["شهروند"] as? List<*>)?.forEach { rolesList.add(Role(it.toString(), "شهروند")) }
                    (rolesMap["مستقل"] as? List<*>)?.forEach { rolesList.add(Role(it.toString(), "مستقل")) }
                    scenarios.add(Scenario(scenarioName, rolesList, count.toString().toInt()))
                }
            }
        } catch (e: Exception) {
            toast(R.string.mafia_load_scenarios_error)
        }
        return scenarios
    }

    private fun loadCustomScenarios(): List<Scenario> {
        val file = File(filesDir, "custom_scenarios.json")
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<Scenario>>() {}.type
            val list: List<Scenario> = mafiaGson.fromJson(file.readText(), type)
            list.map { it.copy(isCustom = true) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun confirmDeleteScenario(name: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mafia_delete_scenario_title)
            .setMessage(getString(R.string.mafia_delete_scenario_confirm, name))
            .setPositiveButton(R.string.mafia_yes) { _, _ ->
                deleteCustomScenario(name)
                loadScenarios()
            }
            .setNegativeButton(R.string.mafia_no, null)
            .show()
    }

    private fun deleteCustomScenario(name: String) {
        val file = File(filesDir, "custom_scenarios.json")
        if (!file.exists()) return
        val type = object : TypeToken<MutableList<Scenario>>() {}.type
        val list: MutableList<Scenario> = mafiaGson.fromJson(file.readText(), type)
        list.removeAll { it.name == name }
        file.writeText(mafiaGson.toJson(list))
    }

    private fun startRoleScreen(scenario: Scenario) {
        val intent = Intent(this, RoleActivity::class.java)
        intent.putExtra("players", mafiaGson.toJson(players))
        intent.putExtra("scenario", mafiaGson.toJson(scenario))
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
