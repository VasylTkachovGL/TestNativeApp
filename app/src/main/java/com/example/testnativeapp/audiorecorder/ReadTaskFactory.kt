package com.example.testnativeapp.audiorecorder

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
* @author Tkachov Vasyl
* @since 04.08.2021
*/
class ReadTaskFactory(private val usbDeviceConnection: UsbDeviceConnection?,
    private val readEndPoint: UsbEndpoint?, private val readByteSize: Int = 64) {

    private lateinit var job: Job
    private val syncObject = Object()
    private var listener: ReadListener? = null
    var isRunning: Boolean = false
        private set

    fun start() {
        stop()
        Log.d("iRig", "ReadTaskFactory: start")
        isRunning = true
        job = GlobalScope.launch(Dispatchers.IO) {
            while (isRunning) {
                synchronized(syncObject) {
                    val buffer = ByteArray(readByteSize)
                    val length = usbDeviceConnection?.bulkTransfer(readEndPoint, buffer, readByteSize, 100)
                    listener?.onDataReceived(length)
                }
            }
        }
        job.start()
    }

    fun stop() {
        Log.d("iRig", "ReadTaskFactory: stop")
        isRunning = false
        if (::job.isInitialized && job.isActive) {
            job.cancel()
        }
    }

    fun setListener(listener: ReadListener) {
        this.listener = listener
    }

    interface ReadListener {
        fun onDataReceived(data: Int?)
    }
}