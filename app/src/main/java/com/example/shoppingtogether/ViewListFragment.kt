package com.example.shoppingtogether

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.shoppingtogether.databinding.FragmentViewListBinding
import com.google.firebase.firestore.FirebaseFirestore

class ViewListFragment : Fragment() {
    private var _binding: FragmentViewListBinding? = null
    private val binding get() = _binding!!
    
    private val TAG = "ViewListFragment"
    private val args: ViewListFragmentArgs by navArgs()
    
    private var shoppingList: ShoppingList? = null
    private lateinit var listItemAdapter: ViewListItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup toolbar with back button
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Setup adapter for list items
        listItemAdapter = ViewListItemAdapter()
        binding.rvListItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = listItemAdapter
        }
        
        // Load the shopping list data
        loadShoppingList(args.listId)
    }
    
    private fun loadShoppingList(listId: String) {
        FirebaseFirestore.getInstance().collection("lists").document(listId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Convert to ShoppingList object
                    val list = document.toObject(ShoppingList::class.java)?.copy(id = document.id)
                    shoppingList = list
                    
                    if (list != null) {
                        // Populate the UI with list data
                        binding.tvListName.text = list.name
                        binding.tvCreatorInfo.text = "Created by ${list.creatorName}"
                        
                        // Load image if it exists
                        if (!list.imageBase64.isNullOrBlank()) {
                            try {
                                val imageBytes = Base64.decode(list.imageBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                binding.ivListImage.setImageBitmap(bitmap)
                                binding.ivListImage.visibility = View.VISIBLE
                            } catch (e: Exception) {
                                Log.e(TAG, "Error loading list image", e)
                            }
                        }
                        
                        // Set up shared users
                        if (list.sharedWith.isNotEmpty()) {
                            binding.tvSharedWith.text = "Shared with:\n${list.sharedWith.joinToString("\n")}"
                        } else {
                            binding.tvSharedWith.text = "Not shared with anyone"
                        }
                        
                        // Set up list items
                        listItemAdapter.updateItems(list.products)
                    }
                } else {
                    Log.d(TAG, "No such list")
                    Toast.makeText(context, "List not found", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading list", e)
                Toast.makeText(context, "Error loading list: ${e.message}", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Adapter for list items in view-only mode
    private class ViewListItemAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<ViewListItemAdapter.ViewHolder>() {
        
        private var items: List<ShoppingListItem> = emptyList()
        
        inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val checkbox = view.findViewById<android.widget.CheckBox>(R.id.checkboxItem)
            val textItemName = view.findViewById<android.widget.TextView>(R.id.tvItemName)
            val textQuantity = view.findViewById<android.widget.TextView>(R.id.tvQuantity)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_shopping_list_item, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            holder.checkbox.isChecked = item.checked
            holder.textItemName.text = item.name
            holder.textQuantity.text = item.quantity.toString()
            
            // Disable checkbox interaction in view-only mode
            holder.checkbox.isEnabled = false
        }
        
        override fun getItemCount() = items.size
        
        fun updateItems(newItems: List<ShoppingListItem>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
} 