package com.example.roamly

import android.content.Context
import androidx.annotation.DrawableRes // Importa questa annotazione per chiarezza

data class CategoryItem(
    val name: String,
    val iconResId: Int
){
    // Sovrascrivi toString() per assicurarti che solo il nome venga visualizzato nel campo di testo del dropdown
    override fun toString(): String {
        return name
    }
}
