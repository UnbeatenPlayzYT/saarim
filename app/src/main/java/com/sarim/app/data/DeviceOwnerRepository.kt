package com.sarim.app.data

import android.accounts.AccountManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.edit
import androidx.core.net.toUri
import com.sarim.app.SarimApplication
import com.sarim.app.utils.ShizukuRunner
import rikka.shizuku.Shizuku
import timber.log.Timber

/** Device-owner setup/state: Shizuku shell commands plus the [DevicePolicyManager] calls they unlock. */
object DeviceOwnerRepository {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private val context = SarimApplication.getInstance()
    private val dpm get() = DeviceAdmin.policyManager
    private val adminName get() = DeviceAdmin.component
    private val packageName = context.packageName
    private val serviceComponent = "$packageName/.SarimAccessibilityService"

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(packageName)

    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) != null
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun installShizuku() {
        val intent = try {
            Intent(Intent.ACTION_VIEW, "market://details?id=$SHIZUKU_PACKAGE".toUri())
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        } catch (e: ActivityNotFoundException) {
            Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE".toUri(),
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        }
        context.startActivity(intent)
    }

    fun openShizukuSetupGuide() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "https://shizuku.rikka.app/guide/setup/".toUri(),
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
    }

    fun checkShizukuPermission(): Boolean {
        if (Shizuku.isPreV11()) return false
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    private val adminPrefs by lazy {
        context.getSharedPreferences(PREFS_ADMIN_RESULT, Context.MODE_PRIVATE)
    }

    /**
     * Re-reads authoritative device-owner state once the setup intent / policy
     * broadcast has landed. Fire-and-forget the Shizuku process, then poll the
     * receiver-persisted result for a bounded window instead of assuming the
     * process-exit code is final.
     */
    fun waitForAdminResult(timeoutMs: Long = ADMIN_RESULT_TIMEOUT_MS, onResult: (Boolean) -> Unit) {
        val start = System.currentTimeMillis()
        var lastShown = false
        while (System.currentTimeMillis() - start < timeoutMs) {
            val received = adminPrefs.getBoolean("admin_result_received", false)
            val enabled = adminPrefs.getBoolean("admin_enabled", false)
            if (received) {
                onResult.invoke(enabled)
                return
            }
            if (!lastShown) {
                lastShown = true
                Timber.tag("DeviceOwnerRepo").d("waiting for admin result")
            }
            Thread.sleep(ADMIN_RESULT_POLL_MS)
        }
        onResult.invoke(false)
    }

    /** Removes device-owner status, or requests it via Shizuku's `dpm set-device-owner` — returns the resulting state.
     *
     * The Shizuku `dpm set-device-owner` runs asynchronously. Poll `isDeviceOwner()` for a short
     * window and return the authoritative result rather than a fire-and-forget check that can
     * false-negative on the very next line. The authoritative confirmation is also delivered to
     * [SarimPolicyUpdateReceiver], so the UI state here is a best-effort convenience.
     */
    fun processDeviceOwnerPrivileges(remove: Boolean = false): Boolean {
        if (remove && dpm.isAdminActive(adminName)) {
            dpm.clearDeviceOwnerApp(packageName)
            adminPrefs.edit {
                putBoolean("admin_result_received", true)
                putBoolean("admin_enabled", false)
                putString("admin_result_package", "")
                putLong("admin_result_at", System.currentTimeMillis())
            }
            return false
        }

        if (!dpm.isDeviceOwnerApp(packageName)) {
            ShizukuRunner.command(
                command = "dpm set-device-owner $packageName/.SarimDeviceAdminReceiver",
                listener = object : ShizukuRunner.CommandResultListener {
                    override fun onCommandResult(output: String, done: Boolean) {
                        println("Output: $output Done: $done")
                    }
                    override fun onCommandError(error: String) {
                        Timber.tag("Shizuku").e(error)
                    }
                })

            // Give the device-owner setup a moment to land before trusting the next check.
            Thread.sleep(250)
        }

        val granted = dpm.isDeviceOwnerApp(packageName)
        adminPrefs.edit {
            putBoolean("admin_result_received", true)
            putBoolean("admin_enabled", granted)
            putString("admin_result_package", packageName)
            putLong("admin_result_at", System.currentTimeMillis())
        }
        return granted
    }

    private const val PREFS_ADMIN_RESULT = "security_prefs"
    private const val ADMIN_RESULT_POLL_MS = 80L
    private const val ADMIN_RESULT_TIMEOUT_MS = 5_000L

    fun setPrivateDNS(host: String): Int {
        return dpm.setGlobalPrivateDnsModeSpecifiedHost(adminName, host)
    }

    fun getPrivateDNS(): String? {
        if (dpm.getGlobalPrivateDnsMode(adminName) != DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME)
            return null
        return dpm.getGlobalPrivateDnsHost(adminName)
    }

    fun getAllAccountsViaShizuku(): List<Pair<String, String>> {
        val accounts = mutableListOf<Pair<String, String>>()
        try {
            ShizukuRunner.command(
                command = "dumpsys account",
                listener = object : ShizukuRunner.CommandResultListener {
                    override fun onCommandResult(output: String, done: Boolean) {
                        val regex = " {4}Account \\{name=(.*?), type=(.*?)\\}".toRegex()

                        output.lines().forEach { line ->
                            val match = regex.find(line)
                            if (match != null) {
                                val accountName = match.groupValues[1]
                                val accountType = match.groupValues[2]
                                accounts.add(Pair(accountType, accountName))
                            }
                        }

                        println("Output: \n$output")
                    }
                    override fun onCommandError(error: String) {
                        Timber.tag("Shizuku").e(error)
                    }
                })

        } catch (e: Exception) {
            Timber.tag("ShizukuError").e(e, "Erro ao buscar contas")
        }
        return accounts
    }

    fun getAppNameFromAccountType(context: Context, accountType: String): String {
        val am = AccountManager.get(context)
        val packManager = context.packageManager

        val authenticators = am.authenticatorTypes

        val auth = authenticators.find { it.type == accountType }

        return if (auth != null) {
            try {
                val appInfo = packManager.getApplicationInfo(auth.packageName, 0)
                packManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                accountType
            }
        } else {
            accountType
        }
    }

    fun getCurrentDeviceOwner(): Pair<String, String>? {
        return try {
            var dpmOutput = ""
            ShizukuRunner.command(
                command = "dpm list-owners",
                listener = object : ShizukuRunner.CommandResultListener {
                    override fun onCommandResult(output: String, done: Boolean) {
                        println("Output: $output Done: $done")
                        dpmOutput = output
                    }

                    override fun onCommandError(error: String) {
                        Timber.tag("Shizuku").e(error)
                    }
                })
            val componentPath = dpmOutput
                .substringAfter("admin=", "")
                .substringBefore(",", "")

            if (componentPath.isEmpty() || !componentPath.contains("/")) return null

            val parts = componentPath.split("/")
            val packageName = parts[0].trim()
            var receiverName = parts[1].trim()

            if (receiverName.startsWith(".")) {
                receiverName = "$packageName/$receiverName"
            }

            Pair(packageName, receiverName)
        } catch (e: Exception) {
            null
        }
    }

    fun getExtraUsersInfo(): List<String> {
        val users = mutableListOf<String>()

        ShizukuRunner.command(
            command = "pm list users",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandResult(output: String, done: Boolean) {
                    println("Output: $output Done: $done")

                    val regex = Regex("""UserInfo\{(\d+):([^:]+):""")

                    val matches = regex.findAll(output)
                    for (match in matches) {
                        val id = match.groupValues[1].toInt()
                        val name = match.groupValues[2]

                        if (id != 0)
                            users.add(name)
                    }
                }

                override fun onCommandError(error: String) {
                    Timber.tag("Shizuku").e(error)
                }
            })

        return users
    }

    fun getAccessibilityServices(): String {
        var services = ""
        ShizukuRunner.command(
            "settings get secure enabled_accessibility_services",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandResult(output: String, done: Boolean) {
                    println("Output: $output Done: $done")
                    services = output
                }

                override fun onCommandError(error: String) {
                    Timber.tag("Shizuku").e(error)
                }
            })
        return services.replace("\n", "")
    }

    fun isAccessibilityGranted(): Boolean {
        return getAccessibilityServices().contains(".SarimAccessibilityService")
    }

    fun changeAccessibilityPermission(grant: Boolean) {
        var services = getAccessibilityServices()
        val isGranted = isAccessibilityGranted()

        if (grant && !isGranted) {
            if (services.isNotEmpty())
                services += ":"
            services += serviceComponent
        }
        else if (!grant && isGranted) {
            services = "'null'"
        }

        ShizukuRunner.command(
            "settings put secure enabled_accessibility_services $services",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandResult(output: String, done: Boolean) {
                    println("Output: $output Done: $done")
                }

                override fun onCommandError(error: String) {
                    Timber.tag("Shizuku").e(error)
                }
            })

        ShizukuRunner.command(
            "settings put secure accessibility_enabled 1",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandResult(output: String, done: Boolean) {
                    println("Output: $output Done: $done")
                }

                override fun onCommandError(error: String) {
                    Timber.tag("Shizuku").e(error)
                }
            })

    }
}
