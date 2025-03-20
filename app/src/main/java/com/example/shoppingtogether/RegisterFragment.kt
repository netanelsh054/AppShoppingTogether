package com.example.shoppingtogether

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
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
import java.io.ByteArrayOutputStream


class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
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
            try {
                // Use ACTION_GET_CONTENT instead of ACTION_PICK for better compatibility
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "image/*"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                
                // Add these flags to avoid the Google Photos app issues
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                // Try to show a chooser with all available image sources
                val chooserIntent = Intent.createChooser(intent, "Select Image")
                getContent.launch(chooserIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error launching image picker", e)
                Toast.makeText(context, "Error opening image picker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
                        
                        // Process profile image if selected
                        if (selectedImageUri != null) {
                            convertImageToBase64(selectedImageUri!!) { base64Image ->
                                // Save user data to Firestore with image as Base64
                                saveUserToFirestore(user?.uid, name, email, base64Image)
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
    
    private fun convertImageToBase64(uri: Uri, callback: (String?) -> Unit) {
        try {
            // Get bitmap from Uri
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // Compress and convert to Base64
            val outputStream = ByteArrayOutputStream()
            // Compress to reduce size (adjust quality as needed)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)
            
            callback(base64Image)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode image", e)
            Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show()
            callback(null)
        }
    }

    private fun saveUserToFirestore(userId: String?, name: String, email: String, profileImageBase64: String?) {
        if (userId == null) {
            Log.e(TAG, "User ID is null, cannot save to Firestore")
            return
        }

        val user = hashMapOf(
            "userId" to userId,
            "name" to name,
            "email" to email,
            "profileImageBase64" to profileImageBase64,
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