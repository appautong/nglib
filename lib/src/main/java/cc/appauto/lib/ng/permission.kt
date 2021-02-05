package cc.appauto.lib.ng

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.provider.Settings

fun AppAutoContext.checkPermissions(ctx: Context): Boolean {
    return executeTask {
        when {
            !initialized -> {
                openAccessibilitySetting(ctx)
                false
            }
            !Settings.canDrawOverlays(ctx) -> {
                openOverlayPermissionSetting(ctx)
                false
            }
            else -> true
        }
    }
}