package com.example.shoppingtogether

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.navigateUp
import com.example.shoppingtogether.databinding.FragmentRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage


class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var selectedImageUri: Uri? = null

    private val binding get() = _binding!!

    private var TAG = "RegisterFragment"

    // ActivityResultLauncher for image selection
    private val getContent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                binding.ivProfileImage.setImageURI(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
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

        val nameEditText = binding.etName
        val emailEditText = binding.etEmail
        val passwordEditText = binding.etPassword
        val confirmPasswordEditText = binding.etConfirmPassword
        val registerButton = binding.btnRegister
        val loginLink = binding.tvLoginLink
        val selectImageButton = binding.btnSelectImage

        // Set up image selection
        selectImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            getContent.launch(intent)
        }

        registerButton.setOnClickListener() {
            val name = nameEditText.text.toString()
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(context, "Please fill out all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEditText.error = "Please enter a valid email address"
                return@setOnClickListener
            }

            if (password.length < 6) {
                passwordEditText.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading indicator
            binding.btnRegister.isEnabled = false
            binding.btnRegister.text = getString(R.string.registering)

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity()) { task ->
                    if (task.isSuccessful) {
                        // Sign up success, update UI with the signed-in user's information
                        val user = auth.currentUser
                        
                        // Upload profile image if selected
                        if (selectedImageUri != null) {
                            val profileImagesRef = storage.reference.child("profile_images/${user?.uid}")
                            
                            profileImagesRef.putFile(selectedImageUri!!)
                                .addOnSuccessListener { taskSnapshot ->
                                    // Get the download URL
                                    profileImagesRef.downloadUrl.addOnSuccessListener { uri ->
                                        // Save user data to Firestore with image URL
                                        saveUserToFirestore(user?.uid, name, email, uri.toString())
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Failed to upload profile image", e)
                                    // Save user data without image
                                    saveUserToFirestore(user?.uid, name, email, null)
                                }
                        } else {
                            // Save user data without image
                            saveUserToFirestore(user?.uid, name, email, null)
                        }
                    } else {
                        // If sign up fails, display a message to the user.
                        binding.btnRegister.isEnabled = true
                        binding.btnRegister.text = getString(R.string.action_register)
                        
                        val errorMessage = when (task.exception) {
                            is FirebaseAuthUserCollisionException -> "Email already in use. Please use a different email or login."
                            is FirebaseAuthWeakPasswordException -> "Password is too weak. Please use a stronger password."
                            else -> "Registration failed: ${task.exception?.message}"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }

        loginLink.setOnClickListener() {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }
    }

    private fun saveUserToFirestore(userId: String?, name: String, email: String, profileImageUrl: String?) {
        if (userId == null) {
            Log.e(TAG, "User ID is null, cannot save to Firestore")
            return
        }

        val user = hashMapOf(
            "userId" to userId,
            "name" to name,
            "email" to email,
            "profileImageUrl" to profileImageUrl,
            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("users").document(userId)
            .set(user)
            .addOnSuccessListener {
                Log.d(TAG, "User data saved to Firestore")
                Toast.makeText(context, "Registration completed successfully!", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_registerFragment_to_homeFragment)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving user data to Firestore", e)
                Toast.makeText(context, "Error saving user data: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnRegister.isEnabled = true
                binding.btnRegister.text = getString(R.string.action_register)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}