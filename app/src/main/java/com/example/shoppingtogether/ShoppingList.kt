package com.example.shoppingtogether

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference

data class ShoppingList(
    val id: String = "",
    val name: String = "",
    val imageBase64: String? = null,
    val creatorId: String = "",
    val creatorName: String = "",
    val isPublic: Boolean = false,
    val sharedWith: List<String> = emptyList(),
    val products: List<ShoppingListItem> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    // Custom constructor for Firestore document data
    constructor(id: String, data: Map<String, Any?>) : this(
        id = id,
        name = data["name"] as? String ?: "",
        imageBase64 = data["imageBase64"] as? String,
        creatorId = data["creatorId"] as? String ?: "",
        creatorName = data["creatorName"] as? String ?: "",
        // Handle various possible types for isPublic
        isPublic = when (val value = data["isPublic"]) {
            is Boolean -> value
            is String -> value.lowercase() == "true"
            is Number -> value.toInt() != 0
            else -> false
        },
        sharedWith = (data["sharedWith"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        products = (data["products"] as? List<*>)?.mapNotNull { productData ->
            if (productData is Map<*, *>) {
                ShoppingListItem(
                    name = (productData["name"] as? String) ?: "",
                    quantity = (productData["quantity"] as? Number)?.toInt() ?: 1,
                    checked = (productData["checked"] as? Boolean) ?: false
                )
            } else null
        } ?: emptyList(),
        createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now(),
        updatedAt = data["updatedAt"] as? Timestamp ?: Timestamp.now()
    )
}

data class ShoppingListItem(
    val name: String = "",
    var quantity: Int = 1,
    var checked: Boolean = false
)