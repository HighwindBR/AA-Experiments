package io.github.highwindbr.aaxp

import android.app.Application
import io.github.highwindbr.aaxp.service.ServiceBridge

class AaExperimentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceBridge.register()
    }
}
