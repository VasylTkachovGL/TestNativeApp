package com.example.testnativeapp.audiorecorder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.experimental.and
import kotlin.math.abs

/*
 * @author Tkachov Vasyl
 * @since 21.07.2021
 */
class AudioRecorder(private val listener: Listener?) {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var mOutputStream: OutputStream? = null

    @Volatile
    var isRecording = false
        private set

    private var mThread: Thread? = null
    var amplitude = 0

    fun prepare(tmpFile: File) {
        val fileDescriptor = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE)
        if (mOutputStream == null) {
            mOutputStream = ParcelFileDescriptor.AutoCloseOutputStream(fileDescriptor)
        }
    }

    fun start() {
        if (isRecording) {
            Log.w("iRig", "Already running")
            return
        }
        isRecording = true
        mThread = object : Thread() {
            override fun run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val buffer = Buffer()
                val record = AudioRecord(MediaRecorder.AudioSource.DEFAULT, buffer.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_8BIT, buffer.size)
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w("iRig", "Failed to start recording")
                    isRecording = false
                    return
                }
                record.startRecording()

                try {
                    while (isRecording) {
                        val len = record.read(buffer.data, 0, buffer.size)
                        if (len >= 0 && len <= buffer.size) {
                            if (isRecording) {
                                mOutputStream?.let {
                                    it.write(buffer.data, 0, len)
                                    it.flush()
                                }
                            }
                            calculateSignalAmplitude(buffer.data[0], buffer.data[1])
                            listener?.onDataReceived(buffer.data)
                        } else {
                            Log.w("iRig", "Unexpected length returned: $len")
                        }
                    }
                } catch (e: IOException) {
                    Log.e("iRig", "Exception with recording stream", e)
                } finally {
                    stopInternal()
                    try {
                        record.stop()
                    } catch (e: IllegalStateException) {
                        Log.e("iRig", "Failed to stop AudioRecord", e)
                    }
                    record.release()
                }
            }
        }
        mThread?.start()
    }

    private fun stopInternal() {
        Log.w("iRig", "Stop internal")
        isRecording = false
        try {
            mOutputStream?.close()
            mOutputStream = null
        } catch (e: IOException) {
            Log.e("iRig", "Failed to close output stream", e)
        }
    }

    fun stop() {
        stopInternal()
        try {
            mThread?.join()
        } catch (e: InterruptedException) {
            Log.e("iRig", "Interrupted while joining AudioRecorder thread", e)
            Thread.currentThread().interrupt()
        }
    }

    private fun calculateSignalAmplitude(byte0: Byte, byte1: Byte) {
        val ab: Int = (byte0 and 0xff.toByte()).toInt() shl 8 or byte1.toInt()
        amplitude = abs(ab)
    }

    interface Listener {
        fun onDataReceived(bytes: ByteArray?)
    }

    private class Buffer : AudioBuffer() {
        override fun validSize(size: Int): Boolean {
            return size != AudioRecord.ERROR && size != AudioRecord.ERROR_BAD_VALUE
        }

        override fun getMinBufferSize(sampleRate: Int): Int {
            return AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)
        }
    }
}