package com.example.testnativeapp

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import com.example.testnativeapp.audiorecorder.AudioRecorder
import kotlinx.android.synthetic.main.activity_usb.*
import kotlinx.coroutines.*
import java.io.File
import java.io.PrintWriter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.ExperimentalTime


/*
 * @author Tkachov Vasyl
 * @since 20.07.2021
 */
@ExperimentalTime
class UsbActivity : Activity() {

    private val usbManager: UsbManager by lazy {
        getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbDevice: UsbDevice? = null
    private var usbDataInterface: UsbInterface? = null
    private var readEndPoint: UsbEndpoint? = null
    private var writeEndPoint: UsbEndpoint? = null
    private val writeDataScope = CoroutineScope(Dispatchers.IO + Job())
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
        discoverConnectedDevice(intent)

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
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        discoverConnectedDevice(intent)
    }

    override fun onStop() {
        super.onStop()
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
            App.core?.setFileDescriptor(connection.fileDescriptor)
            App.core?.setRawUsbDescriptors(connection.rawDescriptors)

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
                    openUsbInterface(it)
                }
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
    private fun writeDataAsync(buffer: ByteArray) {
        val job: Job = writeDataScope.launch {
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

    private suspend fun updateAudioWave() {
        while (audioRecorder?.isRecording == true) {
            delay(100)
            val currentMaxAmplitude = audioRecorder?.amplitude ?: 0
            audioRecordView.update(currentMaxAmplitude)
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
        private const val TIMEOUT = 1000
    }
}