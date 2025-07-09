package com.example.roamly.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.roamly.R
import com.example.roamly.data.models.Category

/**
 * Adapter personalizzato per visualizzare una lista di [Category]
 * all'interno di un `AutoCompleteTextView` o spinner.
 *
 * Ogni voce mostra il nome della categoria e la relativa icona.
 *
 * @param context Il contesto dell'applicazione.
 * @param categories Lista delle categorie da mostrare.
 */
class CategoryAdapter(
    context: Context,
    private val categories: List<Category>
) : ArrayAdapter<Category>(context, 0, categories) {

    /**
     * Restituisce la vista da mostrare nel campo selezionato.
     *
     * @param position La posizione dell'elemento nella lista.
     * @param convertView Vista riciclata (se disponibile).
     * @param parent Il ViewGroup genitore.
     * @return La vista dell'elemento renderizzato.
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromItem(categories[position], convertView, parent)
    }

    /**
     * Restituisce la vista da mostrare nel menu a tendina.
     *
     * @param position La posizione dell'elemento nel menu.
     * @param convertView Vista riciclata (se disponibile).
     * @param parent Il ViewGroup genitore.
     * @return La vista dell'elemento renderizzato.
     */
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromItem(categories[position], convertView, parent)
    }

    /**
     * Crea o ricicla una vista per rappresentare una categoria.
     *
     * @param item La categoria da visualizzare.
     * @param convertView Vista riutilizzabile, se presente.
     * @param parent Il contenitore genitore della vista.
     * @return La vista configurata con nome e icona della categoria.
     */
    private fun createViewFromItem(item: Category, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_category_dropdown, parent, false)

        val nameTextView = view.findViewById<TextView>(R.id.categoryNameTextView)
        val iconImageView = view.findViewById<ImageView>(R.id.categoryIconImageView)

        nameTextView.text = item.name
        iconImageView.setImageResource(item.iconResId)

        return view
    }
}