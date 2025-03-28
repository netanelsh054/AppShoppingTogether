package com.example.shoppingtogether

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.google.firebase.auth.FirebaseAuth

class HomeViewModel : ViewModel() {
    private val repository = ShoppingListsRepository()
    private val TAG = "HomeViewModel"

    // LiveData for all lists combined and sorted
    private val _allLists = MutableLiveData<List<ShoppingList>>(emptyList())
    val allLists: LiveData<List<ShoppingList>> = _allLists

    // LiveData for loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData for error messages
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    // Load all lists for a user
    fun loadLists(userId: String) {
        Log.d(TAG, "loadLists: Starting to load lists for user $userId")
        _isLoading.value = true
        
        // Get the current user's email from Firebase Auth
        val userEmail = FirebaseAuth.getInstance().currentUser?.email
        
        // Create a list to store all lists
        val allLists = mutableListOf<ShoppingList>()
        
        // Load user's own lists
        repository.getMyLists(userId)
            .onEach { lists ->
                Log.d(TAG, "loadLists: Received ${lists.size} my lists from repository")
                allLists.addAll(lists)
                updateCombinedLists(allLists)
            }
            .catch { e ->
                Log.e(TAG, "Error loading my lists", e)
                _errorMessage.value = "Failed to load your lists: ${e.message}"
            }
            .launchIn(viewModelScope)
        
        // Load lists shared with the user - handle null email case
        if (userEmail != null) {
            repository.getSharedLists(userId, userEmail)
                .onEach { lists ->
                    Log.d(TAG, "loadLists: Received ${lists.size} shared lists from repository")
                    allLists.addAll(lists)
                    updateCombinedLists(allLists)
                }
                .catch { e ->
                    Log.e(TAG, "Error loading shared lists", e)
                    _errorMessage.value = "Failed to load shared lists: ${e.message}"
                }
                .launchIn(viewModelScope)
        } else {
            Log.w(TAG, "User email is null, cannot load shared lists")
        }
        
        // Load public lists
        repository.getPublicLists(userId)
            .onEach { lists ->
                Log.d(TAG, "loadLists: Received ${lists.size} public lists from repository")
                allLists.addAll(lists)
                updateCombinedLists(allLists)
            }
            .catch { e ->
                Log.e(TAG, "Error loading public lists", e)
                _errorMessage.value = "Failed to load public lists: ${e.message}"
            }
            .launchIn(viewModelScope)
        
        Log.d(TAG, "loadLists: Initiated all list loading flows")
    }

    private fun updateCombinedLists(lists: List<ShoppingList>) {
        // Sort lists by last update time (newest first)
        val sortedLists = lists.sortedByDescending { it.updatedAt }
        _allLists.value = sortedLists
        _isLoading.value = false
    }

    // Clear error message
    fun clearError() {
        _errorMessage.value = null
    }
} 