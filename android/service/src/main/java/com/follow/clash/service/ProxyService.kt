package com.follow.clash.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.follow.clash.common.GlobalState
import com.follow.clash.core.Core
import com.follow.clash.service.modules.ServiceModules
import com.follow.clash.service.transparent.TransparentProxyController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProxyService : Service(), ManagedService {
    private val modules = ServiceModules(this)
    private val binder = LocalBinder()

    override fun onDestroy() {
        try {
            cleanup()
        } finally {
            super.onDestroy()
        }
    }

    override fun onLowMemory() {
        Core.forceGC()
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        trimCoreMemory(level)
        super.onTrimMemory(level)
    }

    inner class LocalBinder : Binder() {
        val service: ProxyService
            get() = this@ProxyService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun start() {
        try {
            modules.start()
        } catch (error: Exception) {
            stop()
            throw error
        }
    }

    override fun stop() {
        try {
            cleanup()
        } finally {
            stopSelf()
        }
    }

    // `su` can take seconds and this runs on the main thread, so the rules go
    // out on a worker while the modules stop right away.
    private fun cleanup() {
        try {
            GlobalState.launch {
                withContext(Dispatchers.IO) {
                    TransparentProxyController.remove()
                }
            }
        } finally {
            modules.stop()
        }
    }
}
