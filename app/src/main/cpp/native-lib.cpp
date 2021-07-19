#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_testnativeapp_Core_stringFromJNI(
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
JNIEXPORT void JNICALL
Java_com_example_testnativeapp_Core_readDataFromUsb(JNIEnv *env, jobject thiz, jbyteArray bytes,
                                                    jint length) {
    // TODO: implement readDataFromUsb()
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testnativeapp_Core_onUsbConnectionChanged(JNIEnv *env, jobject thiz,
                                                           jboolean is_connected) {
    // TODO: implement onUsbConnectionChanged()
}