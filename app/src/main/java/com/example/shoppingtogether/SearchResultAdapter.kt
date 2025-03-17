package com.example.shoppingtogether

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchResultAdapter (
    private val onItemClick: (Int) -> Unit
): RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    private var searchResultItems: List<String> = emptyList()

    class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val tvTitle = view.findViewById<TextView>(R.id.tvSearchResultTitle)
        val btnSelect = view.findViewById<ImageButton>(R.id.btnSelectItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        var view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_result_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = searchResultItems.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val title = searchResultItems[position]
        holder.tvTitle.text = title
        holder.btnSelect.setOnClickListener { onItemClick(position) }
    }

    fun updateItems(newItems: List<String>) {
        searchResultItems = newItems
        notifyDataSetChanged()
    }

}