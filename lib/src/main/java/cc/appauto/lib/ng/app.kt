package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

fun bringFront(ctx: Context) {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    am.appTasks[0].moveToFront()
}

fun openApp(srv: AccessibilityService?, packageName: String, timeoutMS: Long = 4000): Boolean {
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
        top = getTopAppNode(srv, packageName)
        if (top != null) {
            top.recycle()
            return true
        }
        sleep(interval)
        // retry open the app
        ctx.startActivity(intent)
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