package com.example.roamly.data.models

data class CategoryItem(
    val name: String,
    val iconResId: Int
){
    // Sovrascrivi toString() per assicurarti che solo il nome venga visualizzato nel campo di testo del dropdown
    override fun toString(): String {
        return name
    }
}
