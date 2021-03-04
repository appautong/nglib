package cc.appauto.lib.ng

import android.app.AlertDialog
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import cc.appauto.lib.R

fun AppAutoContext.checkPermissions(activity: AppCompatActivity): Boolean {
    if (accessibilityConnected && Settings.canDrawOverlays(activity)) return true

    val builder = AlertDialog.Builder(activity)
    val diag = builder.setTitle(R.string.appauto_check_permission)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setCancelable(false)
        .create()
    diag.show()

    executor.workHandler.postDelayed({
        diag.dismiss()
    }, 2000)

    return executor.executeTask {
        when {
            !accessibilityConnected -> {
                openAccessibilitySetting(activity)
                false
            }
            !Settings.canDrawOverlays(activity) -> {
                openOverlayPermissionSetting(activity)
                false
            }
            else -> true
        }
    }
}