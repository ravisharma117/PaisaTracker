package com.naxits.paisatracker.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.naxits.paisatracker.MainActivity

object IconSwitchingUtils {
    private const val TAG = "IconSwitchingUtils"

    /**
     * Re-enables the launcher alias after the alternate app icons were removed.
     *
     * Picking an alternate icon used to disable MainActivityOriginal at runtime. That setting
     * survives an app update, so without this a device that had an alternate icon selected would
     * be left with no launcher entry at all.
     */
    fun restoreDefaultLauncherIcon(context: Context) {
        val aliasName = MainActivity::class.java.name.substringBeforeLast('.') + ".MainActivityOriginal"
        val component = ComponentName(context.packageName, aliasName)
        val packageManager = context.packageManager
        try {
            if (packageManager.getComponentEnabledSetting(component) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            ) {
                packageManager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore the default launcher icon", e)
        }
    }
}
