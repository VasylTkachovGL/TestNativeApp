#include <jni.h>
#include <string>
#include <libusb.h>
#include <thread>
#include "UacDevice.h"
#include "log.h"
#include "Utils.h"

#define TAG "iRigJNI"

static const uint16_t RATE = 44100;

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testnativeapp_Core_startLoopback(JNIEnv *env, jobject thiz, jint fd, jint inFrequency, jint inBytesPerSample, jint inChannels, jint outFrequency, jint outBytesPerSample, jint outChannels) {
    LOG_D(TAG, "Init. File descriptor: %d", fd);
    UacDevice device(fd);
    device.prepareAudioOutput();
    device.prepareAudioInput();
    device.setChannelSampleRate(UacDevice::Output, outFrequency);
    device.setChannelSampleRate(UacDevice::Input, inFrequency);

    uint16_t inPacketSize = (inFrequency / 1000) * inBytesPerSample * inChannels;
    uint16_t outPacketSize = (outFrequency / 1000) * outBytesPerSample * outChannels;
    LOG_D(TAG, "Loopback with packet sizes: input=%d, output=%d", inPacketSize, outPacketSize);

    device.loopback(inPacketSize, outPacketSize, inChannels);
    while(true)
        std::this_thread::sleep_for(std::chrono::milliseconds(1000));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testnativeapp_Core_playFile(JNIEnv *env, jobject thiz, jint fd, jstring filePath) {
    LOG_D(TAG, "Init. File descriptor: %d", fd);
    UacDevice device(fd);

    const char *filePathString = env->GetStringUTFChars(filePath, nullptr);
    LOG_D(TAG, "File path: %s", filePathString);

    LOG_D(TAG, "Preparing audio output");
    device.prepareAudioOutput();
    device.setChannelSampleRate(UacDevice::Output, RATE);

    FILE *pcm = fopen(filePathString, "rb");
    check(pcm != nullptr, "fopen() pcm file");
    fseek(pcm, 0, SEEK_END);
    long size = ftell(pcm);
    check(pcm != 0, "PCM file is empty");
    fseek(pcm, 0, SEEK_SET);
    check(ftell(pcm) == 0, "Incorrect file position");

    std::unique_ptr<unsigned char[]> pcmData2(new unsigned char[size]);
    int readBytes = fread(pcmData2.get(), 1, size, pcm);
    check(readBytes == size, "Cant read PCM data");
    fclose(pcm);

    device.playPCM(pcmData2.get(), size);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testnativeapp_Core_recordFile(JNIEnv *env, jobject thiz, jint fd, jstring filePath) {
    LOG_D(TAG, "Init. File descriptor: %d", fd);
    UacDevice device(fd);
    LOG_D(TAG, "Preparing audio input");
    device.prepareAudioInput();
    device.setChannelSampleRate(UacDevice::Input, RATE);

    LOG_D(TAG, "Recording sample");
    size_t size = 10 * RATE * 3; // 10 second(s) of 44.1kHz audio at 24 bit/sample
    std::unique_ptr<unsigned char[]> pcmData(new unsigned char[size]);
    device.recordPCM(pcmData.get(), size);
    LOG_D(TAG, "Sample recorded");

    std::unique_ptr<unsigned char[]> pcmData2(new unsigned char[size / 3 * 2]);
    for (size_t sample = 0; sample < size / 3; sample++) {
        pcmData2[sample * 2] = pcmData[sample * 3 + 1];
        pcmData2[sample * 2 + 1] = pcmData[sample * 3 + 2];
    }

    const char *filePathString = env->GetStringUTFChars(filePath, nullptr);
    LOG_D(TAG, "File path: %s", filePathString);

    FILE *pcm = fopen(filePathString, "w+b");
    check(pcm != nullptr, "fopen() pcm file");
    fwrite(pcmData.get(), 1, size, pcm);
    fclose(pcm);
    LOG_D(TAG, "File saved");
}

// Test method
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_testnativeapp_Core_getExternalStoragePath(
        JNIEnv *env,
        jobject /* this */) {

    jclass envClass = env->FindClass("android/os/Environment");
    jmethodID getExtStorageDirectoryMethod = env->GetStaticMethodID(envClass,
                                                                    "getExternalStorageDirectory",
                                                                    "()Ljava/io/File;");
    jobject extStorageFile = env->CallStaticObjectMethod(envClass, getExtStorageDirectoryMethod);

    jclass fileClass = env->FindClass("java/io/File");
    jmethodID getPathMethod = env->GetMethodID(fileClass, "getPath", "()Ljava/lang/String;");
    auto extStoragePath = (jstring) env->CallObjectMethod(extStorageFile, getPathMethod);
    const char *extStoragePathString = env->GetStringUTFChars(extStoragePath, nullptr);
    env->ReleaseStringUTFChars(extStoragePath, extStoragePathString);

    LOG_D(TAG, "External storage path: %s", extStoragePathString);
    return env->NewStringUTF(extStoragePathString);
}