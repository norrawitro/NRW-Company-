package com.nrw.filefinder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileAdapter(private val onClick: (File) -> Unit) :
    RecyclerView.Adapter<FileAdapter.VH>() {

    private val items = ArrayList<File>()

    fun submit(list: List<File>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.name)
        val path: TextView = v.findViewById(R.id.path)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = items[position]
        holder.name.text = f.name
        holder.path.text = f.absolutePath
        holder.itemView.setOnClickListener { onClick(f) }
    }

    override fun getItemCount(): Int = items.size
}
