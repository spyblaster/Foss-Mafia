package org.fossify.messages.mafia.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.databinding.ItemMafiaScenarioHeaderBinding
import org.fossify.messages.databinding.ItemMafiaScenarioOptionBinding
import org.fossify.messages.mafia.ScenarioActivity

/**
 * A flat adapter whose items are either a header (scenario name) or a
 * selectable option (player count) for that scenario.
 */
class ScenariosAdapter(
    private val onDeleteGroup: (groupName: String) -> Unit,
    private val onSelect: (ScenarioActivity.Scenario) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<ScenarioItem> = emptyList()
    private var selectedScenario: ScenarioActivity.Scenario? = null

    fun submitItems(list: List<ScenarioItem>) {
        items = list
        notifyDataSetChanged()
    }

    fun getSelected(): ScenarioActivity.Scenario? = selectedScenario

    sealed class ScenarioItem {
        data class Header(val name: String, val isCustom: Boolean) : ScenarioItem()
        data class Option(val scenario: ScenarioActivity.Scenario) : ScenarioItem()
    }

    companion object {
        private const val VIEW_HEADER = 0
        private const val VIEW_OPTION = 1

        /** Converts a list of scenarios (already grouped by name in order) to flat items. */
        fun buildItems(scenarios: List<ScenarioActivity.Scenario>): List<ScenarioItem> {
            val result = mutableListOf<ScenarioItem>()
            val grouped = scenarios.groupBy { it.name }
            for ((name, group) in grouped) {
                result += ScenarioItem.Header(name, group.first().isCustom)
                for (s in group) result += ScenarioItem.Option(s)
            }
            return result
        }
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ScenarioItem.Header -> VIEW_HEADER
        is ScenarioItem.Option -> VIEW_OPTION
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_HEADER -> HeaderVH(ItemMafiaScenarioHeaderBinding.inflate(inflater, parent, false))
            else -> OptionVH(ItemMafiaScenarioOptionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ScenarioItem.Header -> (holder as HeaderVH).bind(item)
            is ScenarioItem.Option -> (holder as OptionVH).bind(item)
        }
    }

    inner class HeaderVH(private val b: ItemMafiaScenarioHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ScenarioItem.Header) {
            b.itemScenarioHeaderTitle.text = item.name
            b.itemScenarioHeaderDeleteBtn.isVisible = item.isCustom
            b.itemScenarioHeaderDeleteBtn.setOnClickListener { onDeleteGroup(item.name) }
        }
    }

    inner class OptionVH(private val b: ItemMafiaScenarioOptionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ScenarioItem.Option) {
            val ctx = b.root.context
            b.itemScenarioOptionText.text = ctx.getString(
                org.fossify.messages.R.string.mafia_player_count_label, item.scenario.playerCount
            )
            b.itemScenarioOptionRadio.isChecked = item.scenario == selectedScenario
            b.root.setOnClickListener {
                selectedScenario = item.scenario
                onSelect(item.scenario)
                notifyDataSetChanged()
            }
        }
    }
}
