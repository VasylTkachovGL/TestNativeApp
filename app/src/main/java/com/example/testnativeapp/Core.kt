package com.example.testnativeapp

/*
* @author Tkachov Vasyl
* @since 20.07.2021
*/
class Core {

    external fun getExternalStoragePath(): String

    external fun modifyRecordedDataFromAndroid(bytes: ByteArray) : ByteArray

    external fun setFileDescriptor(fileDescriptor: Int)

    external fun setRawUsbDescriptors(descriptors: ByteArray)

    companion object {
        // Used to load the 'native-libs' library on application startup.
        init {
            System.loadLibrary("native-libs")
        }
    }
}