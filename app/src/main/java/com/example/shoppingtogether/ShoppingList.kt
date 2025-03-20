package com.example.shoppingtogether

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference

data class ShoppingList(
    val id: String = "",
    val name: String = "",
    val imageUrl: String? = null,
    val user: DocumentReference? = null,
    val products: List<ShoppingListItem> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)

data class ShoppingListItem(
    val name: String = "",
    var quantity: Int = 1
)