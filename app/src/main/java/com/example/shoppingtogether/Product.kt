package com.example.shoppingtogether

import com.google.firebase.firestore.DocumentReference

data class Product (val name: String)

data class ShoppingListItem(
    val name: String,
    var quantity: Int
)

data class ShoppingList(
    val name: String,
    val products: MutableList<ShoppingListItem> = mutableListOf(),
    val user: DocumentReference,
    val createdAt: Long = System.currentTimeMillis()
)