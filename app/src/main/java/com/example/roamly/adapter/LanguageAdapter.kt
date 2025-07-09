package com.example.roamly.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.roamly.R
import com.example.roamly.data.models.Language

/**
 * Adapter personalizzato per visualizzare una lista di lingue con bandiere
 * all'interno di un `AutoCompleteTextView` o dropdown.
 *
 * Ogni voce mostra il nome della lingua e la relativa icona di bandiera, ottenuta
 * dinamicamente tramite `Language.getFlagResId(context)`.
 *
 * @param context Il contesto dell'app.
 * @param languages Lista iniziale delle lingue da visualizzare.
 */
class LanguageAdapter(
    context: Context,
    private val languages: MutableList<Language>
) : ArrayAdapter<Language>(context, 0, languages) {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    /**
     * Restituisce la vista da mostrare nel campo selezionato.
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    /**
     * Restituisce la vista da mostrare nella lista a discesa.
     */
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    /**
     * Crea (o ricicla) una vista per un item, includendo nome e bandiera della lingua.
     *
     * @param position Posizione dell’elemento nella lista.
     * @param convertView Vista riutilizzabile, se disponibile.
     * @param parent ViewGroup contenitore.
     * @return La vista dell’elemento formattata.
     */
    private fun createItemView(position: Int, convertView: View?, parent: ViewGroup): View {
        val language = getItem(position)
        val view: View

        val holder: ViewHolder
        if (convertView == null) {
            view = inflater.inflate(R.layout.dropdown_language_with_icon, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }
        holder.languageName.text = language?.name

        language?.let {
            val resId = it.getFlagResId(context)
            if (resId != 0) {
                holder.languageFlag.setImageResource(resId)
            } else {
                holder.languageFlag.setImageResource(R.drawable.ic_flag_default)
            }
        } ?: holder.languageFlag.setImageDrawable(null)
        return view
    }

    /**
     * ViewHolder interno per ottimizzare il rendering della lista.
     *
     * @param view La vista da cui recuperare i riferimenti.
     */
    private class ViewHolder(view: View) {
        val languageName: TextView = view.findViewById(R.id.itemTextView)
        val languageFlag: ImageView = view.findViewById(R.id.itemIconView)
    }

    /**
     * Aggiorna dinamicamente la lista di lingue mostrate nel dropdown.
     *
     * @param newLanguages Nuova lista da visualizzare.
     */
    fun updateLanguages(newLanguages: List<Language>) {
        clear()
        addAll(newLanguages)
        notifyDataSetChanged()
    }
}