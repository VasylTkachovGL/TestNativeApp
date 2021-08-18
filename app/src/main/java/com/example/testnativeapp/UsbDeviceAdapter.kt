package com.example.testnativeapp

import android.hardware.usb.UsbDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.*

/*
* @author Tkachov Vasyl
* @since 18.08.2021
*/
class UsbDeviceAdapter(private val clickListener: UsbDeviceListClickListener) :
    RecyclerView.Adapter<UsbDeviceAdapter.UsbDeviceViewHolder>() {

    private val usbDevices = ArrayList<UsbDevice>()

    interface UsbDeviceListClickListener {
        fun onDeviceClicked(usbDevice: UsbDevice)
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsbDeviceViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.usb_device_view, parent, false)
        return UsbDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsbDeviceViewHolder, position: Int) {
        holder.onBind(usbDevices[position])
    }

    override fun getItemCount(): Int {
        return usbDevices.size
    }

    override fun getItemId(position: Int): Long {
        return usbDevices[position].deviceId.toLong()
    }

    fun clearUsbDevices() {
        usbDevices.clear()
        notifyItemRangeRemoved(0, usbDevices.size)
    }

    fun addUsbDevices(devices: MutableCollection<UsbDevice>) {
        usbDevices.addAll(devices)
        notifyItemRangeInserted(0, usbDevices.size)
    }

    inner class UsbDeviceViewHolder internal constructor(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val title: TextView = itemView.findViewById(R.id.usb_device_title)
        private val vendor: TextView = itemView.findViewById(R.id.usb_device_vendor)
        private val product: TextView = itemView.findViewById(R.id.usb_device_product)

        fun onBind(device: UsbDevice) {
            title.text = device.productName
            vendor.text = String.format(Locale.getDefault(), "Vendor id: %s", device.vendorId)
            product.text = String.format(Locale.getDefault(), "Product id: %d", device.productId)
            itemView.setOnClickListener {
                clickListener.onDeviceClicked(device)
            }
        }
    }
}