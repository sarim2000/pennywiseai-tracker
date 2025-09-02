package com.pennywiseai.tracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PennyWiseApplication : Application(), Configuration.Provider, DefaultLifecycleObserver {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    companion object {
        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }
    
    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    
    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
    }
    
    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
    }
}