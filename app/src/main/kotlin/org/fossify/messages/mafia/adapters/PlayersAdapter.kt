package org.fossify.messages.mafia.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.databinding.ItemMafiaPlayerBinding
import org.fossify.messages.mafia.GameActivity

class PlayersAdapter(
    private val selectedPlayers: () -> Set<GameActivity.Player>,
    private val onToggle: (GameActivity.Player, Boolean) -> Unit,
    private val onDelete: (GameActivity.Player) -> Unit,
    private val onEdit: (GameActivity.Player) -> Unit
) : ListAdapter<GameActivity.Player, PlayersAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMafiaPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemMafiaPlayerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(player: GameActivity.Player) {
            binding.itemPlayerCheckbox.setOnCheckedChangeListener(null)
            binding.itemPlayerCheckbox.text = player.name
            binding.itemPlayerCheckbox.isChecked = selectedPlayers().contains(player)
            binding.itemPlayerCheckbox.setOnCheckedChangeListener { _, isChecked ->
                onToggle(player, isChecked)
            }
            // Don't show phone number in the list
            binding.itemPlayerPhone.text = ""
            binding.itemPlayerEditBtn.setOnClickListener { onEdit(player) }
            binding.itemPlayerDeleteBtn.setOnClickListener { onDelete(player) }
            binding.root.setOnLongClickListener { onEdit(player); true }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<GameActivity.Player>() {
            override fun areItemsTheSame(oldItem: GameActivity.Player, newItem: GameActivity.Player) =
                oldItem.name == newItem.name && oldItem.phone == newItem.phone
            override fun areContentsTheSame(oldItem: GameActivity.Player, newItem: GameActivity.Player) =
                oldItem == newItem
        }
    }
}