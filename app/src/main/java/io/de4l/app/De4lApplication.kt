package io.de4l.app

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class De4lApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        context = this
    }

    companion object {
        lateinit var context: Context
            private set
    }

}


