package com.example.shoppingtogether

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ShoppingListAdapter(
    private val onItemClick: (ShoppingList) -> Unit
) : RecyclerView.Adapter<ShoppingListAdapter.ViewHolder>() {

    private var lists: List<ShoppingList> = emptyList()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivListImage: ImageView = view.findViewById(R.id.ivListImage)
        val tvListName: TextView = view.findViewById(R.id.tvListName)
        val tvCreatorName: TextView = view.findViewById(R.id.tvCreatorName)
        val tvItemsPreview: TextView = view.findViewById(R.id.tvItemsPreview)
        val tvUpdatedAt: TextView = view.findViewById(R.id.tvUpdatedAt)
        val tvSharingStatus: TextView = view.findViewById(R.id.tvSharingStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shopping_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val list = lists[position]
        
        // Set list name
        holder.tvListName.text = list.name
        
        // Set creator name
        holder.tvCreatorName.text = "Created by ${list.creatorName}"
        
        // Set items preview
        val itemsPreview = if (list.products.isEmpty()) {
            "No items"
        } else {
            list.products.take(3).joinToString(", ") { it.name } +
                    if (list.products.size > 3) ", ..." else ""
        }
        holder.tvItemsPreview.text = itemsPreview
        
        // Set updated time
        holder.tvUpdatedAt.text = getTimeAgo(list.updatedAt.toDate())
        
        // Set sharing status with correct logic
        val sharingStatus = when {
            list.isPublic -> "Public"
            list.sharedWith.isNotEmpty() -> "Shared"
            else -> "Private"
        }
        holder.tvSharingStatus.text = sharingStatus
        
        // Set sharing status background color based on status
        val statusColor = when {
            list.isPublic -> android.graphics.Color.parseColor("#4CAF50") // Green
            list.sharedWith.isNotEmpty() -> android.graphics.Color.parseColor("#2196F3") // Blue
            else -> android.graphics.Color.parseColor("#9E9E9E") // Gray
        }
        holder.tvSharingStatus.background.setColorFilter(statusColor, android.graphics.PorterDuff.Mode.SRC_IN)
        
        // Load image if available
        if (!list.imageBase64.isNullOrBlank()) {
            try {
                val imageBytes = Base64.decode(list.imageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                holder.ivListImage.setImageBitmap(bitmap)
            } catch (e: Exception) {
                holder.ivListImage.setImageResource(R.drawable.ic_shopping_cart)
            }
        } else {
            holder.ivListImage.setImageResource(R.drawable.ic_shopping_cart)
        }
        
        // Set item click listener
        holder.itemView.setOnClickListener {
            onItemClick(list)
        }
    }
    
    private fun getTimeAgo(date: Date): String {
        val now = Date()
        val diffInMillis = now.time - date.time
        
        return when {
            diffInMillis < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diffInMillis < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)
                "$minutes min ago"
            }
            diffInMillis < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diffInMillis)
                "$hours h ago"
            }
            diffInMillis < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diffInMillis)
                "$days days ago"
            }
            else -> dateFormat.format(date)
        }
    }

    override fun getItemCount() = lists.size

    fun updateLists(newLists: List<ShoppingList>) {
        lists = newLists
        notifyDataSetChanged()
    }
} 