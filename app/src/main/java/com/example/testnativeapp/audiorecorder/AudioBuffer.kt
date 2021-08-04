package com.example.testnativeapp.audiorecorder

import android.util.Log

/*
 * @author Tkachov Vasyl
 * @since 21.07.2021
 */
abstract class AudioBuffer protected constructor() {
    val size: Int
    val sampleRate: Int
    val data: ByteArray

    protected abstract fun validSize(size: Int): Boolean
    protected abstract fun getMinBufferSize(sampleRate: Int): Int

    init {
        var size = -1
        var sampleRate = -1

        for (rate in POSSIBLE_SAMPLE_RATES) {
            sampleRate = rate
            size = getMinBufferSize(sampleRate)
            if (validSize(size)) {
                break
            }
        }

        if (!validSize(size)) {
            size = 1024
        }
        this.size = size
        this.sampleRate = sampleRate
        Log.d("iRig", "Write buffer: $size sampleRate: $sampleRate")
        data = ByteArray(size)
    }

    companion object {
        private val POSSIBLE_SAMPLE_RATES =
            intArrayOf( /*8000, 11025, 16000, 22050,*/32000, 44100, 48000, 88200, 96000)
    }
}