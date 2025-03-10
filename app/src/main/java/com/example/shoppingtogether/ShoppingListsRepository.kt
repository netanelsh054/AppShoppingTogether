package com.example.shoppingtogether

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ShoppingListsRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun createList(list: ShoppingList): Result<Unit> {
        return try {
            db.collection("lists")
                .add(list)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

//    fun removeList(list: ShoppingList) {
//        shoppingLists.remove(list)
//    }
//
//    fun getLists(): List<ShoppingList> {
//        return shoppingLists
//    }
//
//    fun getListById(id: String): ShoppingList? {
//        return shoppingLists.find { it.id == id }
//    }
//
//    fun addProductToList(listId: String, product: Product) {
//        val list = shoppingLists.find { it.id == listId }
//        list?.products?.add(product)
//    }
//
//    fun removeProductFromList(listId: String, product: Product) {
//        val list = shoppingLists.find { it.id == listId }
//        list?.products?.remove(product)
//    }
//
//    fun getProductFromList(listId: String, productId: String): Product? {
//        val list = shoppingLists.find { it.id == listId }
//        return list?.products?.find { it.id == productId }
//    }
//
//    fun updateProductInList(listId: String, product: Product) {
//        val list = shoppingLists.find { it.id == listId }
//        val index = list?.products?.indexOfFirst { it.id == product.id }
//        if (index != null && index != -1) {
//            list.products[index] = product
//        }
//    }
}