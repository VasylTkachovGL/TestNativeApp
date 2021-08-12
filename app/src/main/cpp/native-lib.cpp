#include <jni.h>
#include <string>
#include <libusb.h>
#include "UacDevice.h"
#include "log.h"
#include "Utils.h"

#define TAG "iRigJNI"

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testnativeapp_Core_recordData(JNIEnv *env, jobject thiz, jbyteArray bytes) {
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_testnativeapp_Core_init(JNIEnv *env, jobject thiz, jint fd, jstring filePath) {
    LOG_D(TAG, "Init. File descriptor: %d", fd);
    UacDevice device(fd);
    LOG_D(TAG, "Preparing audio input");
    device.prepareAudioInput();
    device.setChannelSampleRate(UacDevice::Input, 44000);

    // Record sample
    LOG_D(TAG, "Recording sample");
    size_t size = 10 * 44000 * 3; // 10 second(s) of 44.1kHz audio at 24 bit/sample
    std::unique_ptr<unsigned char[]> pcmData(new unsigned char[size]);
    device.recordPCM(pcmData.get(), size);
    LOG_D(TAG, "Sample recorded");

//    std::unique_ptr<unsigned char[]> pcmData2(new unsigned char[size / 3 * 2]);
//    for (size_t sample = 0; sample < size / 3; sample++) {
//        pcmData2[sample * 2] = pcmData[sample * 3 + 1];
//        pcmData2[sample * 2 + 1] = pcmData[sample * 3 + 2];
//    }

    const char *filePathString = env->GetStringUTFChars(filePath, nullptr);
    LOG_D(TAG, "File path: %s", filePathString);

    FILE * pcm = fopen(filePathString,"w+b");
    check(pcm != NULL, "fopen() pcm file");
    fwrite(pcmData.get(), 1, size, pcm);
    fclose(pcm);
    LOG_D(TAG, "File saved");

    // Play sample
    LOG_D(TAG, "Preparing audio output");
    device.prepareAudioOutput();

    LOG_D(TAG, "Setting audio output parameters");
    device.setChannelSampleRate(UacDevice::Output, 44000);

    FILE * pcm2 = fopen(filePathString,"rb");
    check(pcm2 != NULL, "fopen() pcm file");
    fseek(pcm2, 0, SEEK_END);
    long size2 = ftell(pcm2);
    check(pcm2 != 0, "PCM file is empty");
    fseek(pcm2, 0, SEEK_SET);
    check(ftell(pcm2) == 0, "Incorrect file position");

    std::unique_ptr<unsigned char[]> pcmData2(new unsigned char[size2]);
    int readBytes = fread(pcmData2.get(), 1, size2, pcm2);
    check(readBytes == size2, "Cant read PCM data");
    fclose(pcm);

    device.playPCM(pcmData2.get(), size2);

    return (jboolean) JNI_TRUE;
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
    jstring extStoragePath = (jstring) env->CallObjectMethod(extStorageFile, getPathMethod);
    const char *extStoragePathString = env->GetStringUTFChars(extStoragePath, nullptr);
    env->ReleaseStringUTFChars(extStoragePath, extStoragePathString);

    LOG_D(TAG, "External storage path: %s", extStoragePathString);
    return env->NewStringUTF(extStoragePathString);
}