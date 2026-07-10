package org.fossify.messages.mafia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.reflect.TypeToken
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityMafiaRoleBinding
import org.fossify.messages.mafia.adapters.RoleGroup
import org.fossify.messages.mafia.adapters.RoleSummaryAdapter
import java.io.File
import java.io.Serializable

class RoleActivity : BaseGameActivity() {

    data class Role(val name: String, val side: String, val selectionType: Int = 0) : Serializable
    data class Scenario(val name: String, val roles: MutableList<Role>, val playerCount: Int)
    data class AssignedRole(val playerName: String, val playerPhone: String, val role: Role, var lives: Int = 1) : Serializable

    companion object {
        const val SELECT_SINGLE = 0
        const val SELECT_TWO = 1
        const val SELECT_MULTI = 2
    }

    private val binding by viewBinding(ActivityMafiaRoleBinding::inflate)
    private val summaryAdapter = RoleSummaryAdapter()
    private lateinit var scenario: Scenario
    private lateinit var players: List<GameActivity.Player>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.mafia_roles_title)
        val playersJson = intent.getStringExtra("players") ?: "[]"
        val scenarioJson = intent.getStringExtra("scenario") ?: return
        val playerType = object : TypeToken<List<GameActivity.Player>>() {}.type
        players = mafiaGson.fromJson(playersJson, playerType)
        val scenarioType = object : TypeToken<Scenario>() {}.type
        scenario = mafiaGson.fromJson(scenarioJson, scenarioType)
        binding.roleSummaryList.adapter = summaryAdapter
        refreshSummary()
        binding.roleAddBtn.setOnClickListener { showAddRoleDialog() }
        binding.roleDeleteBtn.setOnClickListener { showDeleteRolesDialog() }
        binding.roleSaveScenarioBtn.setOnClickListener { showSaveScenarioDialog() }
        binding.roleAssignBtn.setOnClickListener { attemptAssign() }
        binding.roleSeatingBtn.setOnClickListener { showSeatingDialog() }
    }

    private fun sideColor(side: String): Int = when (side) {
        "مافیا" -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
        "شهروند" -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
        else -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
    }

    private fun refreshSummary() {
        val sides = listOf(
            "مافیا" to R.string.mafia_side_mafia,
            "شهروند" to R.string.mafia_side_city,
            "مستقل" to R.string.mafia_side_independent
        )
        val groups = sides.map { (side, labelRes) ->
            RoleGroup(
                title = getString(labelRes),
                color = sideColor(side),
                roles = scenario.roles.filter { it.side == side }
            )
        }
        summaryAdapter.submitGroups(groups)
    }

    private fun showAddRoleDialog() {
        val sides = listOf(
            getString(R.string.mafia_side_city) to "شهروند",
            getString(R.string.mafia_side_mafia) to "مافیا",
            getString(R.string.mafia_side_independent) to "مستقل"
        ).reversed()
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mafia_add_role)
            .setSingleChoiceItems(sides.map { it.first }.toTypedArray(), sides.size - 1) { _, _ -> }
            .setPositiveButton(R.string.mafia_continue_btn, null)
            .setNegativeButton(R.string.mafia_cancel, null)
            .create()
        dlg.setOnShowListener {
            dlg.listView?.layoutDirection = View.LAYOUT_DIRECTION_RTL
            dlg.listView?.textDirection = View.TEXT_DIRECTION_RTL
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedIdx = dlg.listView?.checkedItemPosition ?: -1
                if (selectedIdx < 0) { dlg.dismiss(); return@setOnClickListener }
                val side = sides[selectedIdx].second
                dlg.dismiss()
                showAddRoleNameDialog(side)
            }
        }
        dlg.show()
    }

    private fun showAddRoleNameDialog(side: String) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val nameLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val nameField = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = getString(R.string.mafia_role_name_hint)
        }
        val nameEdit = com.google.android.material.textfield.TextInputEditText(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textDirection = View.TEXT_DIRECTION_RTL
        }
        nameField.addView(nameEdit)
        nameLayout.addView(nameField)
        val sideLabel = when (side) {
            "مافیا" -> getString(R.string.mafia_side_mafia)
            "مستقل" -> getString(R.string.mafia_side_independent)
            else -> getString(R.string.mafia_side_city)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(sideLabel)
            .setView(nameLayout)
            .setPositiveButton(R.string.mafia_continue_btn, null)
            .setNegativeButton(R.string.mafia_cancel, null)
            .show()
            .apply {
                getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = nameEdit.text.toString().trim()
                    if (name.isEmpty()) {
                        nameField.error = getString(R.string.mafia_role_name_empty_error)
                        return@setOnClickListener
                    }
                    dismiss()
                    showAddRoleTypeDialog(side, name)
                }
            }
    }

    private fun showAddRoleTypeDialog(side: String, roleName: String) {
        val selectionTypes = listOf(
            getString(R.string.mafia_select_single) to SELECT_SINGLE,
            getString(R.string.mafia_select_two) to SELECT_TWO,
            getString(R.string.mafia_select_multi) to SELECT_MULTI
        ).reversed()
        val sideLabel = when (side) {
            "مافیا" -> getString(R.string.mafia_side_mafia)
            "مستقل" -> getString(R.string.mafia_side_independent)
            else -> getString(R.string.mafia_side_city)
        }
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(sideLabel)
            .setSingleChoiceItems(selectionTypes.map { it.first }.toTypedArray(), -1) { _, _ -> }
            .setPositiveButton(R.string.mafia_register, null)
            .setNegativeButton(R.string.mafia_cancel, null)
            .create()
        dlg.setOnShowListener {
            dlg.listView?.layoutDirection = View.LAYOUT_DIRECTION_RTL
            dlg.listView?.textDirection = View.TEXT_DIRECTION_RTL
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedIdx = dlg.listView?.checkedItemPosition ?: -1
                if (selectedIdx < 0) {
                    toast("یک نوع انتخاب کنید")
                    return@setOnClickListener
                }
                val selType = selectionTypes[selectedIdx].second
                scenario.roles.add(Role(roleName, side, selType))
                refreshSummary()
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    private fun dp(): Int = (8 * resources.displayMetrics.density).toInt()

    private val textColor get() = getProperTextColor()

    private fun showDeleteRolesDialog() {
        if (scenario.roles.isEmpty()) return
        val checkBoxes = scenario.roles.map { role ->
            CheckBox(this).apply { text = "${role.name} (${role.side})" }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            for (cb in checkBoxes) addView(cb)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mafia_delete_role)
            .setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton(R.string.mafia_delete_role) { _, _ ->
                val toRemove = checkBoxes.indices.filter { checkBoxes[it].isChecked }.map { scenario.roles[it] }
                scenario.roles.removeAll(toRemove)
                refreshSummary()
            }
            .setNegativeButton(R.string.mafia_cancel, null)
            .show()
    }

    private fun showSaveScenarioDialog() {
        val dialogBinding = org.fossify.messages.databinding.DialogMafiaAddPlayerBinding.inflate(layoutInflater)
        dialogBinding.dialogPlayerNameHolder.hint = getString(R.string.mafia_scenario_name_hint)
        dialogBinding.dialogPlayerPhoneHolder.visibility = android.view.View.GONE
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mafia_save_scenario)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.mafia_ok, null)
            .setNegativeButton(R.string.mafia_cancel, null)
            .show()
            .apply {
                getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = dialogBinding.dialogPlayerName.text.toString().trim()
                    if (name.isEmpty()) {
                        dialogBinding.dialogPlayerNameHolder.error = getString(R.string.mafia_scenario_name_empty_error)
                        return@setOnClickListener
                    }
                    saveCustomScenario(name)
                    dismiss()
                }
            }
    }

    private fun saveCustomScenario(name: String) {
        val file = File(filesDir, "custom_scenarios.json")
        val type = object : TypeToken<MutableList<ScenarioActivity.Scenario>>() {}.type
        val list: MutableList<ScenarioActivity.Scenario> = if (file.exists()) {
            mafiaGson.fromJson(file.readText(), type)
        } else {
            mutableListOf()
        }
        list.add(
            ScenarioActivity.Scenario(
                name = name,
                roles = scenario.roles.map { ScenarioActivity.Role(it.name, it.side, it.selectionType) },
                playerCount = scenario.roles.size
            )
        )
        file.writeText(mafiaGson.toJson(list))
        toast(R.string.mafia_scenario_saved)
    }

    private fun attemptAssign() {
        if (scenario.roles.size != players.size) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.mafia_error_title)
                .setMessage(getString(R.string.mafia_count_mismatch, players.size, scenario.roles.size))
                .setPositiveButton(R.string.mafia_ok, null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mafia_assign_roles)
            .setMessage(R.string.mafia_assign_confirm)
            .setPositiveButton(R.string.mafia_yes) { _, _ -> assignRoles() }
            .setNegativeButton(R.string.mafia_no, null)
            .show()
    }

    private fun assignRoles() {
        val prefs = getSharedPreferences("mafia_counter", Context.MODE_PRIVATE)
        var counter = prefs.getInt("game_counter", 0) + 1
        if (counter > 50) counter = 1
        prefs.edit().putInt("game_counter", counter).apply()
        java.io.File(filesDir, "extra_columns.json").delete()
        val shuffledPlayers = players.toMutableList().apply { shuffle() }
        val result = shuffledPlayers.indices.map { i ->
            AssignedRole(
                playerName = shuffledPlayers[i].name,
                playerPhone = shuffledPlayers[i].phone,
                role = scenario.roles[i]
            )
        }
        val intent = Intent(this, ResultActivity::class.java)
        intent.putExtra("result", ArrayList(result))
        startActivity(intent)
    }

    private fun showSeatingDialog() {
        if (players.isEmpty()) {
            toast("هیچ بازیکنی وجود ندارد")
            return
        }

        val shuffledPlayers = players.shuffled()
        val playersList = shuffledPlayers.mapIndexed { index, player ->
            "${index + 1}. ${player.name}"
        }.joinToString("\n")

        MaterialAlertDialogBuilder(this)
            .setTitle("ترتیب نشستن")
            .setMessage(playersList)
            .setPositiveButton(R.string.mafia_ok, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}