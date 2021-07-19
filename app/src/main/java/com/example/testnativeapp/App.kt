package com.example.testnativeapp

import android.app.Application

/*
* @author Tkachov Vasyl
* @since 20.07.2021
*/public class App : Application() {

    override fun onCreate() {
        super.onCreate()
        if (core == null) {
            core = Core()
        }
    }

    companion object {
        var core: Core? = null
    }
}