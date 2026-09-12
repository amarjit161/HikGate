package com.hikgate.app.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hikgate.app.R
import com.hikgate.app.data.Device

class DeviceAdapter(
    private var devices: List<Device>,
    private val onClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: android.widget.TextView = itemView.findViewById(R.id.deviceName)
        val host: android.widget.TextView = itemView.findViewById(R.id.deviceHost)
        val dot: View = itemView.findViewById(R.id.statusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = devices[position]
        holder.name.text = d.name
        holder.host.text = "${d.host}:${d.httpPort}" + if (d.useHttps) " (https)" else ""
        holder.dot.setBackgroundResource(R.drawable.dot_offline) // updated live by DeviceActivity's ping, not here
        holder.itemView.setOnClickListener { onClick(d) }
    }

    override fun getItemCount() = devices.size

    fun submitList(newList: List<Device>) {
        devices = newList
        notifyDataSetChanged()
    }
}
