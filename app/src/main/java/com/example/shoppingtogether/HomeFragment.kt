package com.example.shoppingtogether

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.shoppingtogether.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private lateinit var auth: FirebaseAuth
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User is not signed in, navigate to login
            findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
            return
        }

        // Setup toolbar
        binding.toolbar.title = "Shopping Together"

        // Update UI with user information
        binding.welcomeTextView.text = "Welcome back, ${currentUser.displayName ?: "User"}!"
        binding.textEmail.text = getString(R.string.user_email, currentUser.email)

        // Set up navigation via cards
        binding.newListCard.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_addListFragment)
        }

        binding.myListsCard.setOnClickListener {
            // This would navigate to a My Lists fragment once it's created
            // For now, just show a toast
            Toast.makeText(context, "My Lists feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.profileCard.setOnClickListener {
            // This would navigate to the Profile fragment
            findNavController().navigate(R.id.profileFragment)
        }

        // Set up logout button
        binding.buttonLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}