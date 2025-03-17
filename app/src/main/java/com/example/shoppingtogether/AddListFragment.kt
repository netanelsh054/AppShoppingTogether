package com.example.shoppingtogether

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException


class AddListFragment : Fragment() {

    private var _binding: FragmentAddListBinding? = null
    private val binding get() = _binding!!

    private var searchResults = mutableListOf<String>()
    private var selectedItems = mutableListOf<ShoppingListItem>()

    private lateinit var searchResultAdapter: SearchResultAdapter
    private lateinit var selectedItemsAdapter: SelectedItemsAdapter

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

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
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

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
        // Search for the product in the database
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("https://edamam-food-and-grocery-database.p.rapidapi.com/auto-complete?q=" + productName)
            .get()
            .addHeader("x-rapidapi-key", "1e63a6128amsh5b23db490d832a8p100981jsnb577c7d1471e")
            .addHeader("x-rapidapi-host", "edamam-food-and-grocery-database.p.rapidapi.com")
            .build()

        val response = client.newCall(request).enqueue(
            responseCallback = object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d("AddListFragment", "Failed to search for product", e)
                    Toast.makeText(requireContext(), "Failed to search for product", Toast.LENGTH_LONG).show()
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.source()
                    val products: List<String>? = productsAdapter.fromJson(body)
                    requireActivity().runOnUiThread {
                        if (products != null) {
                            searchResults.clear()
                            searchResults.addAll(products)
                            searchResultAdapter.updateItems(searchResults)
                            Log.d("AddListFragment", "Products found: $products")
                        } else {
                            Log.d("AddListFragment", "No products found")
                        }
                    }
                }
            }
        )
    }
    
    fun saveList() {
        val userId = firebaseAuth.currentUser?.uid
        val name = binding.etListName.text.toString()
        if (userId == null) {
            Toast.makeText(context, "You must be logged in to create a list", Toast.LENGTH_SHORT).show()
            return
        }
        if (name == "") {
            Toast.makeText(context, "Enter a name for this list!", Toast.LENGTH_LONG).show()
            return
        }
        if (selectedItems.size == 0) {
            Toast.makeText(context, "The list is empty!", Toast.LENGTH_LONG).show()
            return
        }

        val user = firestore.collection("users").document(userId)

        val shoppingList = ShoppingList(
            name = name,
            user = user,
            products = selectedItems
        )

        firestore.collection("lists")
            .add(shoppingList)
            .addOnSuccessListener {
                Toast.makeText(context, "Shopping list created!", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}