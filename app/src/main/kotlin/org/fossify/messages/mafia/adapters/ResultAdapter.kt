package org.fossify.messages.mafia.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.databinding.ItemMafiaRoleGroupBinding
import org.fossify.messages.databinding.ItemMafiaResultRowBinding
import org.fossify.messages.mafia.RoleActivity

sealed class ResultItem {
    data class Header(val title: String, val color: Int) : ResultItem()
    data class Row(val assigned: RoleActivity.AssignedRole, val color: Int) : ResultItem()
}

class ResultAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<ResultItem> = emptyList()

    fun submitItems(list: List<ResultItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ResultItem.Header -> 0
        is ResultItem.Row -> 1
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> HeaderVH(ItemMafiaRoleGroupBinding.inflate(inflater, parent, false))
            else -> RowVH(ItemMafiaResultRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ResultItem.Header -> (holder as HeaderVH).bind(item)
            is ResultItem.Row -> (holder as RowVH).bind(item)
        }
    }

    class HeaderVH(private val b: ItemMafiaRoleGroupBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ResultItem.Header) {
            b.itemRoleGroupTitle.text = item.title
            b.itemRoleGroupTitle.setTextColor(item.color)
            b.itemRoleGroupDivider.setBackgroundColor(item.color)
            b.itemRoleGroupChips.removeAllViews()
        }
    }

    class RowVH(private val b: ItemMafiaResultRowBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ResultItem.Row) {
            b.itemResultPlayerName.text = item.assigned.playerName
            b.itemResultRoleName.text = item.assigned.role.name
            // Tint the separator "*" with the side color for a subtle visual cue
            b.itemResultSeparator.setTextColor(item.color)
        }
    }
}
