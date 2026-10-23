package com.aipromptbridge

import android.app.Application

class AipbApp : Application() {
    /** Manual dependency container (no DI framework needed at this size). Created once per process. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
