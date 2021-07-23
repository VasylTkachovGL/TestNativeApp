package com.example.testnativeapp.audiorecorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

import static android.os.Process.THREAD_PRIORITY_AUDIO;
import static android.os.Process.setThreadPriority;

/*
 * @author Tkachov Vasyl
 * @since 21.07.2021
 */
public class AudioRecorder {

    /**
     * The stream to write to.
     */
    private final OutputStream mOutputStream;

    /**
     * If true, the background thread will continue to loop and record audio. Once false, the thread
     * will shut down.
     */
    private volatile boolean mAlive;

    /**
     * The background thread recording audio for us.
     */
    private Thread mThread;

    private final Listener listener;
    public int amplitude = 0;
    public int bufferSize = -1;

    /**
     * A simple audio recorder.
     */
    public AudioRecorder(ParcelFileDescriptor fileDescriptor, Listener listener) {
        this.listener = listener;
        mOutputStream = new ParcelFileDescriptor.AutoCloseOutputStream(fileDescriptor);
    }

    /**
     * @return True if actively recording. False otherwise.
     */
    public boolean isRecording() {
        return mAlive;
    }

    /**
     * Starts recording audio.
     */
    public void start() {
        if (isRecording()) {
            Log.w("iRig", "Already running");
            return;
        }

        mAlive = true;
        mThread = new Thread() {
            @Override
            public void run() {
                setThreadPriority(THREAD_PRIORITY_AUDIO);

                Buffer buffer = new Buffer();
                bufferSize = buffer.size;
                AudioRecord record = new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        buffer.sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_8BIT,
                        buffer.size);

                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.w("iRig", "Failed to start recording");
                    mAlive = false;
                    return;
                }

                record.startRecording();

                // While we're running, we'll read the bytes from the AudioRecord and write them
                // to our output stream.
                try {
                    while (isRecording()) {
                        int len = record.read(buffer.data, 0, buffer.size);
                        if (len >= 0 && len <= buffer.size) {
                            if (isRecording()) {
                                mOutputStream.write(buffer.data, 0, len);
                                mOutputStream.flush();
                            }
                            calculateSignalAmplitude(buffer.data[0], buffer.data[1]);
                            if (listener != null) {
                                listener.onDataReceived(buffer.data);
                            }
                        } else {
                            Log.w("iRig", "Unexpected length returned: " + len);
                        }
                    }
                } catch (IOException e) {
                    Log.e("iRig", "Exception with recording stream", e);
                } finally {
                    stopInternal();
                    try {
                        record.stop();
                    } catch (IllegalStateException e) {
                        Log.e("iRig", "Failed to stop AudioRecord", e);
                    }
                    record.release();
                }
            }
        };
        mThread.start();
    }

    private void stopInternal() {
        Log.w("iRig", "Stop internal");
        mAlive = false;
        try {
            mOutputStream.close();
        } catch (IOException e) {
            Log.e("iRig", "Failed to close output stream", e);
        }
    }

    /**
     * Stops recording audio.
     */
    public void stop() {
        stopInternal();
        try {
            mThread.join();
        } catch (InterruptedException e) {
            Log.e("iRig", "Interrupted while joining AudioRecorder thread", e);
            Thread.currentThread().interrupt();
        }
    }

    private void calculateSignalAmplitude(Byte byte0, byte byte1) {
        int ab = (byte0 & 0xff) << 8 | byte1;
        amplitude = Math.abs(ab);
    }

    public interface Listener {
        void onDataReceived(byte[] bytes);
    }

    private static class Buffer extends AudioBuffer {
        @Override
        protected boolean validSize(int size) {
            return size != AudioRecord.ERROR && size != AudioRecord.ERROR_BAD_VALUE;
        }

        @Override
        protected int getMinBufferSize(int sampleRate) {
            return AudioRecord.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        }
    }
}