package com.example.shoppingtogether

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.shoppingtogether.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private lateinit var auth: FirebaseAuth
    private val binding get() = _binding!!
    
    private lateinit var viewModel: HomeViewModel
    private lateinit var listsAdapter: ShoppingListAdapter
    
    private val TAG = "HomeFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User is not signed in, navigate to login
            Log.d(TAG, "onViewCreated: No current user, navigating to login")
            findNavController().navigate(R.id.loginFragment, null, 
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build()
            )
            return
        }
        
        // Ensure we have user email for shared lists
        val userEmail = currentUser.email
        if (userEmail == null) {
            Log.w(TAG, "User has no email address - shared lists will not be available")
            Toast.makeText(context, "Warning: Your account has no email address. Shared lists will not be available.", Toast.LENGTH_LONG).show()
        }
        
        Log.d(TAG, "onViewCreated: User logged in: ${currentUser.uid}, ${currentUser.email}")

        // Setup toolbar
        binding.toolbar.title = "Shopping Together"
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_logout -> {
                    Log.d(TAG, "Logout menu item clicked")
                    auth.signOut()
                    Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.loginFragment, null, 
                        androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                    )
                    true
                }
                else -> false
            }
        }

        // Update UI with user information
        binding.welcomeTextView.text = "Welcome back, ${currentUser.displayName ?: "User"}!"
        binding.textEmail.text = getString(R.string.user_email, currentUser.email)

        // Setup RecyclerView
        setupRecyclerView()
        
        // Setup SwipeRefreshLayout
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "onRefresh: Manually refreshing lists")
            viewModel.loadLists(currentUser.uid)
        }

        // Setup FAB for adding new list
        binding.fabAddList.setOnClickListener {
            Log.d(TAG, "fabAddList clicked, navigating to add list")
            findNavController().navigate(R.id.action_homeFragment_to_addListFragment)
        }
        
        // Observe ViewModel data
        observeViewModel()
        
        // Load lists
        Log.d(TAG, "onViewCreated: Initial load of lists for user ${currentUser.uid}, email: ${currentUser.email}")
        showLoading(true)
        viewModel.loadLists(currentUser.uid)

    }
    
    private fun setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView: Setting up RecyclerView")
        
        listsAdapter = ShoppingListAdapter { list ->
            navigateToListDetail(list)
        }
        binding.listsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = listsAdapter
        }
        
        Log.d(TAG, "setupRecyclerView: RecyclerView initialized")
    }
    
    private fun observeViewModel() {
        Log.d(TAG, "observeViewModel: Setting up observers")
        
        // Observe all lists
        viewModel.allLists.observe(viewLifecycleOwner) { lists ->
            Log.d(TAG, "allLists observer: Received ${lists.size} lists")
            listsAdapter.updateLists(lists)
            
            if (lists.isEmpty()) {
                Log.d(TAG, "allLists observer: No lists, showing empty view")
                binding.emptyText.visibility = View.VISIBLE
                binding.listsRecyclerView.visibility = View.GONE
            } else {
                Log.d(TAG, "allLists observer: ${lists.size} lists, showing RecyclerView")
                binding.emptyText.visibility = View.GONE
                binding.listsRecyclerView.visibility = View.VISIBLE
            }
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "isLoading observer: Loading state = $isLoading")
            showLoading(isLoading)
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Log.e(TAG, "Error message received: $it")
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.swipeRefreshLayout.isRefreshing = isLoading
    }
    
    private fun navigateToListDetail(list: ShoppingList) {
        Log.d(TAG, "navigateToListDetail: Clicked on list ${list.id}, name=${list.name}")
        val currentUser = auth.currentUser
        
        if (currentUser != null && currentUser.uid == list.creatorId) {
            // User is the owner, navigate to edit fragment
            val action = HomeFragmentDirections.actionHomeFragmentToEditListFragment(list.id)
            findNavController().navigate(action)
        } else {
            // User is not the owner, navigate to view fragment
            val action = HomeFragmentDirections.actionHomeFragmentToViewListFragment(list.id)
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}