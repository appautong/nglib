package cc.appauto.lib.ng

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