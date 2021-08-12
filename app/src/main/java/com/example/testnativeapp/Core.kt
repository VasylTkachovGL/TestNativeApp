package com.example.testnativeapp

/*
* @author Tkachov Vasyl
* @since 20.07.2021
*/
class Core {

    external fun getExternalStoragePath(): String

    external fun recordData(bytes: ByteArray)

    external fun init(fileDescriptor: Int, filePath: String) : Boolean

    companion object {
        // Used to load the 'native-libs' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}