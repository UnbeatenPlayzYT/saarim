package com.sarim.app.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarim.app.data.AppRepository
import com.sarim.app.models.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsViewModel : ViewModel() {
    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            isLoading = true
            apps = withContext(Dispatchers.IO) {
                try {
                    AppRepository.getApps()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            isLoading = false
        }
    }

    fun blockApp(packageName: String, hidden: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppRepository.setAppHidden(packageName, hidden)
                loadApps()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun suspendApp(packageName: String, suspended: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppRepository.setAppSuspended(packageName, suspended)
                loadApps()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setUninstallBlocked(packageName: String, block: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppRepository.setUninstallBlocked(packageName, block)
                loadApps()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setApplicationRestrictions(packageName: String, restrictions: android.os.Bundle) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppRepository.setApplicationRestrictions(packageName, restrictions)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
