package com.sarim.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.sarim.app.data.AppRepository
import com.sarim.app.security.CommitmentLock
import com.sarim.app.security.MandatoryProtection
import com.sarim.app.utils.LocaleUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class SarimApplication : Application() {

    companion object {
        private lateinit var instance: SarimApplication

        fun getInstance() : SarimApplication {
            return instance
        }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppRepository.invalidateCache()
            applicationScope.launch { AppRepository.getApps() }
        }
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        // Arabic unless the user picks another language in Config.
        LocaleUtils.applyDefaultLocale(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        registerReceiver(packageChangeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })

        applicationScope.launch {
            AppRepository.getApps()
        }

        // The whole protection stack — visual blocking, forbidden words, browser lists, filtering
        // DNS, Tor/torrents and the system restrictions — is always on: this re-asserts it on
        // every launch, including on a fresh install and after an upgrade.
        applicationScope.launch { CommitmentLock.enforce(this@SarimApplication) }
        applicationScope.launch { MandatoryProtection.enforce(this@SarimApplication) }
    }
}
