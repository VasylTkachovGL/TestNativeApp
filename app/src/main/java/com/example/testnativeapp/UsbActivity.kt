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
import com.example.testnativeapp.audiorecorder.AudioRecorder
import com.example.testnativeapp.audiorecorder.ReadTaskFactory
import kotlinx.android.synthetic.main.activity_usb.*
import kotlinx.coroutines.*
import java.io.File
import java.io.PrintWriter
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
    private var usbDataInterface: UsbInterface? = null
    private var readEndPoint: UsbEndpoint? = null
    private var writeEndPoint: UsbEndpoint? = null
    private var factory: ReadTaskFactory? = null
    private var usbPermissionReceiver: BroadcastReceiver? = null
    private var deviceStatusReceiver: BroadcastReceiver? = null

    private val dataScope = CoroutineScope(Dispatchers.IO + Job())
    private val audioWaveScope = CoroutineScope(Dispatchers.Main)

    private var audioRecorder: AudioRecorder? = null
    private val tmpFile: File by lazy {
        val f = File("$externalCacheDir${File.separator}iRig_tmp.pcm")
        if (!f.exists()) {
            f.createNewFile()
        }
        f
    }
    private val audioFile: File by lazy {
        val f = File("$externalCacheDir${File.separator}iRig_sample.pcm")
        if (!f.exists()) {
            f.createNewFile()
        }
        f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb)
        checkConnectedDevices()

        startReadButton.setOnClickListener {
            if (audioRecorder?.isRecording == false) {
                recordAudio()
                startReadButton.text = "Stop recording"
            } else {
                stopRecordingAudio()
                startReadButton.text = "Start recording"
            }
        }

        audioRecorder = AudioRecorder(object : AudioRecorder.Listener {
            override fun onDataReceived(bytes: ByteArray?) {
                // Here we got data from audio recorder
                bytes?.asSequence()?.chunked(64)?.forEach {
                    writeDataAsync(it.toByteArray())
                }
            }
        })

        deviceStatusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                parseDeviceIntent(intent)
            }
        }
        registerReceiver(deviceStatusReceiver, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        })
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
                        Log.d("iRig", "ACTION_USB_DEVICE_ATTACHED")
                        onDeviceAttached(device)
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        Log.d("iRig", "ACTION_USB_DEVICE_DETACHED")
                        onDeviceDetached()
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
    }

    private fun onDeviceDetached() {
        closeConnection()
    }

    private fun checkConnectedDevices() {
        val deviceList: MutableIterator<UsbDevice> = usbManager.deviceList.values.iterator()
        deviceList.asSequence().filter {
            it.productId == 26 && it.vendorId == 6499
        }.firstOrNull()?.also {
            onDeviceAttached(it)
        }
    }

    private fun openUsbDevice(usbDevice: UsbDevice) {
        Log.d("iRig", "openUsbDevice ${usbDevice.productName}")
        usbDeviceConnection = usbManager.openDevice(usbDevice)

        usbDeviceConnection?.let { connection ->
            App.core?.setFileDescriptor(connection.fileDescriptor)
            App.core?.setRawUsbDescriptors(connection.rawDescriptors)

            for (interfaceIndex in 0 until usbDevice.interfaceCount) {
                val usbInterface = usbDevice.getInterface(interfaceIndex)
                val isClaimed = connection.claimInterface(usbInterface, true)
                Log.d("iRig","Interface id: ${usbInterface.id} class: ${usbInterface.interfaceClass} name: ${usbInterface.name}")
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
                    Log.d("iRig","- Endpoint type: ${endpoint.type} direction: ${endpoint.direction} maxPacketSize: ${endpoint.maxPacketSize}")
                }
            }
            if (writeEndPoint == null || readEndPoint == null) {
                return
            }

            factory = ReadTaskFactory(connection, readEndPoint)
            factory?.setListener(object : ReadTaskFactory.ReadListener {
                override fun onDataReceived(data: Int?) {
                    Log.d("iRig", "Received: $data")
                }
            })
            factory?.start()
        }
    }

    private fun writeDataAsync(buffer: ByteArray) {
        dataScope.launch {
            writeData(buffer)
        }
    }

    private suspend fun writeData(bytes: ByteArray): Int = suspendCoroutine { continuation ->
        usbDeviceConnection?.apply {
            val transferResult = bulkTransfer(writeEndPoint, bytes, bytes.size, TIMEOUT)
            Log.d("iRig", "Write data[${bytes.size}] result: $transferResult")
            continuation.resume(transferResult)
        }
    }

    private fun requestUsbPermission(usbDevice: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
        usbPermissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d("iRig", "UsbPermissionReceiver: ${intent.action}")
                if (ACTION_USB_PERMISSION != intent.action) {
                    return
                }
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                val permissionGranted =
                    intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (permissionGranted) {
                    device?.let { openUsbDevice(it) }
                } else {
                    Toast.makeText(context, "Permission denied: $device", Toast.LENGTH_LONG).show()
                }
            }
        }
        registerReceiver(usbPermissionReceiver, IntentFilter(ACTION_USB_PERMISSION))
        usbManager.requestPermission(usbDevice, permissionIntent)
    }

    private fun closeConnection() {
        factory?.let {
            if (it.isRunning) {
                it.stop()
            }
        }
        if (audioRecorder?.isRecording == true) {
            audioRecorder?.stop()
        }
        usbDeviceConnection?.releaseInterface(usbDataInterface)
        usbDeviceConnection?.close()
    }

    private fun recordAudio() {
        clearTmpAudioFile()
        audioRecorder?.apply {
            prepare(tmpFile)
            start()
        }
        audioWaveScope.launch { updateAudioWave() }
    }

    private fun stopRecordingAudio() {
        audioRecorder?.stop()
        tmpFile.copyTo(audioFile, true)
        audioRecordView.recreate()
    }

    private fun clearTmpAudioFile() {
        PrintWriter(tmpFile).run {
            print("")
            close()
        }
    }

    private suspend fun updateAudioWave() {
        while (audioRecorder?.isRecording == true) {
            delay(100)
            val currentMaxAmplitude = audioRecorder?.amplitude ?: 0
            audioRecordView.update(currentMaxAmplitude)
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.testnativeapp.USB_PERMISSION"
        private const val TIMEOUT = 1000
    }
}