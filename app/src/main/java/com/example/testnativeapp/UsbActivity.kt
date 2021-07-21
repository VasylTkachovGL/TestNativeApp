package com.example.testnativeapp

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_usb.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/*
 * @author Tkachov Vasyl
 * @since 20.07.2021
 */
class UsbActivity : Activity() {

    private val usbManager: UsbManager by lazy {
        getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbDevice: UsbDevice? = null
    private var usbDataInterface: UsbInterface? = null
    private var readEndPoint: UsbEndpoint? = null
    private var writeEndPoint: UsbEndpoint? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val factory: ReadTaskFactory by lazy {
        ReadTaskFactory()
    }
    private var readListener: ReadListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb)
        discoverConnectedDevice(intent)

        start_read_button.setOnClickListener {
            start_read_button.isEnabled = false
            stop_read_button.isEnabled = true
            factory.start()
        }
        stop_read_button.setOnClickListener {
            start_read_button.isEnabled = true
            stop_read_button.isEnabled = false
            factory.stop()
        }
        readListener = object : ReadListener {
            override fun onNewData(data: Int?) {
                // Here we got new read data
                val info = StringBuilder()
                info.append("Read: ")
                info.append(data)
                info.append("\n")
                Log.d("iRig", info.toString())
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        discoverConnectedDevice(intent)
    }

    override fun onStop() {
        super.onStop()
        usbDeviceConnection?.releaseInterface(usbDataInterface)
        usbDeviceConnection?.close()
    }

    private fun discoverConnectedDevice(intent: Intent?) {
        intent?.action?.let { action ->
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            usbDevice = device
            if (device == null) {
                finish()
            } else if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                onDeviceAttached(device)
            }
        }
    }

    private fun onDeviceAttached(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            openUsbDevice(device)
        } else {
            requestUsbPermission(device)
        }
    }

    private fun openUsbDevice(usbDevice: UsbDevice) {
        usbDeviceConnection = usbManager.openDevice(usbDevice)

        usbDeviceConnection?.let { connection ->
            val interfaceCount = usbDevice.interfaceCount
            var interfaceIndex = 0
            while (true) {
                if (interfaceIndex == interfaceCount) {
                    interfaceIndex = 6
                    break
                }
                if (usbDevice.getInterface(interfaceIndex).id == 3) {
                    break
                }
                interfaceIndex++
            }

            usbDataInterface = usbDevice.getInterface(interfaceIndex)
            usbDataInterface?.let {
                Log.d("iRig", "Claim interface: ${it.id}")
                if (connection.claimInterface(it, true)) {
                    return
                }
                openUsbInterface(it)
                writeDataAsync(byteArrayOf(0))
            }
        }
    }

    private fun openUsbInterface(usbInterface: UsbInterface) {
        val info = StringBuilder()
        for (j in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(j)
            when (endpoint.direction) {
                UsbConstants.USB_DIR_OUT -> writeEndPoint = endpoint
                UsbConstants.USB_DIR_IN -> readEndPoint = endpoint
            }
            info.append(
                "\nEndpoint ${endpoint.endpointNumber} type: ${endpoint.type} direction: ${endpoint.direction}")
        }
        Log.d("iRig", info.toString())
    }

    /**
     * Use this method to send data to USB device
     */
    fun writeDataAsync(buffer: ByteArray) {
        scope.launch {
            writeData(buffer)
        }
    }

    private suspend fun writeData(bytes: ByteArray): Int = suspendCoroutine { continuation ->
        usbDeviceConnection?.apply {
            val transferResult = bulkTransfer(writeEndPoint, bytes, bytes.size, TIMEOUT)
            Log.d("iRig", "writeData result: $transferResult")
            continuation.resume(transferResult)
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter)
        usbManager.requestPermission(device, permissionIntent)
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("iRig", "onReceive: ${intent.action}")
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            openUsbDevice(it)
                        }
                    } else {
                        Toast.makeText(this@UsbActivity, "Permission denied for device $device",
                            Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }

    interface ReadListener {
        fun onNewData(data: Int?)
    }

    inner class ReadTaskFactory(protected val readByteSize: Int = 4096,
        val listener: ReadListener? = readListener) {

        private var isRunning: Boolean = false
        private lateinit var job: Job
        private val syncObject = Object()

        fun start() {
            stop()
            isRunning = true
            job = GlobalScope.launch(Dispatchers.IO) {
                while (isRunning) {
                    synchronized(syncObject) {
                        val buffer = ByteArray(readByteSize)
                        val bulkTransfer =
                            usbDeviceConnection?.bulkTransfer(readEndPoint, buffer, readByteSize,
                                200)
                        listener?.onNewData(bulkTransfer)
                    }
                }
            }
            job.start()
        }

        fun stop() {
            isRunning = false
            if (::job.isInitialized && job.isActive) {
                job.cancel()
            }
        }
    }

    fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
        private const val TIMEOUT = 1000
    }
}