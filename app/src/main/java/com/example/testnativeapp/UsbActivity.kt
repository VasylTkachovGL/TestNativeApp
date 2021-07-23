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

    private val factory: ReadTaskFactory by lazy {
        ReadTaskFactory()
    }
    private var readListener: ReadListener? = null
    private var audioRecorder: AudioRecorder? = null
    private val tmpFile: File by lazy {
        val f = File("$externalCacheDir${File.separator}iRig_tmp.pcm")
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
            if (!factory.isRunning) {
                recordAudio()
                factory.start()
                startReadButton.text = "Stop recording"
            } else {
                stopRecordingAudio()
                factory.stop()
                startReadButton.text = "Start recording"
            }
        }

        val fileDescriptor = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE)
        audioRecorder = AudioRecorder(fileDescriptor, object: AudioRecorder.Listener {
            override fun onDataReceived(bytes: ByteArray?) {
                // Here we got data from audio recorder
                if (bytes != null) {
                    val modifiedData = App.core?.modifyRecordedDataFromAndroid(bytes)

                    // Send modified recorded data to USB device
                    modifiedData?.let { writeDataAsync(it) }
                }
            }
        })

        readListener = object : ReadListener {
            override fun onNewData(data: Int?) {
                // Here we got data received from iRig
                val info = StringBuilder()
                info.append("Read data result: $data \n")
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
        if (!factory.isRunning) {
            factory.stop()
        }
        usbDeviceConnection?.releaseInterface(usbDataInterface)
        usbDeviceConnection?.close()
    }

    private fun recordAudio() {
        // clear tmp audio file
        PrintWriter(tmpFile).run {
            print("")
            close()
        }
        audioRecorder?.start()
        audioWaveScope.launch { updateAudioWave() }
    }

    private fun stopRecordingAudio() {
        audioRecorder?.stop()
        audioRecordView.recreate()
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
                    connection.controlTransfer(0x21, 0x22, 0x1, 0, null, 0, 0)
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
        writeDataScope.launch {
            writeData(buffer)
        }
    }

    private suspend fun writeData(bytes: ByteArray): Int = suspendCoroutine { continuation ->
        usbDeviceConnection?.apply {
            val transferResult = bulkTransfer(writeEndPoint, bytes, bytes.size, TIMEOUT)
            Log.d("iRig", "Write data result: $transferResult")
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

    inner class ReadTaskFactory(private val readByteSize: Int = 640,
        private val listener: ReadListener? = readListener) {

        var isRunning: Boolean = false
            private set
        private lateinit var job: Job
        private val syncObject = Object()

        fun start() {
            stop()
            isRunning = true
            job = GlobalScope.launch(Dispatchers.IO) {
                while (isRunning) {
                    synchronized(syncObject) {
                        val buffer = ByteArray(readByteSize)
                        val bulkTransfer = usbDeviceConnection?.bulkTransfer(readEndPoint, buffer, readByteSize,100)
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

    private suspend fun updateAudioWave() {
        while (factory.isRunning) {
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