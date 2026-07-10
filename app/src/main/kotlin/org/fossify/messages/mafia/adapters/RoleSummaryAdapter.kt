package org.fossify.messages.mafia.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import org.fossify.messages.databinding.ItemMafiaRoleGroupBinding
import org.fossify.messages.mafia.RoleActivity

data class RoleGroup(
    val title: String,
    val color: Int,
    val roles: List<RoleActivity.Role>
)

class RoleSummaryAdapter : RecyclerView.Adapter<RoleSummaryAdapter.VH>() {

    private var groups: List<RoleGroup> = emptyList()

    fun submitGroups(list: List<RoleGroup>) {
        groups = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = groups.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMafiaRoleGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(groups[position])

    class VH(private val b: ItemMafiaRoleGroupBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(group: RoleGroup) {
            b.itemRoleGroupTitle.text = group.title
            b.itemRoleGroupTitle.setTextColor(group.color)
            b.itemRoleGroupDivider.setBackgroundColor(group.color)

            b.itemRoleGroupChips.removeAllViews()
            if (group.roles.isEmpty()) {
                // Show a single disabled chip as placeholder so the group is still visible
                val chip = Chip(b.root.context)
                chip.text = "—"
                chip.isEnabled = false
                b.itemRoleGroupChips.addView(chip)
            } else {
                for (role in group.roles) {
                    val chip = Chip(b.root.context)
                    chip.text = role.name
                    chip.isClickable = false
                    b.itemRoleGroupChips.addView(chip)
                }
            }
        }
    }
}
