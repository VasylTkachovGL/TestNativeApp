package com.example.testnativeapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

/*
 * @author Tkachov Vasyl
 * @since 02.09.2021
 */
class CustomSpinnerAdapter internal constructor(context: Context, var items: List<Int>) :
    ArrayAdapter<Int?>(context, R.layout.item_dropdown_list, R.id.title_view, items) {

    private var inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (view == null) {
            view = inflater.inflate(R.layout.item_dropdown_list, parent, false)
            return view
        }
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val item = items[position]
        val holder: ItemViewHolder
        if (view == null) {
            view = inflater.inflate(R.layout.item_dropdown_list, parent, false)
            holder = ItemViewHolder(view)
            view.tag = holder
        } else {
            holder = view.tag as ItemViewHolder
        }
        holder.title.text = item.toString()
        return view!!
    }

    internal class ItemViewHolder(view: View) {
        var title: TextView = view.findViewById(R.id.title_view)
    }

}