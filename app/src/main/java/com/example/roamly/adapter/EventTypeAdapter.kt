package com.example.roamly.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.roamly.R
import com.example.roamly.data.utils.EventTypeProvider.EventTypeOption

class EventTypeAdapter(
    context: Context,
    private val items: List<EventTypeOption>
) : ArrayAdapter<EventTypeOption>(context, 0, items) {

    private val inflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    private fun createItemView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = items[position]
        val view = convertView ?: inflater.inflate(R.layout.dropdown_item_icon_text, parent, false)

        view.findViewById<TextView>(R.id.itemTextView).text = context.getString(item.labelResId)
        view.findViewById<ImageView>(R.id.itemIconView).setImageResource(item.iconResId)

        return view
    }
}
