package com.example.shoppingtogether

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.shoppingtogether.databinding.FragmentAddListBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.util.UUID
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

class AddListFragment : Fragment() {

    private var _binding: FragmentAddListBinding? = null
    private val binding get() = _binding!!

    private var searchResults = mutableListOf<String>()
    private var selectedItems = mutableListOf<ShoppingListItem>()

    private lateinit var searchResultAdapter: SearchResultAdapter
    private lateinit var selectedItemsAdapter: SelectedItemsAdapter

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var imageUri: Uri? = null
    private lateinit var currentPhotoPath: String

    private val getImageFromGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                imageUri = uri
                try {
                    // Simple image loading without Glide
                    binding.ivListImage.setImageURI(uri)
                } catch (e: Exception) {
                    Log.e("AddListFragment", "Error loading image", e)
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
                Log.e("AddListFragment", "Error loading camera image", e)
                Toast.makeText(requireContext(), "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAddListBinding.inflate(inflater, container, false)
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

        // Setup image selection
        binding.btnSelectImage.setOnClickListener {
            showImageSelectionDialog()
        }

        searchResultAdapter = SearchResultAdapter { index -> addToSelectedItems(index) }
        selectedItemsAdapter = SelectedItemsAdapter { index, isPlus ->
            onSelectedItemClick(index, isPlus)
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchResultAdapter
        }

        binding.rvSelectedItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = selectedItemsAdapter
        }

        // Add enter key listener to search EditText
        binding.etProductSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN
            ) {
                search(binding.etProductSearch.text.toString())
                true
            } else {
                false
            }
        }

        binding.btnSearch.setOnClickListener {
            search(binding.etProductSearch.text.toString())
        }

        binding.btnSave.setOnClickListener {
            saveList()
        }
    }

    fun addToSelectedItems(index: Int) {
        var title = searchResults.removeAt(index)
        Log.d("AddListFragment", "Adding " + title + " to the list")
        selectedItems.add(ShoppingListItem(title, 1))
        searchResultAdapter.updateItems(searchResults)
        selectedItemsAdapter.updateItems(selectedItems)
    }

    fun onSelectedItemClick(index: Int, isPlus: Boolean) {
        val item = selectedItems[index]
        if(isPlus)
            item.quantity++
        else if(item.quantity > 1)
            item.quantity--
        else {
            selectedItems.removeAt(index)
            searchResults.add(0, item.name)
            searchResultAdapter.updateItems(searchResults)
        }
        selectedItemsAdapter.updateItems(selectedItems)
    }

    private val moshi = Moshi.Builder().build()
    val t = Types.newParameterizedType(MutableList::class.java, String::class.java)
    private val productsAdapter: JsonAdapter<List<String>>  = moshi.adapter(t)

    fun search(productName: String) {
        if (productName.isBlank()) {
            Toast.makeText(context, "Please enter a product name", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.progressBar.visibility = View.VISIBLE
        // Search for the product in the database
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("https://edamam-food-and-grocery-database.p.rapidapi.com/auto-complete?q=" + productName)
            .get()
            .addHeader("x-rapidapi-key", "1e63a6128amsh5b23db490d832a8p100981jsnb577c7d1471e")
            .addHeader("x-rapidapi-host", "edamam-food-and-grocery-database.p.rapidapi.com")
            .build()

        val response = client.newCall(request).enqueue(
            responseCallback = object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    Log.d("AddListFragment", "Failed to search for product", e)
                    requireActivity().runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Failed to search for product", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    val body = response.body?.source()
                    val products: List<String>? = productsAdapter.fromJson(body)
                    requireActivity().runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        if (products != null) {
                            searchResults.clear()
                            searchResults.addAll(products)
                            searchResultAdapter.updateItems(searchResults)
                            Log.d("AddListFragment", "Products found: $products")
                        } else {
                            Log.d("AddListFragment", "No products found")
                            Toast.makeText(requireContext(), "No products found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
    
    private fun uploadImage(onSuccess: (String) -> Unit, onFailure: () -> Unit) {
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
            Log.e("AddListFragment", "Failed to encode image", e)
            onFailure()
        }
    }
    
    fun saveList() {
        val userId = firebaseAuth.currentUser?.uid
        val name = binding.etListName.text.toString()
        if (userId == null) {
            Toast.makeText(context, "You must be logged in to create a list", Toast.LENGTH_SHORT).show()
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
                val user = firestore.collection("users").document(userId)

                val shoppingList = hashMapOf(
                    "name" to name,
                    "user" to user,
                    "imageBase64" to imageBase64,
                    "products" to selectedItems.map { item -> 
                        hashMapOf("name" to item.name, "quantity" to item.quantity)
                    },
                    "createdAt" to com.google.firebase.Timestamp.now()
                )

                firestore.collection("lists")
                    .add(shoppingList)
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(context, "Shopping list created!", Toast.LENGTH_SHORT).show()
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
            Log.e("AddListFragment", "Error launching camera", e)
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
            Log.e("AddListFragment", "Error launching gallery", e)
            Toast.makeText(requireContext(), "Error opening gallery: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}