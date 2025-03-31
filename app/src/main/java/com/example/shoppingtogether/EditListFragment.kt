package com.example.shoppingtogether

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.shoppingtogether.databinding.FragmentEditListBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class EditListFragment : Fragment() {

    private var _binding: FragmentEditListBinding? = null
    private val binding get() = _binding!!
    
    private val TAG = "EditListFragment"
    private val args: EditListFragmentArgs by navArgs()
    
    private var shoppingList: ShoppingList? = null
    private var selectedItems = mutableListOf<ShoppingListItem>()
    private val sharedUsers = mutableListOf<String>() // Store emails of users to share with

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var imageUri: Uri? = null
    private var originalImageBase64: String? = null
    private lateinit var currentPhotoPath: String
    private lateinit var listItemAdapter: ListItemAdapter

    private val getImageFromGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                imageUri = uri
                try {
                    // Simple image loading
                    binding.ivListImage.setImageURI(uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading image", e)
                    Toast.makeText(requireContext(), "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val getImageFromCamera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                // Load the captured image
                val file = File(currentPhotoPath)
                imageUri = Uri.fromFile(file)
                binding.ivListImage.setImageURI(imageUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading camera image", e)
                Toast.makeText(requireContext(), "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Setup toolbar with back button
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Setup adapter for list items
        listItemAdapter = ListItemAdapter { 
            position, action -> handleItemAction(position, action) 
        }
        binding.rvListItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = listItemAdapter
        }
        
        // Load the shopping list data
        loadShoppingList(args.listId)
        
        // Setup image selection
        binding.btnSelectImage.setOnClickListener {
            showImageSelectionDialog()
        }
        
        // Setup share functionality
        binding.btnAddUser.setOnClickListener {
            addUserToShareList()
        }
        
        // Setup add item button
        binding.btnAddItem.setOnClickListener {
            addNewItem()
        }
        
        // Setup save and delete buttons
        binding.btnSave.setOnClickListener {
            saveList()
        }
        
        binding.btnDelete.setOnClickListener {
            confirmDeleteList()
        }
    }
    
    private fun loadShoppingList(listId: String) {
        binding.progressBar.visibility = View.VISIBLE
        
        firestore.collection("lists").document(listId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Convert to ShoppingList object
                    val list = document.toObject<ShoppingList>()?.copy(
                        id = document.id,
                        isPublic = document.getBoolean("isPublic") ?: false
                    )
                    Log.d(TAG, "isPublic: "+ document.get("isPublic").toString());
                    Log.d(TAG, list.toString());
                    shoppingList = list
                    
                    if (list != null) {
                        // Populate the UI with list data
                        binding.etListName.setText(list.name)
                        binding.switchPublic.isChecked = list.isPublic
                        
                        // Load image if it exists
                        originalImageBase64 = list.imageBase64
                        if (!list.imageBase64.isNullOrBlank()) {
                            try {
                                val imageBytes = Base64.decode(list.imageBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                binding.ivListImage.setImageBitmap(bitmap)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error loading list image", e)
                            }
                        }
                        
                        // Set up shared users
                        sharedUsers.clear()
                        if (list.sharedWith.isNotEmpty()) {
                            sharedUsers.addAll(list.sharedWith)
                            updateSharedUsersText()
                        }
                        
                        // Set up list items
                        selectedItems.clear()
                        selectedItems.addAll(list.products)
                        listItemAdapter.updateItems(selectedItems)
                    }
                } else {
                    Log.d(TAG, "No such list")
                    Toast.makeText(context, "List not found", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading list", e)
                Toast.makeText(context, "Error loading list: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                findNavController().navigateUp()
            }
    }
    
    private fun addNewItem() {
        val itemName = binding.etNewItem.text.toString().trim()
        
        if (itemName.isEmpty()) {
            Toast.makeText(context, "Please enter an item name", Toast.LENGTH_SHORT).show()
            return
        }
        
        selectedItems.add(ShoppingListItem(itemName, 1, false))
        listItemAdapter.updateItems(selectedItems)
        binding.etNewItem.text?.clear()
    }
    
    private fun handleItemAction(position: Int, action: ListItemAction) {
        when (action) {
            ListItemAction.INCREMENT -> {
                selectedItems[position].quantity++
                listItemAdapter.updateItems(selectedItems)
            }
            ListItemAction.DECREMENT -> {
                val item = selectedItems[position]
                if (item.quantity > 1) {
                    item.quantity--
                } else {
                    selectedItems.removeAt(position)
                }
                listItemAdapter.updateItems(selectedItems)
            }
            ListItemAction.TOGGLE -> {
                selectedItems[position].checked = !selectedItems[position].checked
                listItemAdapter.updateItems(selectedItems)
            }
        }
    }

    private fun addUserToShareList() {
        val email = binding.etShareEmail.text.toString().trim()
        
        // Validate email
        if (email.isEmpty()) {
            binding.tilShareEmail.error = "Please enter an email address"
            return
        }
        
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilShareEmail.error = "Please enter a valid email address"
            return
        }
        
        // Add email to list if not already there
        if (!sharedUsers.contains(email)) {
            sharedUsers.add(email)
            binding.etShareEmail.text?.clear()
            binding.tilShareEmail.error = null
            updateSharedUsersText()
            Toast.makeText(context, "User added to share list", Toast.LENGTH_SHORT).show()
        } else {
            binding.tilShareEmail.error = "This user is already in the share list"
        }
    }
    
    private fun updateSharedUsersText() {
        if (sharedUsers.isEmpty()) {
            binding.tvSharedUsers.text = "No users added yet"
        } else {
            binding.tvSharedUsers.text = sharedUsers.joinToString("\n")
        }
    }
    
    private fun uploadImage(onSuccess: (String) -> Unit, onFailure: () -> Unit) {
        // If image wasn't changed, use the original
        if (imageUri == null && originalImageBase64 != null) {
            onSuccess(originalImageBase64!!)
            return
        }
        
        val imageUri = this.imageUri ?: return onSuccess("")  // No image selected is OK
        
        try {
            // Get bitmap from Uri
            val inputStream = requireContext().contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // Compress and convert to Base64
            val outputStream = ByteArrayOutputStream()
            // Compress to reduce size - adjust quality as needed (0-100)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)
            
            // Base64 string is ready to be stored in Firestore
            onSuccess(base64Image)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode image", e)
            onFailure()
        }
    }
    
    private fun saveList() {
        val currentList = shoppingList ?: return
        val listId = currentList.id
        val userId = firebaseAuth.currentUser?.uid
        val name = binding.etListName.text.toString()
        val isPublic = binding.switchPublic.isChecked
        
        if (userId == null) {
            Toast.makeText(context, "You must be logged in to edit a list", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Only allow editing if user is the creator
        if (userId != currentList.creatorId) {
            Toast.makeText(context, "You don't have permission to edit this list", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (name.isBlank()) {
            Toast.makeText(context, "Enter a name for this list!", Toast.LENGTH_LONG).show()
            return
        }
        
        if (selectedItems.isEmpty()) {
            Toast.makeText(context, "The list is empty!", Toast.LENGTH_LONG).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        uploadImage(
            onSuccess = { imageBase64 ->
                // Update shopping list
                val updatedList = hashMapOf(
                    "name" to name,
                    "isPublic" to isPublic,
                    "sharedWith" to sharedUsers,
                    "imageBase64" to imageBase64,
                    "products" to selectedItems.map { item -> 
                        hashMapOf(
                            "name" to item.name, 
                            "quantity" to item.quantity,
                            "checked" to item.checked
                        )
                    },
                    "updatedAt" to Timestamp.now()
                )

                firestore.collection("lists").document(listId)
                    .update(updatedList)
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(context, "Shopping list updated!", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            },
            onFailure = {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Failed to process image. Try again.", Toast.LENGTH_LONG).show()
            }
        )
    }
    
    private fun confirmDeleteList() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete List")
            .setMessage("Are you sure you want to delete this shopping list? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteList() }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteList() {
        val listId = shoppingList?.id ?: return
        val userId = firebaseAuth.currentUser?.uid ?: return
        
        // Only allow deletion if user is the creator
        if (userId != shoppingList?.creatorId) {
            Toast.makeText(context, "You don't have permission to delete this list", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.progressBar.visibility = View.VISIBLE
        
        firestore.collection("lists").document(listId)
            .delete()
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Shopping list deleted!", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Error deleting list: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showImageSelectionDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Select List Image")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> launchCamera()
                    1 -> launchGallery()
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun launchCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            // Create the file where the photo should go
            val photoFile = createImageFile()
            photoFile.also {
                val photoURI = FileProvider.getUriForFile(
                    requireContext(),
                    "com.example.shoppingtogether.fileprovider",
                    it
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                getImageFromCamera.launch(takePictureIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching camera", e)
            Toast.makeText(requireContext(), "Error launching camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun launchGallery() {
        try {
            // Use ACTION_GET_CONTENT instead of ACTION_PICK for better compatibility
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            
            // Add these flags to avoid the Google Photos app issues
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // Try to show a chooser with all available image sources
            val chooserIntent = Intent.createChooser(intent, "Select Image")
            getImageFromGallery.launch(chooserIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching gallery", e)
            Toast.makeText(requireContext(), "Error opening gallery: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Enum for list item actions
    enum class ListItemAction {
        INCREMENT, DECREMENT, TOGGLE
    }
    
    // Adapter for list items
    inner class ListItemAdapter(
        private val onAction: (Int, ListItemAction) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ListItemAdapter.ViewHolder>() {
        
        private var items: List<ShoppingListItem> = emptyList()
        
        inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val checkbox = view.findViewById<android.widget.CheckBox>(R.id.checkboxItem)
            val textItemName = view.findViewById<android.widget.TextView>(R.id.tvItemName)
            val textQuantity = view.findViewById<android.widget.TextView>(R.id.tvQuantity)
            val btnPlus = view.findViewById<android.widget.Button>(R.id.btnPlusItem)
            val btnMinus = view.findViewById<android.widget.Button>(R.id.btnMinusItem)
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
            
            // Set up click listeners
            holder.checkbox.setOnClickListener {
                onAction(position, ListItemAction.TOGGLE)
            }
            
            holder.btnPlus.setOnClickListener {
                onAction(position, ListItemAction.INCREMENT)
            }
            
            holder.btnMinus.setOnClickListener {
                onAction(position, ListItemAction.DECREMENT)
            }
        }
        
        override fun getItemCount() = items.size
        
        fun updateItems(newItems: List<ShoppingListItem>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
} 