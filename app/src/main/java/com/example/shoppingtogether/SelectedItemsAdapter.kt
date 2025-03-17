package com.example.shoppingtogether

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SelectedItemsAdapter(
    private val onItemClick: (Int, Boolean) -> Unit
): RecyclerView.Adapter<SelectedItemsAdapter.ViewHolder>() {
    private var selectedItems: List<ShoppingListItem> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var tvTitle = view.findViewById<TextView>(R.id.tvSelectedItemTitle)
        var btnPlusSelected = view.findViewById<Button>(R.id.btnPlusSelect)
        var btnMinusSelected = view.findViewById<Button>(R.id.btnMinusSelect)
        var tvQuantity = view.findViewById<TextView>(R.id.tvQuantity)
    }

    override fun getItemCount() = selectedItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.selected_items, parent, false)
        return ViewHolder(view)

    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val selectedItem = selectedItems[position]
        holder.tvTitle.text = selectedItem.name
        holder.tvQuantity.text = selectedItem.quantity.toString()
        holder.btnPlusSelected.setOnClickListener { onItemClick(position, true) }
        holder.btnMinusSelected.setOnClickListener {
            onItemClick(position, false)
            notifyDataSetChanged()
        }
    }

    fun updateItems(newItems: MutableList<ShoppingListItem>) {
        selectedItems = newItems
        notifyDataSetChanged()
    }
}