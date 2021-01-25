package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

fun AccessibilityService?.getHierarchyString(): String {
    if (this == null) return "null accessibility service"
    val tree = HierarchyTree.from(this) ?: return "null returned by getTopAppNode in HierarchyTree.from"
    return tree.hierarchyString
}

fun AccessibilityService.doGesture(p: Path, start: Long, dur: Long) {
    if(this.serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES == 0) {
        Log.e(TAG, "doGesture:require CAPABILITY_CAN_PERFORM_GESTURES")
        return
    }
    val builder = GestureDescription.Builder()
    builder.addStroke(GestureDescription.StrokeDescription(p, start, dur))
    this.dispatchGesture(builder.build(), null, null)
}

// scroll duration shall not less than 40ms, as it will accelerate the scroll process hugely
fun AccessibilityService.scroll(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, start: Long, dur: Long) {
    val path = Path()
    path.moveTo(xFrom.toFloat(), yFrom.toFloat())
    path.lineTo(xTo.toFloat(), yTo.toFloat())
    Log.v(TAG, "scroll: stroke path from ($xFrom, $yFrom) to ($xTo, $yTo)")
    this.doGesture(path, start, dur)
}

// scroll duration shall not less than 40ms, as it will accelerate the scroll process hugely
fun AccessibilityService.scrollUpDown(isUp: Boolean, screenPercent: Float, duration: Long) {
    if (screenPercent <= 0) {
        return
    }

    val touchMode = this.touchModeEnabled()
    if (touchMode) {
        this.setTouchMode(false)
        sleep(100)
    }

    var percent = screenPercent
    if (screenPercent > 1) {
        percent = 1f
    }
    val wm = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    var p = Point()
    wm.defaultDisplay.getSize(p)
    val height = p.y
    val width = p.x

    val space = (1 - percent)/2
    val x = width / 2
    val yBottom = (height * (1 - space)).toInt()
    val yTop = (height * space).toInt()

    Log.v(TAG, "scroll: space: $space, width: $width, height: $height, yTop: $yTop, yBottom: $yBottom")

    if (isUp) {
        this.scroll(x, yBottom, x, yTop, 0, duration)
    } else {
        this.scroll(x, yTop, x, yBottom, 0, duration)
    }
    if (touchMode) {
        // should sleep duration to wait the asynchronous scroll finish
        sleep(duration)
        this.setTouchMode(true)
    }
}

fun AccessibilityService.touchModeEnabled() : Boolean {
    return (this.serviceInfo.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) != 0
}

fun AccessibilityService.setTouchMode(enable: Boolean) {
    val info = this.serviceInfo
    if (enable) {
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
    } else {
        info.flags = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
    }
    this.serviceInfo = info
    getTopAppNode(this)?.recycle()
}

fun AccessibilityService.click(x: Int, y: Int, start: Long, dur: Long) {
    val path = Path()
    path.moveTo(x.toFloat(), y.toFloat())
    Log.v(TAG, "click point of ($x, $y)")
    this.doGesture(path, start, dur)
}

fun getTopAppNode(srv: AccessibilityService?, packageName: String? = null): AccessibilityNodeInfo? {
    if (srv == null) return null;

    var node: AccessibilityNodeInfo? = null
    var tried = 0
    while(node == null && tried < 3) {
        tried++
        node = srv.rootInActiveWindow
        if (node != null && (packageName == null || packageName == node.packageName)) {
            return node
        }

        node = try {
            srv.windows.find {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root != null && (packageName == null || it.root.packageName == packageName)
            }?.root
        } catch (e: Exception) {
            Log.e(TAG, "getTopAppNode leads to exception: ${e.message}")
            null
        }
        sleep(100)
    }
    return node
}


/** MUST be called in thread other than main UI thread to get the proper result
 * it's strange that there is no input method window in srv.windows
 * however, when check the srv.windows sometime later in main thread,
 * the input method window can be found. I'm guessing that that srv.windows
 * will be refreshed/updated only in main UI thread, so, the current function shall
 * not be call in main thread
 *
 **/
fun getInputIMEWindow(srv: AccessibilityService?) : AccessibilityWindowInfo? {
    if (srv == null) return null

    return try {
        srv.windows.find {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
    } catch (e: Exception) {
        Log.e(TAG, "getInputIMEWindow: ${Log.getStackTraceString(e)}")
        null
    }
}
