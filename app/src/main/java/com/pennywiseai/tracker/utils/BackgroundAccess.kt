package com.pennywiseai.tracker.utils

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.pennywiseai.tracker.R
import java.util.Locale

/**
 * Manufacturer battery managers that sit on top of Android's own restrictions.
 * Only Samsung documents its link; the rest are community-sourced, change between
 * OS versions and cannot be probed without declaring `<queries>`, so the how-to
 * always stays on screen and [startFirstAvailable] falls through to App info,
 * where current MIUI, ColorOS and OxygenOS keep the same toggle anyway.
 *
 * Order matters: a Honor phone can report Huawei as its manufacturer.
 */
enum class DeviceOem(
    private val aliases: List<String>,
    @param:StringRes val titleRes: Int,
    @param:StringRes val howToRes: Int,
    private val components: List<String> = emptyList()
) {
    SAMSUNG(
        listOf("samsung"),
        R.string.background_access_oem_title_samsung,
        R.string.background_access_oem_howto_samsung
    ),
    XIAOMI(
        listOf("xiaomi", "redmi", "poco"),
        R.string.background_access_oem_title_autostart,
        R.string.background_access_oem_howto_xiaomi,
        listOf("com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity")
    ),
    HONOR(
        listOf("honor"),
        R.string.background_access_oem_title_autostart,
        R.string.background_access_oem_howto_huawei,
        listOf(
            "com.hihonor.systemmanager/com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager/com.huawei.systemmanager.optimize.process.ProtectActivity"
        )
    ),
    HUAWEI(
        listOf("huawei"),
        R.string.background_access_oem_title_autostart,
        R.string.background_access_oem_howto_huawei,
        listOf(
            "com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager/com.huawei.systemmanager.optimize.process.ProtectActivity"
        )
    ),
    ONEPLUS(
        listOf("oneplus"),
        R.string.background_access_oem_title_autostart,
        R.string.background_access_oem_howto_coloros,
        listOf("com.oneplus.security/com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
    ),
    OPPO(
        listOf("oppo", "realme"),
        R.string.background_access_oem_title_autostart,
        R.string.background_access_oem_howto_coloros,
        listOf(
            "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter/com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity"
        )
    ),
    VIVO(
        listOf("vivo", "iqoo"),
        R.string.background_access_oem_title_autostart,
        R.string.background_access_oem_howto_vivo,
        listOf(
            "com.vivo.permissionmanager/com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure/com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            "com.iqoo.secure/com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
        )
    ),
    TRANSSION(
        listOf("tecno", "infinix", "itel"),
        R.string.background_access_oem_title_autostart,
        R.string.background_access_oem_howto_transsion,
        listOf(
            "com.transsion.phonemaster/com.cyin.himgr.autostart.AutoStartActivity",
            "com.transsion.phonemanager/com.itel.autobootmanager.activity.AutoBootMgrActivity"
        )
    ),
    ASUS(
        listOf("asus"),
        R.string.background_access_oem_title_autostart,
        R.string.background_access_oem_howto_asus,
        listOf(
            "com.asus.mobilemanager/com.asus.mobilemanager.autostart.AutoStartActivity",
            "com.asus.mobilemanager/com.asus.mobilemanager.powersaver.PowerSaverSettings"
        )
    ),

    /** Motorola, Lenovo, Meizu, custom ROMs: no reliable link, App info plus a hint. */
    OTHER(
        emptyList(),
        R.string.background_access_oem_title_autostart,
        R.string.background_access_oem_howto_other
    );

    fun intents(): List<Intent> = when (this) {
        // developer.samsung.com/mobile/app-management.html; 2 = Never sleeping apps
        SAMSUNG -> listOf(
            Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY")
                .setPackage("com.samsung.android.lool")
                .putExtra("activity_type", 2)
        )
        else -> components.map { Intent().setComponent(ComponentName.unflattenFromString(it)) }
    }

    companion object {
        /** These follow stock Android, so the other checklist rows already cover them. */
        private val STOCK_LIKE = listOf("google", "sony", "nothing", "hmd", "nokia")

        fun detect(manufacturer: String, brand: String): DeviceOem? {
            val device = "$manufacturer $brand".lowercase(Locale.ROOT)
            return entries.firstOrNull { oem -> oem.aliases.any(device::contains) }
                ?: OTHER.takeUnless { STOCK_LIKE.any(device::contains) }
        }

        val current: DeviceOem? by lazy { detect(Build.MANUFACTURER, Build.BRAND) }
    }
}

/** What the checklist shows. A null field means the row doesn't apply on this device. */
data class BackgroundAccessState(
    val batteryUnrestricted: Boolean = true,
    val backgroundRestricted: Boolean? = null,
    val unusedRestrictionsStatus: Int? = null,
    val oem: DeviceOem? = null,
    val oemConfirmed: Boolean = false
) {
    val showKeepAccess: Boolean
        get() = unusedRestrictionsStatus != null &&
            unusedRestrictionsStatus != UnusedAppRestrictionsConstants.ERROR &&
            unusedRestrictionsStatus != UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE

    val keepAccessDone: Boolean
        get() = unusedRestrictionsStatus == UnusedAppRestrictionsConstants.DISABLED

    /**
     * Samsung drops an exempted app from Never sleeping apps and from its picker
     * (seen on One UI 9), so there the two steps are alternatives: either one
     * finishes the job and the other one leaves the list.
     */
    val oemIsBatteryAlternative: Boolean
        get() = oem == DeviceOem.SAMSUNG

    val showBatteryStep: Boolean
        get() = batteryUnrestricted || !(oemIsBatteryAlternative && oemConfirmed)

    val showOemStep: Boolean
        get() = oem != null && !(oemIsBatteryAlternative && batteryUnrestricted)

    val stepsLeft: Int
        get() = listOf(
            showBatteryStep && !batteryUnrestricted,
            backgroundRestricted == true,
            showKeepAccess && !keepAccessDone,
            showOemStep && !oemIsBatteryAlternative && !oemConfirmed
        ).count { it }
}

fun Context.isBatteryUnrestricted(): Boolean =
    getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(packageName) == true

/** A separate state from [isBatteryUnrestricted]: the exemption dialog does not clear it. */
fun Context.isBackgroundRestricted(): Boolean? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getSystemService<ActivityManager>()?.isBackgroundRestricted
    } else {
        null
    }

// The system list, not the one-tap request: Play allows REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
// only when Doze breaks the app's core function, and SMS broadcasts still arrive in Doze.
fun Context.batteryUnrestrictedIntents(): List<Intent> = listOf(
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
)

fun Context.canReadSms(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

fun Context.appInfoIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

/**
 * Starts the first intent the device accepts, then App info as the last resort.
 * Trying is the only check available: a component can be missing, not exported
 * or permission-guarded, and package visibility hides all of that from queries.
 */
fun Context.startFirstAvailable(intents: List<Intent> = emptyList()) {
    for (intent in intents + appInfoIntent()) {
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}
