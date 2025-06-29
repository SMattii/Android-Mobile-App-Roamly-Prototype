package com.example.roamly.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.roamly.R
import com.example.roamly.Language

class LanguageAdapter(
    context: Context,
    private val languages: MutableList<Language>
) : ArrayAdapter<Language>(context, 0, languages) {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

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
        holder.languageFlag.setImageResource(language?.flagResId ?: 0)

        return view
    }

    private class ViewHolder(view: View) {
        val languageName: TextView = view.findViewById(R.id.itemTextView)
        val languageFlag: ImageView = view.findViewById(R.id.itemIconView)
    }

    fun updateLanguages(newLanguages: List<Language>) {
        clear()
        addAll(newLanguages)
        notifyDataSetChanged()
    }
}