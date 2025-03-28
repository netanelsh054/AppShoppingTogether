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

    // LiveData for my lists
    private val _myLists = MutableLiveData<List<ShoppingList>>(emptyList())
    val myLists: LiveData<List<ShoppingList>> = _myLists

    // LiveData for shared lists
    private val _sharedLists = MutableLiveData<List<ShoppingList>>(emptyList())
    val sharedLists: LiveData<List<ShoppingList>> = _sharedLists

    // LiveData for public lists
    private val _publicLists = MutableLiveData<List<ShoppingList>>(emptyList())
    val publicLists: LiveData<List<ShoppingList>> = _publicLists

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
        
        // Load user's own lists
        repository.getMyLists(userId)
            .onEach { lists ->
                Log.d(TAG, "loadLists: Received ${lists.size} my lists from repository")
                for (list in lists) {
                    Log.d(TAG, "My list: id=${list.id}, name=${list.name}, products=${list.products.size}")
                }
                _myLists.value = lists
                _isLoading.value = false
            }
            .catch { e ->
                Log.e(TAG, "Error loading my lists", e)
                _errorMessage.value = "Failed to load your lists: ${e.message}"
                _isLoading.value = false
            }
            .launchIn(viewModelScope)
        
        // Load lists shared with the user - handle null email case
        if (userEmail != null) {
            repository.getSharedLists(userId, userEmail)
                .onEach { lists ->
                    Log.d(TAG, "loadLists: Received ${lists.size} shared lists from repository")
                    for (list in lists) {
                        Log.d(TAG, "Shared list: id=${list.id}, name=${list.name}, creator=${list.creatorName}")
                    }
                    _sharedLists.value = lists
                    _isLoading.value = false
                }
                .catch { e ->
                    Log.e(TAG, "Error loading shared lists", e)
                    _errorMessage.value = "Failed to load shared lists: ${e.message}"
                    _isLoading.value = false
                }
                .launchIn(viewModelScope)
        } else {
            Log.w(TAG, "User email is null, cannot load shared lists")
            _sharedLists.value = emptyList()
        }
        
        // Load public lists
        repository.getPublicLists(userId)
            .onEach { lists ->
                Log.d(TAG, "loadLists: Received ${lists.size} public lists from repository")
                for (list in lists) {
                    Log.d(TAG, "Public list: id=${list.id}, name=${list.name}, creator=${list.creatorName}")
                }
                _publicLists.value = lists
                _isLoading.value = false
            }
            .catch { e ->
                Log.e(TAG, "Error loading public lists", e)
                _errorMessage.value = "Failed to load public lists: ${e.message}"
                _isLoading.value = false
            }
            .launchIn(viewModelScope)
        
        Log.d(TAG, "loadLists: Initiated all three list loading flows")
    }

    // Clear error message
    fun clearError() {
        _errorMessage.value = null
    }
} 