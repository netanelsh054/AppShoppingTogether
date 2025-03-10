package com.example.shoppingtogether

data class Product (val name: String)

data class ShoppingListItem(
    val name: String,
    val quantity: Int
)

data class ShoppingList(
    val id: String,
    val name: String,
    val products: MutableList<ShoppingListItem> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis()
)