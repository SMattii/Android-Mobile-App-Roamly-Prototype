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

class InterestAdapter(
    context: Context,
    private val interests: MutableList<Pair<Interest, Int>> // accoppiati a icona
) : ArrayAdapter<Pair<Interest, Int>>(context, 0, interests) {

    private val inflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    private fun createItemView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (interest, iconResId) = getItem(position)!!
        val view = convertView ?: inflater.inflate(R.layout.dropdown_item_icon_text, parent, false)

        view.findViewById<TextView>(R.id.itemTextView).text = interest.name
        view.findViewById<ImageView>(R.id.itemIconView).setImageResource(iconResId)

        return view
    }

    fun updateInterests(newList: List<Pair<Interest, Int>>) {
        interests.clear()
        interests.addAll(newList)
        notifyDataSetChanged()
    }
}