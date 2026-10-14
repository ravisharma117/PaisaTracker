package com.naxits.paisatracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.naxits.paisatracker.utils.IconSwitchingUtils

/**
 * Runs right after the app is updated, so the launcher entry is back before the user looks for it.
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        IconSwitchingUtils.restoreDefaultLauncherIcon(context)
    }
}
