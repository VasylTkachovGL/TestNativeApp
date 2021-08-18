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
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_usb.*
import kotlinx.coroutines.*
import java.io.File
import java.util.*

/*
 * @author Tkachov Vasyl
 * @since 20.07.2021
 */
class UsbActivity : Activity() {

    private val tmpFilePath by lazy { "$externalCacheDir${File.separator}data.pcm" }
    private val usbManager: UsbManager by lazy {
        getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbDataInterface: UsbInterface? = null
    private var readEndPoint: UsbEndpoint? = null
    private var writeEndPoint: UsbEndpoint? = null
    private var usbPermissionReceiver: BroadcastReceiver? = null
    private var deviceStatusReceiver: BroadcastReceiver? = null
    private var isRecording = false

    private var adapter: UsbDeviceAdapter? = null
    private val audioWaveScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb)
        refreshDeviceList()

        loopbackButton.setOnClickListener {
            usbDeviceConnection?.let { connection ->
                val inFrequency = inFreqEditView.getIntValue()
                val inBytesPerSample = inBytesPerSampleEditView.getIntValue()
                val inChannels = inChannelsEditView.getIntValue()
                val outFrequency = outFreqEditView.getIntValue()
                val outBytesPerSample = outBytesPerSampleEditView.getIntValue()
                val outChannels = outChannelsEditView.getIntValue()
                App.core?.startLoopback(connection.fileDescriptor, inFrequency, inBytesPerSample,
                    inChannels, outFrequency, outBytesPerSample, outChannels)
            }
        }

        playButton.setOnClickListener {
            usbDeviceConnection?.let { connection ->
                App.core?.playFile(connection.fileDescriptor, tmpFilePath)
            }
        }

        searchButton.setOnClickListener {
            refreshDeviceList()
        }

        deviceStatusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                parseDeviceIntent(intent)
            }
        }
        registerReceiver(deviceStatusReceiver, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        })

        appBuildInfoView.text =
            String.format(Locale.getDefault(), "app version: %s(%s)", BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        parseDeviceIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        usbPermissionReceiver?.let { unregisterReceiver(usbPermissionReceiver) }
        deviceStatusReceiver?.let { unregisterReceiver(deviceStatusReceiver) }
        closeConnection()
    }

    private fun parseDeviceIntent(intent: Intent?) {
        intent?.action?.let { action ->
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            device?.let {
                when (action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        Log.d(TAG, "ACTION_USB_DEVICE_ATTACHED")
                        //onDeviceAttached(device)
                        refreshDeviceList()
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        Log.d(TAG, "ACTION_USB_DEVICE_DETACHED")
                        onDeviceDetached()
                        refreshDeviceList()
                        Toast.makeText(this, "USB DEVICE DETACHED", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: finish()
        }
    }

    private fun onDeviceAttached(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            openUsbDevice(device)
        } else {
            requestUsbPermission(device)
        }
        audioFieldsGroup.visibility = View.VISIBLE
        deviceListGroup.visibility = View.GONE
        usbDevicesEmptyView.visibility = View.GONE
    }

    private fun onDeviceDetached() {
        closeConnection()
        audioFieldsGroup.visibility = View.GONE
        deviceListGroup.visibility = View.VISIBLE
    }

    private fun initDeviceList() {
        adapter = UsbDeviceAdapter(object : UsbDeviceAdapter.UsbDeviceListClickListener {
            override fun onDeviceClicked(usbDevice: UsbDevice) {
                onDeviceAttached(usbDevice)
            }
        })
        usbDevicesView.adapter = adapter
        usbDevicesView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
    }

    private fun refreshDeviceList() {
        if (adapter == null) {
            initDeviceList()
        }
        val deviceList = usbManager.deviceList.values
        Log.d(TAG, "refreshDeviceList ${deviceList.size}")

        adapter?.addUsbDevices(deviceList.toMutableList())
        usbDevicesEmptyView.visibility = if (deviceList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openUsbDevice(usbDevice: UsbDevice) {
        Log.d(TAG, "openUsbDevice ${usbDevice.productName}")
        usbDeviceConnection = usbManager.openDevice(usbDevice)

        usbDeviceConnection?.let { connection ->
            for (interfaceIndex in 0 until usbDevice.interfaceCount) {
                val usbInterface = usbDevice.getInterface(interfaceIndex)
                val isClaimed = connection.claimInterface(usbInterface, true)
                Log.d(TAG, "Interface id: ${usbInterface.id} name: ${usbInterface.name}")
                if (!isClaimed) {
                    continue
                }
                for (index in 0 until usbInterface.endpointCount) {
                    val endpoint = usbInterface.getEndpoint(index)
                    if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        when (endpoint.direction) {
                            UsbConstants.USB_DIR_OUT -> writeEndPoint = endpoint
                            UsbConstants.USB_DIR_IN -> readEndPoint = endpoint
                        }
                    }
                    Log.d(TAG, "- Endpoint type: ${endpoint.type} dir: ${endpoint.direction}")
                }
                usbDataInterface = usbInterface
            }
            if (writeEndPoint == null || readEndPoint == null) {
                return
            }
        }
    }

    private fun requestUsbPermission(usbDevice: UsbDevice) {
        Log.d(TAG, "requestUsbPermission")
        val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
        usbPermissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "UsbPermissionReceiver: ${intent.action}")
                if (ACTION_USB_PERMISSION != intent.action) {
                    return
                }
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                val permissionGranted =
                    intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (permissionGranted) {
                    device?.let { openUsbDevice(it) }
                } else {
                    Toast.makeText(context, "Permission denied: ${device?.manufacturerName}", Toast.LENGTH_LONG).show()
                }
            }
        }
        registerReceiver(usbPermissionReceiver, IntentFilter(ACTION_USB_PERMISSION))
        usbManager.requestPermission(usbDevice, permissionIntent)
    }

    private fun closeConnection() {
        usbDeviceConnection?.releaseInterface(usbDataInterface)
        usbDeviceConnection?.close()
    }

    private fun startAudioWave() {
        audioWaveScope.launch { updateAudioWave() }
    }

    private fun stopAudioWave() {
        audioWaveScope.cancel()
        audioRecordView.recreate()
    }

    private suspend fun updateAudioWave() {
        while (isRecording) {
            delay(100)
            val currentMaxAmplitude = 0
            audioRecordView.update(currentMaxAmplitude)
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.testnativeapp.USB_PERMISSION"
        private const val TAG = "iRig"
    }
}