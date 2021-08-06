#include <jni.h>
#include <string>
#include <libusb.h>

// Test function
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
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_testnativeapp_Core_modifyRecordedDataFromAndroid(JNIEnv *env, jobject thiz, jbyteArray bytes) {
    return bytes;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testnativeapp_Core_setFileDescriptor(JNIEnv *env, jobject thiz, jint fileDescriptor) {
    libusb_context *ctx;
    libusb_device_handle *devh;
//    libusb_set_option(&ctx, LIBUSB_OPTION_WEAK_AUTHORITY, NULL);
    libusb_init(&ctx);
    libusb_wrap_sys_device(NULL, (intptr_t)fileDescriptor, &devh);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testnativeapp_Core_setRawUsbDescriptors(JNIEnv *env, jobject thiz, jbyteArray descriptors) {

}