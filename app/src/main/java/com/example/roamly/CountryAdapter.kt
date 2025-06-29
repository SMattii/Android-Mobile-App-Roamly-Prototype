package com.example.roamly

import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class CountryAdapter(
    context: Context,
    private val countries: List<Country>
) : ArrayAdapter<Country>(context, 0, countries) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

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