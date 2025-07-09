package com.example.roamly.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.roamly.R
import com.example.roamly.data.models.Interest

/**
 * Adapter personalizzato per mostrare una lista di interessi in un `AutoCompleteTextView` o dropdown,
 * ciascuno associato a un'icona.
 *
 * Ogni elemento è rappresentato da un `Pair<Interest, Int>`:
 * - il primo elemento è il modello [Interest]
 * - il secondo è il resource ID della relativa icona
 *
 * @param context Il contesto dell'applicazione.
 * @param interests Lista mutabile di interessi con icona da visualizzare.
 */
class InterestAdapter(
    context: Context,
    private val interests: MutableList<Pair<Interest, Int>> // accoppiati a icona
) : ArrayAdapter<Pair<Interest, Int>>(context, 0, interests) {

    private val inflater = LayoutInflater.from(context)

    /**
     * Restituisce la vista dell’elemento selezionato.
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    /**
     * Restituisce la vista dell’elemento nella lista a discesa.
     */
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    /**
     * Crea la vista per un item della lista dropdown, con testo e icona.
     *
     * @param position Posizione dell'elemento.
     * @param convertView Vista riutilizzabile (se disponibile).
     * @param parent Il ViewGroup contenitore.
     * @return La vista dell’elemento.
     */
    private fun createItemView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (interest, iconResId) = getItem(position)!!
        val view = convertView ?: inflater.inflate(R.layout.dropdown_item_icon_text, parent, false)

        view.findViewById<TextView>(R.id.itemTextView).text = interest.name
        view.findViewById<ImageView>(R.id.itemIconView).setImageResource(iconResId)

        return view
    }
}