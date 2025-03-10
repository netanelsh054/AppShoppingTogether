package com.example.shoppingtogether

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.shoppingtogether.databinding.FragmentAddListBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException


class AddListFragment : Fragment() {

    private var _binding: FragmentAddListBinding? = null
    private val binding get() = _binding!!

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
        binding.btnSearch.setOnClickListener {
            search(binding.etProductSearch.text.toString())
        }
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
                    if (products != null) {
                        Log.d("AddListFragment", "Products found: $products")
                    } else {
                        Log.d("AddListFragment", "No products found")
                    }
                }
            }
        )
    }
}