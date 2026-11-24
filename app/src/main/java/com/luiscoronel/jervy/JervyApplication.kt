package com.luiscoronel.jervy

import android.app.Application

class JervyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
