package com.example.testnativeapp

import android.hardware.usb.UsbDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.*

import androidx.recyclerview.widget.AsyncListDiffer


/*
* @author Tkachov Vasyl
* @since 18.08.2021
*/
class UsbDeviceAdapter(private val clickListener: UsbDeviceListClickListener) :
    RecyclerView.Adapter<UsbDeviceAdapter.UsbDeviceViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<UsbDevice>() {
        override fun areItemsTheSame(old: UsbDevice, new: UsbDevice): Boolean {
            return old.deviceId == new.deviceId
        }

        override fun areContentsTheSame(old: UsbDevice, new: UsbDevice): Boolean {
            return old == new
        }
    }
    private val differ: AsyncListDiffer<UsbDevice> = AsyncListDiffer(this, diffCallback)

    interface UsbDeviceListClickListener {
        fun onDeviceClicked(usbDevice: UsbDevice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsbDeviceViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.usb_device_view, parent, false)
        return UsbDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsbDeviceViewHolder, position: Int) {
        holder.onBind(differ.currentList[position])
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    fun addUsbDevices(devices: MutableList<UsbDevice>) {
        differ.submitList(devices)
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