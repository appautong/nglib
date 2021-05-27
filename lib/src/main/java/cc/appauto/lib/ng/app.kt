package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import cc.appauto.lib.R

fun bringFront(ctx: Context) {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    am.appTasks[0].moveToFront()
}

@JvmOverloads
fun openApp(srv: AccessibilityService?, packageName: String, isDualApp: Boolean=false, timeoutMS: Long = 4000): Boolean {
    if (srv == null) return false

    val ctx = srv.applicationContext

    var now = System.currentTimeMillis()
    val deadline = now + timeoutMS
    var interval = timeoutMS.shr(3)
    if (interval < 500) interval = 500

    val intent = ctx.packageManager.getLaunchIntentForPackage(packageName) ?: return false

    ctx.startActivity(intent)

    var top: AccessibilityNodeInfo?
    do {
        top = getTopAppNode(srv)
        var retryStartActivity = true

        if (top != null) {
            if (top.packageName == packageName) {
                top.recycle()
                return true
            }
            // check whether there are dual app opening prompt
            val tree = HierarchyTree.from(top)
            var res = tree.classHierarchySelector("${ClassName.TextView}").text("请选择要使用的应用")
            if (res.isNotEmpty()) {
                retryStartActivity = false
                res = tree.classHierarchySelector("${ClassName.Linearlayout} > ${ClassName.ImageView}")
                if (res.size != 2) {
                    Log.w(TAG, "unexpected number (${res.size} of app found in the app selection ui, expect number is 2")
                    tree.recycle()
                    return false
                }

                val node  = if (isDualApp) {
                    res.contentDescription("双开").clickableParent().firstOrNull()
                } else {
                    res.selector {
                        when {
                            it.contentDescription == null -> false
                            it.contentDescription!!.contains("双开") -> false
                            else -> true
                        }
                    }.clickableParent().firstOrNull()
                }

                if (node != null) {
                    tree.getAccessibilityNodeInfo(node)?.click(null)
                } else {
                    Log.w(TAG, "can not find app to be opened in app selection UI: $res")
                }
            }
            tree.recycle()
        }
        sleep(interval)
        // retry open the app
        if (retryStartActivity) ctx.startActivity(intent)
        now = System.currentTimeMillis()
    } while (now < deadline)
    return false
}

fun quitApp(service: AccessibilityService?, packageName: String, maxBackCount : Int = 10) {
    if (service == null)
        return

    var tmpNode = getTopAppNode(service, packageName)
    var backed = 0
    while (tmpNode != null && backed < maxBackCount) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        tmpNode.recycle()
        backed++
        sleep(200)
        tmpNode = getTopAppNode(service, packageName)
    }
    tmpNode?.recycle()

    if (backed == maxBackCount) {
        Log.w(TAG, "backed $backed times without quit $packageName, press home")
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }
}


private var cachedAppWinRect: Rect? = null

fun getAppBound(srv: AccessibilityService?): Rect? {
    if (cachedAppWinRect != null) return cachedAppWinRect

    if (srv == null) return null

    val ret = Rect()

    val wm = srv.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val p = Point()
    wm.defaultDisplay.getRealSize(p)

    ret.left = 0
    ret.right = p.x

    // enumerate the windows to exclude any top/bottom navigation bar
    srv.windows.forEach {
        if (it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.layer > 0) {
            val r = Rect()
            it.getBoundsInScreen(r)
            if (r.top == 0) {
                ret.top = r.bottom
            } else if (r.bottom == p.y) {
                ret.bottom = r.top
            }
        }
    }
    cachedAppWinRect = ret

    return ret
}

/***
 * ctx: if ctx is not activity instance, showConfirmDialog param is useless and
 * confirm dialogue can not be showed.
 */
@JvmOverloads
fun openSetting(ctx: Context, action: String, showConfirmDialog: Boolean = false, confirmMessage: String) {
    val intent = Intent(action)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!(ctx is Activity && showConfirmDialog)) {
        AppAutoContext.appContext.startActivity(intent)
        return
    }
    val builder = AlertDialog.Builder(ctx)
    builder.setTitle(R.string.appauto_require_permission)
        .setMessage(confirmMessage)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setPositiveButton(android.R.string.ok) { _, _ -> ctx.startActivity(intent) }
        .setNegativeButton(android.R.string.cancel) { _,_ -> /* no-op */}
        .show()
}