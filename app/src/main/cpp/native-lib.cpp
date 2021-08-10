#include <jni.h>
#include <string>
#include <libusb.h>
#include "UacDevice.h"
#include "log.h"
#include "Utils.h"

#define TAG "iRigJNI"

static const uint16_t IRIG_UA_VID = 0x6499;
static const uint16_t IRIG_UA_PID = 0x0026;

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testnativeapp_Core_recordData(JNIEnv *env, jobject thiz, jbyteArray bytes) {
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_testnativeapp_Core_init(JNIEnv *env, jobject thiz, jint fd, jlongArray handle) {
    LOG_D(TAG, "Init. File descriptor: %d", fd);
    UacDevice device(fd, IRIG_UA_PID);
    LOG_D(TAG, "Preparing audio input");
    device.prepareAudioInput();

    LOG_D(TAG, "Setting audio input parameters");
    device.setChannelSampleRate(UacDevice::Input, 48000);

    // Record test sample
    size_t size = 5 * 48000 * 3; // 5 seconds of 48kHz audio at 24 bit/sample
    std::unique_ptr<unsigned char[]> pcmData(new unsigned char[size]);
    device.recordPCM(pcmData.get(), size);

    std::unique_ptr<unsigned char[]> pcmData2(new unsigned char[size/3*2]);
    for(size_t sample=0; sample < size /3; sample++)
    {
        pcmData2[sample*2] = pcmData[sample*3];
        pcmData2[sample*2+1] = pcmData[sample*3+1];
    }

//    FILE * pcm = fopen(argv[2],"w+b");
//    check(pcm != NULL, "fopen() pcm file");
//    fwrite(pcmData2.get(), 1, size/3*2, pcm);
//    fclose(pcm);

    //Play sample
    LOG_D(TAG, "Preparing audio output");
    device.prepareAudioOutput();

    LOG_D(TAG, "Setting audio output parameters");
    device.setChannelVolume(UacDevice::Output, 5);
    device.setChannelSampleRate(UacDevice::Output, 48000);

//    FILE * pcm = fopen(argv[2],"rb");
//    check(pcm != NULL, "fopen() pcm file");
//    fseek(pcm, 0, SEEK_END);
//    long size = ftell(pcm);
//    check(size != 0, "PCM file is empty");
//    fseek(pcm, 0, SEEK_SET);
//    check(ftell(pcm) == 0, "Incorrect file position");
//
//    std::unique_ptr<unsigned char[]> pcmData(new unsigned char[size]);
//    int readBytes = fread(pcmData.get(), 1, size, pcm);
//    check(readBytes == size, "Cant read PCM data");
//    fclose(pcm);

    device.playPCM(pcmData2.get(), size);

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

    return env->NewStringUTF(extStoragePathString);
}