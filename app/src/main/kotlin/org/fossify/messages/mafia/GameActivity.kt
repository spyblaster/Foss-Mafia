package org.fossify.messages.mafia

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.google.gson.reflect.TypeToken
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityMafiaGameBinding
import org.fossify.messages.databinding.DialogMafiaAddPlayerBinding
import org.fossify.messages.databinding.DialogMafiaTermsBinding
import org.fossify.messages.mafia.adapters.PlayersAdapter
import java.io.File
import java.text.Collator
import java.util.Locale

class GameActivity : BaseGameActivity() {

    data class Player(
        val name: String,
        val phone: String = ""
    ) : java.io.Serializable

    data class ScenarioRole(val name: String, val side: String, val selectionType: Int = 0) : java.io.Serializable
    data class CustomScenario(val name: String, val roles: List<ScenarioRole>, val playerCount: Int) : java.io.Serializable

    data class SavedGameData(
        val players: List<Player>,
        val customScenarios: List<CustomScenario> = emptyList()
    ) : java.io.Serializable

    private val binding by viewBinding(ActivityMafiaGameBinding::inflate)
    private val players = mutableListOf<Player>()
    private val selectedPlayers = mutableSetOf<Player>()
    private val persianCollator = Collator.getInstance(Locale.forLanguageTag("fa"))
    private lateinit var playersAdapter: PlayersAdapter

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { savePlayersToUri(it) }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadPlayersFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("mafia_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("terms_accepted", false)) {
            showTermsDialog(); return
        }
        val savedGameFile = java.io.File(filesDir, "game.json")
        if (savedGameFile.exists()) {
            startActivity(Intent(this, ResultActivity::class.java).putExtra("from_saved", true))
            finish(); return
        }
        setupMainUI()
    }

    private fun showTermsDialog() {
        val termsText = loadTermsText()
        val dialogBinding = DialogMafiaTermsBinding.inflate(layoutInflater)
        dialogBinding.dialogTermsText.text = termsText
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mafia_terms_title)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .setPositiveButton(R.string.mafia_accept) { _, _ ->
                getSharedPreferences("mafia_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("terms_accepted", true).apply()
                setupMainUI()
            }
            .setNegativeButton(R.string.mafia_decline) { _, _ -> finish() }
            .show()
    }

    private fun loadTermsText(): String {
        return try {
            val json = assets.open("terms.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, Any>>() {}.type
            @Suppress("UNCHECKED_CAST")
            val data: Map<String, Any> = mafiaGson.fromJson(json, type)
            val terms = data["terms"] as? Map<String, Any>
            val title = terms?.get("title")?.toString() ?: getString(R.string.mafia_terms_title)
            val body = terms?.get("body")?.toString() ?: ""
            "$title\n\n$body"
        } catch (e: Exception) {
            getString(R.string.mafia_terms_default_body)
        }
    }

    private fun setupMainUI() {
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.mafia_select_players_title)

        playersAdapter = PlayersAdapter(
            selectedPlayers = { selectedPlayers },
            onToggle = { player, isChecked ->
                if (isChecked) selectedPlayers.add(player) else selectedPlayers.remove(player)
                updateCountText()
            },
            onDelete = { player -> confirmDeletePlayer(player) },
            onEdit = { player -> showEditPlayerDialog(player) }
        )
        binding.gamePlayersList.adapter = playersAdapter

        binding.gameImportBtn.setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        binding.gameExportBtn.setOnClickListener {
            if (players.isEmpty()) toast(R.string.mafia_no_players_to_save)
            else exportLauncher.launch("players.json")
        }
        binding.gameAddPlayerBtn.setOnClickListener { showAddPlayerDialog() }
        binding.gameContinueBtn.setOnClickListener {
            if (selectedPlayers.isEmpty()) toast(R.string.mafia_no_players_selected)
            else startScenarioScreen()
        }

        loadPlayers()
    }

    private fun loadPlayers() {
        val prefs = getSharedPreferences("mafia_players", Context.MODE_PRIVATE)
        val json = prefs.getString("players", "[]") ?: "[]"
        val type = object : TypeToken<List<Player>>() {}.type
        players.clear()
        players.addAll(mafiaGson.fromJson(json, type))
        players.sortWith { a, b -> persianCollator.compare(a.name, b.name) }
        refreshList()
    }

    private fun savePlayersToPrefs() {
        getSharedPreferences("mafia_players", Context.MODE_PRIVATE)
            .edit().putString("players", mafiaGson.toJson(players)).apply()
    }

    private fun savePlayersToUri(uri: Uri) {
        try {
            val customScenariosFile = File(filesDir, "custom_scenarios.json")
            val customScenarios: List<CustomScenario> = if (customScenariosFile.exists()) {
                val type = object : TypeToken<List<CustomScenario>>() {}.type
                mafiaGson.fromJson(customScenariosFile.readText(), type)
            } else {
                emptyList()
            }
            
            val savedData = SavedGameData(players = players, customScenarios = customScenarios)
            contentResolver.openOutputStream(uri)?.use { it.write(mafiaGson.toJson(savedData).toByteArray()) }
            toast(R.string.mafia_players_saved)
        } catch (e: Exception) {
            toast(getString(R.string.mafia_save_error, e.message ?: ""))
        }
    }

    private fun loadPlayersFromUri(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return

            val savedData: SavedGameData? = try {
                mafiaGson.fromJson(json, SavedGameData::class.java)
            } catch (e: Exception) {
                null
            }

            val importedPlayers: List<Player> = if (savedData != null) {
                savedData.players
            } else {
                val type = object : TypeToken<List<Player>>() {}.type
                mafiaGson.fromJson(json, type)
            }

            var added = 0
            for (p in importedPlayers) {
                if (players.none { it.name == p.name }) { players.add(p); added++ }
            }
            players.sortWith { a, b -> persianCollator.compare(a.name, b.name) }
            savePlayersToPrefs(); refreshList()

            if (savedData != null && savedData.customScenarios.isNotEmpty()) {
                val customScenariosFile = File(filesDir, "custom_scenarios.json")
                customScenariosFile.writeText(mafiaGson.toJson(savedData.customScenarios))
            }

            toast(getString(R.string.mafia_players_imported, added))
        } catch (e: Exception) {
            toast(getString(R.string.mafia_import_error, e.message ?: ""))
        }
    }

    private fun refreshList() {
        playersAdapter.submitList(players.toList())
        binding.gameEmptyPlaceholder.beVisibleIf(players.isEmpty())
        binding.gamePlayersList.beVisibleIf(players.isNotEmpty())
        updateCountText()
    }

    private fun updateCountText() {
        binding.gameCountText.text = getString(R.string.mafia_player_count, selectedPlayers.size.toPersianDigits())
    }

    private fun confirmDeletePlayer(player: Player) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mafia_delete_player_title)
            .setMessage(getString(R.string.mafia_delete_player_confirm, player.name))
            .setPositiveButton(R.string.mafia_yes) { _, _ ->
                players.remove(player)
                selectedPlayers.remove(player)
                savePlayersToPrefs()
                refreshList()
            }
            .setNegativeButton(R.string.mafia_no, null)
            .show()
    }

    private fun showAddPlayerDialog() {
        val dialogBinding = DialogMafiaAddPlayerBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mafia_add_player_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.mafia_register, null)
            .setNegativeButton(R.string.mafia_cancel, null)
            .show()
            .apply {
                getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = dialogBinding.dialogPlayerName.text.toString().trim()
                    val phone = dialogBinding.dialogPlayerPhone.text.toString().trim()
                    if (name.isEmpty()) {
                        dialogBinding.dialogPlayerNameHolder.error = getString(R.string.mafia_name_empty_error)
                        return@setOnClickListener
                    }
                    if (players.any { it.name == name }) {
                        dialogBinding.dialogPlayerNameHolder.error = getString(R.string.mafia_name_duplicate_error)
                        return@setOnClickListener
                    }
                    players.add(Player(name, phone))
                    players.sortWith { a, b -> persianCollator.compare(a.name, b.name) }
                    savePlayersToPrefs(); refreshList(); dismiss()
                }
            }
    }

    private fun showEditPlayerDialog(player: Player) {
        val dialogBinding = DialogMafiaAddPlayerBinding.inflate(layoutInflater)
        dialogBinding.dialogPlayerName.setText(player.name)
        dialogBinding.dialogPlayerPhone.setText(player.phone)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mafia_edit_player_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.mafia_ok, null)
            .setNegativeButton(R.string.mafia_cancel, null)
            .show()
            .apply {
                getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val newName = dialogBinding.dialogPlayerName.text.toString().trim()
                    val newPhone = dialogBinding.dialogPlayerPhone.text.toString().trim()
                    if (newName.isEmpty()) {
                        dialogBinding.dialogPlayerNameHolder.error = getString(R.string.mafia_name_empty_error)
                        return@setOnClickListener
                    }
                    if (newName != player.name && players.any { it.name == newName }) {
                        dialogBinding.dialogPlayerNameHolder.error = getString(R.string.mafia_name_duplicate_error)
                        return@setOnClickListener
                    }
                    val wasSelected = selectedPlayers.contains(player)
                    players.remove(player)
                    val updatedPlayer = player.copy(name = newName, phone = newPhone)
                    players.add(updatedPlayer)
                    players.sortWith { a, b -> persianCollator.compare(a.name, b.name) }
                    if (wasSelected) {
                        selectedPlayers.remove(player)
                        selectedPlayers.add(updatedPlayer)
                    }
                    savePlayersToPrefs(); refreshList(); dismiss()
                }
            }
    }

    private fun startScenarioScreen() {
        val intent = Intent(this, ScenarioActivity::class.java)
        intent.putExtra("players", mafiaGson.toJson(selectedPlayers.toList()))
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}