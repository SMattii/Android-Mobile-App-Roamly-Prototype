package com.example.roamly

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.roamly.data.models.CategoryItem

class CategoryAdapter(
    context: Context,
    private val categories: List<CategoryItem>
) : ArrayAdapter<CategoryItem>(context, 0, categories) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromItem(categories[position], convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromItem(categories[position], convertView, parent)
    }

    private fun createViewFromItem(item: CategoryItem, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_category_dropdown, parent, false)

        val nameTextView = view.findViewById<TextView>(R.id.categoryNameTextView)
        val iconImageView = view.findViewById<ImageView>(R.id.categoryIconImageView)

        nameTextView.text = item.name
        iconImageView.setImageResource(item.iconResId)

        return view
    }
}