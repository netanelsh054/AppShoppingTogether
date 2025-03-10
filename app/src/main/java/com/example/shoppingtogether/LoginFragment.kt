package com.example.shoppingtogether

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.shoppingtogether.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException


class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private lateinit var auth: FirebaseAuth

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Check if user is already signed in
        if (auth.currentUser != null) {
            // User is already signed in, navigate to home
            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
            return
        }

        val emailEditText = binding.etEmail
        val passwordEditText = binding.etPassword
        val loginButton = binding.btnLogin
        val registerLink = binding.tvRegisterLink

        passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                performLogin(emailEditText.text.toString(), passwordEditText.text.toString())
                true
            }
            false
        }

        loginButton.setOnClickListener {
            performLogin(
                emailEditText.text.toString(),
                passwordEditText.text.toString()
            )
        }

        registerLink.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
    }

    private fun performLogin(email: String, password: String) {
        binding.loading.visibility = View.VISIBLE
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Please fill out all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email address"
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters long"
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                binding.loading.visibility = View.GONE

                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    val user = auth.currentUser
                    Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                } else {
                    // If sign in fails, display a message to the user.
                    val errorMessage = when (task.exception) {
                        is FirebaseAuthInvalidUserException -> "User not found. Please check your email or register."
                        is FirebaseAuthInvalidCredentialsException -> "Invalid password. Please try again."
                        else -> "Authentication failed: ${task.exception?.message}"
                    }
                    Log.d("LoginFragment", errorMessage);
                    Toast.makeText(context, "Authentication failed. Please try again.", Toast.LENGTH_LONG).show();
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}