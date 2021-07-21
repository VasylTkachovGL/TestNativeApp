package com.example.testnativeapp

/*
* @author Tkachov Vasyl
* @since 20.07.2021
*/
class Core {

    external fun getExternalStoragePath(): String

    external fun readDataFromUsb(bytes: ByteArray, length: Int)

    companion object {
        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}