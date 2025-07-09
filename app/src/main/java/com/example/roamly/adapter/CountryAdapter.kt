package com.example.roamly.adapter

import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.roamly.R
import com.example.roamly.data.models.Country

/**
 * Adapter personalizzato per visualizzare una lista di [Country] all'interno
 * di un `AutoCompleteTextView` o spinner, con nome del paese e bandiera.
 *
 * L'adapter cerca dinamicamente la risorsa drawable della bandiera
 * basandosi sul codice ISO del paese (`country.code.lowercase()`).
 *
 * @param context Il contesto dell'app.
 * @param countries La lista di paesi da mostrare.
 */
class CountryAdapter(
    context: Context,
    private val countries: List<Country>
) : ArrayAdapter<Country>(context, 0, countries) {

    /**
     * Restituisce la vista mostrata quando un elemento è selezionato.
     *
     * @param position Posizione dell'elemento selezionato.
     * @param convertView Vista riciclata (se presente).
     * @param parent ViewGroup contenitore.
     * @return Vista rappresentativa dell'elemento selezionato.
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    /**
     * Restituisce la vista mostrata nella lista a tendina.
     *
     * @param position Posizione dell'elemento nella dropdown.
     * @param convertView Vista riciclata (se presente).
     * @param parent ViewGroup contenitore.
     * @return Vista rappresentativa dell'elemento nella dropdown.
     */
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    /**
     * Crea (o ricicla) una vista per un elemento della lista, impostando
     * il nome del paese e la bandiera corrispondente.
     *
     * Se la bandiera non viene trovata, viene mostrata una placeholder.
     *
     * @param position Posizione dell'elemento nella lista.
     * @param convertView Vista riutilizzabile.
     * @param parent ViewGroup contenitore.
     * @return La vista completa per il paese specificato.
     */
    private fun createView(position: Int, convertView: View?, parent: ViewGroup): View {
        val country = countries[position]
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_country_dropdown, parent, false)

        val nameTextView = view.findViewById<TextView>(R.id.countryNameTextView)
        val flagImageView = view.findViewById<ImageView>(R.id.flagImageView)

        nameTextView.text = country.name

        val flagResId = context.resources.getIdentifier(
            country.code.lowercase(), "drawable", context.packageName
        )

        try {
            if (flagResId != 0) {
                flagImageView.setImageResource(flagResId)
            } else {
                flagImageView.setImageResource(R.drawable.flag_placeholder)
            }
        } catch (e: Resources.NotFoundException) {
            flagImageView.setImageResource(R.drawable.flag_placeholder)
        }

        return view
    }
}