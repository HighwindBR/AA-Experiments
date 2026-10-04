package io.github.aaexperiments

import android.app.Application
import io.github.aaexperiments.service.ServiceBridge

class AaExperimentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceBridge.register()
    }
}
