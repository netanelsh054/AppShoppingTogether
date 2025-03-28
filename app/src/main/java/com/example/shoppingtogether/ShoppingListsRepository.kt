package com.example.shoppingtogether

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ShoppingListsRepository {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "ShoppingListsRepository"

    // Get lists created by the current user
    fun getMyLists(userId: String): Flow<List<ShoppingList>> = callbackFlow {
        Log.d(TAG, "getMyLists: Fetching lists for user $userId")
        val listenerRegistration = db.collection("lists")
            .whereEqualTo("creatorId", userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting my lists", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                Log.d(TAG, "getMyLists: snapshot received, has documents: ${snapshot?.documents?.isNotEmpty()}")
                val lists = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data
                        Log.d(TAG, "getMyLists: doc id=${doc.id}, data=$data")
                        data?.let { ShoppingList(doc.id, it) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document to ShoppingList", e)
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "getMyLists: Processed ${lists.size} lists")
                if (lists.isEmpty()) {
                    Log.d(TAG, "getMyLists: No lists found")
                }
                trySend(lists)
            }

        awaitClose { listenerRegistration.remove() }
    }

    // Get lists shared with the current user
    fun getSharedLists(userId: String, userEmail: String): Flow<List<ShoppingList>> = callbackFlow {
        Log.d(TAG, "getSharedLists: Fetching lists shared with user email $userEmail")
        val listenerRegistration = db.collection("lists")
            .whereArrayContains("sharedWith", userEmail)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting shared lists", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                Log.d(TAG, "getSharedLists: snapshot received, has documents: ${snapshot?.documents?.isNotEmpty()}")
                val lists = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data
                        Log.d(TAG, "getSharedLists: doc id=${doc.id}, data=$data")
                        data?.let { ShoppingList(doc.id, it) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document to ShoppingList", e)
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "getSharedLists: Processed ${lists.size} lists")
                if (lists.isEmpty()) {
                    Log.d(TAG, "getSharedLists: No lists found")
                }
                trySend(lists)
            }

        awaitClose { listenerRegistration.remove() }
    }

    // Get public lists (excluding user's own lists)
    fun getPublicLists(userId: String): Flow<List<ShoppingList>> = callbackFlow {
        Log.d(TAG, "getPublicLists: Fetching public lists for user $userId")
        val listenerRegistration = db.collection("lists")
            .whereEqualTo("isPublic", true)
            .whereNotEqualTo("creatorId", userId)
            .orderBy("creatorId") // Required for the not equal query
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting public lists", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                Log.d(TAG, "getPublicLists: snapshot received, has documents: ${snapshot?.documents?.isNotEmpty()}")
                val lists = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data
                        Log.d(TAG, "getPublicLists: doc isPublic=${doc.data?.get("isPublic")}")
                        val rawIsPublic = doc.data?.get("isPublic")
                        
                        // Use the custom constructor instead
                        val list = data?.let { ShoppingList(doc.id, it) }
                        
                        // Log the result for debugging
                        Log.d(TAG, "getPublicLists: doc id=${doc.id}, rawIsPublic=$rawIsPublic, objectIsPublic=${list?.isPublic}")
                        
                        list
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document to ShoppingList", e)
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "getPublicLists: Processed ${lists.size} lists")
                if (lists.isEmpty()) {
                    Log.d(TAG, "getPublicLists: No lists found")
                }
                trySend(lists)
            }

        awaitClose { listenerRegistration.remove() }
    }

    // Create a new shopping list
    suspend fun createList(list: ShoppingList): Result<String> {
        return try {
            val docRef = db.collection("lists")
                .add(list)
                .await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating list", e)
            Result.failure(e)
        }
    }

    // Update an existing shopping list (only if user is creator)
    suspend fun updateList(list: ShoppingList): Result<Unit> {
        return try {
            db.collection("lists")
                .document(list.id)
                .set(list.copy(updatedAt = Timestamp.now()))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating list", e)
            Result.failure(e)
        }
    }

    // Delete a shopping list
    suspend fun deleteList(listId: String): Result<Unit> {
        return try {
            db.collection("lists")
                .document(listId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting list", e)
            Result.failure(e)
        }
    }

    // Update list sharing
    suspend fun updateListSharing(
        listId: String, 
        isPublic: Boolean, 
        sharedWith: List<String>
    ): Result<Unit> {
        return try {
            db.collection("lists")
                .document(listId)
                .update(
                    mapOf(
                        "isPublic" to isPublic,
                        "sharedWith" to sharedWith,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating list sharing", e)
            Result.failure(e)
        }
    }

    // Toggle item checked status
    suspend fun toggleItemChecked(
        listId: String,
        productIndex: Int,
        isChecked: Boolean
    ): Result<Unit> {
        return try {
            // First get the current list
            val listDoc = db.collection("lists").document(listId).get().await()
            val list = listDoc.toObject(ShoppingList::class.java) ?: 
                return Result.failure(Exception("List not found"))
            
            // Create updated products list
            val updatedProducts = list.products.toMutableList()
            if (productIndex < updatedProducts.size) {
                val currentItem = updatedProducts[productIndex]
                updatedProducts[productIndex] = currentItem.copy(checked = isChecked)
                
                // Update the document
                db.collection("lists")
                    .document(listId)
                    .update(
                        mapOf(
                            "products" to updatedProducts,
                            "updatedAt" to Timestamp.now()
                        )
                    )
                    .await()
                
                Result.success(Unit)
            } else {
                Result.failure(Exception("Product index out of bounds"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling item checked status", e)
            Result.failure(e)
        }
    }
}