package com.example.testnativeapp

/*
* @author Tkachov Vasyl
* @since 20.07.2021
*/
class Core {

    external fun startLoopback(fileDescriptor: Int)

    external fun playFile(fileDescriptor: Int, filePath: String)

    external fun recordFile(fileDescriptor: Int, filePath: String)

    external fun getExternalStoragePath(): String

    companion object {
        // Used to load the 'native-libs' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}