package com.userexec.soneme.sync

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

data class RowModel(val id: Long, val title: String, val subtitle: String, val status: RunStatus? = null)

class SyncListAdapter(context: Context) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private var rows: List<RowModel> = emptyList()
    private var selectedPosition = -1
    private var listFocused = false

    fun replace(values: List<RowModel>) {
        rows = values
        if (selectedPosition !in rows.indices) selectedPosition = if (rows.isEmpty()) -1 else 0
        notifyDataSetChanged()
    }

    fun setSelectedPosition(position: Int) {
        if (selectedPosition == position) return
        selectedPosition = position
        notifyDataSetChanged()
    }

    fun setListFocused(value: Boolean) {
        if (listFocused == value) return
        listFocused = value
        notifyDataSetChanged()
    }

    fun row(position: Int): RowModel? = rows.getOrNull(position)

    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = rows[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.row_sync_item, parent, false)
        val row = rows[position]
        val title = view.findViewById<TextView>(R.id.rowTitle)
        val subtitle = view.findViewById<TextView>(R.id.rowSubtitle)
        val status = view.findViewById<TextView>(R.id.rowStatus)

        title.text = row.title
        subtitle.text = row.subtitle
        status.text = ""
        row.status?.let {
            val (glyph, color) = runStatusGlyph(it)
            status.text = glyph
            status.setTextColor(color)
        }

        val selected = listFocused && position == selectedPosition
        view.isSelected = selected
        title.isSelected = selected
        subtitle.isSelected = selected
        return view
    }
}
