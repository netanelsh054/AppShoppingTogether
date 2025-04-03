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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.shoppingtogether.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var imageUri: Uri? = null
    private var initialImageUrl: String? = null
    private lateinit var currentPhotoPath: String

    private val getImageFromGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                imageUri = uri
                try {
                    // Simple image loading without Glide
                    binding.profileImage.setImageURI(uri)
                } catch (e: Exception) {
                    Log.e("ProfileFragment", "Error loading image", e)
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
                binding.profileImage.setImageURI(imageUri)
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error loading camera image", e)
                Toast.makeText(requireContext(), "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val TAG = "ProfileFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        // Setup toolbar
        binding.toolbar.title = "Profile"
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Setup logout button
        binding.btnLogout.setOnClickListener {
            Log.d(TAG, "Logout button clicked")
            auth.signOut()
            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
            // Clear back stack and navigate to login
            findNavController().navigate(R.id.loginFragment, null, 
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(findNavController().graph.startDestinationId, true)
                    .build()
            )
        }

        // Check if user is logged in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            findNavController().navigate(R.id.loginFragment, null, 
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(findNavController().graph.startDestinationId, true)
                    .build()
            )
            return
        }

        // Setup UI with user data
        loadUserProfile()

        // Setup image selection
        binding.btnChangeImage.setOnClickListener {
            showImageSelectionDialog()
        }

        // Setup save button
        binding.btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }
    }
    
    private fun showImageSelectionDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Profile Picture")
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
            Log.e("ProfileFragment", "Error launching camera", e)
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
            Log.e("ProfileFragment", "Error launching gallery", e)
            Toast.makeText(requireContext(), "Error opening gallery: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserProfile() {
        val user = auth.currentUser ?: return

        // Load basic info from auth
        binding.textViewEmail.text = user.email
        binding.editTextName.setText(user.displayName)

        // Show loading indicator
        binding.progressBar.visibility = View.VISIBLE
        
        // Load profile data from Firestore
        val userId = user.uid
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userData = document.data
                    
                    // If name is not set in Auth but exists in Firestore
                    if (binding.editTextName.text.toString().isBlank() && userData?.get("name") != null) {
                        binding.editTextName.setText(userData["name"].toString())
                    }
                    
                    // Load profile image if exists
                    initialImageUrl = userData?.get("profileImageBase64") as? String
                    if (!initialImageUrl.isNullOrBlank()) {
                        try {
                            // Decode Base64 string to bitmap
                            val imageBytes = Base64.decode(initialImageUrl, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            binding.profileImage.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            Log.e("ProfileFragment", "Error loading profile image", e)
                        }
                    }
                }
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e("ProfileFragment", "Error loading user data", e)
                Toast.makeText(requireContext(), "Failed to load profile data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserProfile() {
        val user = auth.currentUser ?: return
        val newName = binding.editTextName.text.toString().trim()
        
        if (newName.isBlank()) {
            Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        // Only upload image if a new one was selected
        if (imageUri != null) {
            uploadProfileImage { imageBase64 ->
                updateUserProfile(user, newName, imageBase64)
            }
        } else {
            // No new image, just update the profile with existing image
            updateUserProfile(user, newName, initialImageUrl)
        }
    }

    private fun uploadProfileImage(onComplete: (String?) -> Unit) {
        val imageUri = this.imageUri ?: return onComplete(null)
        
        try {
            // Get bitmap from Uri
            val inputStream = requireContext().contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // Compress and convert to Base64
            val outputStream = ByteArrayOutputStream()
            // Compress to reduce size - adjust quality as needed (0-100)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)
            
            // Base64 string is ready to be stored in Firestore
            onComplete(base64Image)
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Failed to encode image", e)
            binding.progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show()
            onComplete(null)
        }
    }

    private fun updateUserProfile(user: com.google.firebase.auth.FirebaseUser, newName: String, imageBase64: String?) {
        // Update Auth profile
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(newName)
            .build()

        user.updateProfile(profileUpdates)
            .addOnSuccessListener {
                // Update Firestore profile
                val userId = user.uid
                val userData = hashMapOf<String, Any>(
                    "name" to newName
                )
                
                // Add image if available
                if (imageBase64 != null) {
                    userData["profileImageBase64"] = imageBase64
                }
                
                firestore.collection("users").document(userId)
                    .set(userData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.homeFragment, null,
                            androidx.navigation.NavOptions.Builder()
                                .setPopUpTo(R.id.nav_graph, true)
                                .build()
                        )
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        Log.e("ProfileFragment", "Error updating Firestore profile", e)
                        Toast.makeText(requireContext(), "Failed to update profile in database", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e("ProfileFragment", "Error updating Auth profile", e)
                Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}