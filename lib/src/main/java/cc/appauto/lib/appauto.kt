package cc.appauto.lib

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import cc.appauto.lib.*
import com.alibaba.fastjson.JSONObject
import kotlinx.coroutines.Runnable
import java.util.concurrent.locks.ReentrantLock

internal const val TAG = "appauto"
var traceFilter = false
var traceOther = false

class NodeWalker() {
    private var walkDepth = 0

    var autoFilterInvisible = true

    // skip container may slow the efficiency of position filter,
    // as the container may be skipped while not filter out
    var autoSkipContainerWithEmptyText = false
    var traceFind = false
    var traceExpect = false

    var expectRetry: Runnable? = null

    // runtime section, these variables are set at runtime, shall be readonly at external side
    var lastError: String? = null

    var classes: MutableList<String> = mutableListOf()
    val classesStr: String
        get() {return classes.joinToString(",")}

    var ids: MutableList<String> = mutableListOf()
    val idStr: String
        get() {return ids.joinToString("-")}

    private var findMutex: ReentrantLock = ReentrantLock()

    private fun _find(top: AccessibilityNodeInfo, vararg predicates: Filter): AccessibilityNodeInfo? {
        val depth = walkDepth
        if (depth > 20) {
            lastError = "find: abort as walkDepth > $walkDepth"
            Log.w(TAG, lastError!!)
            return null
        }

        for (i in 0 until top.childCount) {
            var child: AccessibilityNodeInfo
            try {
                child = top.getChild(i)
            } catch (_: Exception) {
                continue
            }

            // the id add to ids shall be removed at every exit point of the loop
            ids.add("$i")
            if (!child.className.isNullOrEmpty()) {
                classes.add(child.className.toString())
            } else {
                classes.add("NA")
            }

            if (traceFind) Log.d(TAG, "${ids.joinToString("-")} ${child.string()} is on checking")

            // check visibility, filter out invisible child and its descendant nodes
            if (autoFilterInvisible && !child.isVisibleToUser) {
                if (traceFind) Log.v(TAG, "${ids.joinToString("-")} ${child.string()} skipped due to invisible")
                child.recycle()
                ids.removeAt(ids.lastIndex)
                classes.removeAt(classes.lastIndex)
                continue
            }

            var skip = false
            if (child.text.isNullOrEmpty()) {
                // check container node
                if (autoSkipContainerWithEmptyText && child.childCount > 0) {
                    skip = true
                    if (traceFind) Log.v(TAG, "${ids.joinToString("-")} ${child.string()} skipped due to container with empty text")
                }
            }

            // check current child node
            // should keep indicates that whether current node should be kept and not not free/recycle
            // however, if Abort is returned by any filter, the node will not be kept
            var shouldKeep = false
            if (!skip) {
                var abort = false
                var allMatch = true
                for (p in predicates) {
                    val ret = p.filter(child)
                    if (ret == FilterResult.Abort) {
                        abort = true
                        break
                    }
                    if (ret != FilterResult.Match) allMatch = false
                    if (ret == FilterResult.ContinueKept) shouldKeep = true
                }
                if (abort) {
                    if (traceFind) Log.d(TAG, "${ids.joinToString("-")} ${child.string()} aborted by filter")
                    child.recycle()
                    ids.removeAt(ids.lastIndex)
                    classes.removeAt(classes.lastIndex)
                    continue
                }
                if (allMatch) {
                    if (traceFind) Log.d(TAG, "${ids.joinToString("-")} ${child.string()} matched by all filters")
                    return child
                }
            }

            // recursive traverse the descendant nodes
            walkDepth++
            val node = _find(child, *predicates)
            // restore depth to current depth
            walkDepth = depth
            if (!shouldKeep) {
                child.recycle()
            }
            ids.removeAt(ids.lastIndex)
            classes.removeAt(classes.lastIndex)

            if (node != null) {
                return node
            }
        }
        return null
    }

    fun find(srv: AccessibilityService, vararg predicates: Filter): AccessibilityNodeInfo? {
        val top = getTopAppNode(srv)
        val node = this.find(top, *predicates)
        top?.recycle()
        return node
    }

    fun find(top: AccessibilityNodeInfo?, vararg predicates: Filter): AccessibilityNodeInfo? {
        if (top == null) return null

        findMutex.lock()
        reset()
        ids.add("0")
        if (top.className.isNullOrEmpty()) {
            classes.add("NA")
        } else {
            classes.add(top.className.toString())
        }

        if (predicates.all { it.filter(top) ==  FilterResult.Match}) {
            findMutex.unlock()
            return top
        }

        val ret = _find(top, *predicates)

        findMutex.unlock()
        return ret
    }

    fun findParent(root: AccessibilityNodeInfo?, vararg predicates: Filter): AccessibilityNodeInfo? {
        var next: AccessibilityNodeInfo? = root
        while(next != null) {
            if (predicates.all { it.filter(next!!) ==  FilterResult.Match}) break
            val tmp = next
            next = try { next.parent } catch (_: Exception) { null }

            // recycle intermediate node
            if (tmp != root) {
                tmp.recycle()
            }
        }
        return next
    }

    // this.root is the top node if expect successfully, otherwise, root is recycled
    fun expect(srv:AccessibilityService?, walk: Filter, retryInterval: Long = 1000, retryCount: Int = 3, packageName: String? = null): AccessibilityNodeInfo? {
        var tried = 0

        do {
            tried += 1
            val top = getTopAppNode(srv, packageName)

            if (top == null) {
                if (traceExpect) Log.v(TAG, "expect: continue as top is null, tried: ${tried}")
                if (expectRetry != null)  expectRetry!!.run()
                if (traceExpect) Log.v(TAG, "expect: finished call the expect retry runnable")
                sleep(retryInterval)
                continue
            }
            val node = this.find(top, walk)
            if (node != null) {
                if (top != node) top.recycle()
                if (traceExpect) Log.v(TAG, "expect: tried: $tried, found expected node: ${node.string()}")
                return node
            }
            top.recycle()
            if (traceExpect) Log.v(TAG, "expect: continue as no found, tried: $tried")
            if (expectRetry != null)  expectRetry!!.run()
            sleep(retryInterval)
        } while(tried < (retryCount+1))
        if (traceExpect) Log.v(TAG, "expect: return after tried: $tried without found matched")
        return null
    }

    fun reset() {
        walkDepth = 0
        lastError = null
        ids.clear()
        classes.clear()
    }
}


internal fun doGesture(service: AccessibilityService?, p: Path, start: Long, dur: Long) {
    if (service == null)
        return
    if(service.serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES == 0) {
        Log.w(TAG, "doGesture:require CAPABILITY_CAN_PERFORM_GESTURES")
        return
    }
    val builder = GestureDescription.Builder()
    builder.addStroke(GestureDescription.StrokeDescription(p, start, dur))
    service.dispatchGesture(builder.build(), null, null)
}

// scroll duration shall not less than 40ms, as it will accelerate the scroll process hugely
fun AccessibilityService.scroll(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, start: Long, dur: Long) {
    val path = Path()
    path.moveTo(xFrom.toFloat(), yFrom.toFloat())
    path.lineTo(xTo.toFloat(), yTo.toFloat())
    Log.v(TAG, "scroll: stroke path from ($xFrom, $yFrom) to ($xTo, $yTo)")

    doGesture(this, path, start, dur)
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

    Log.d(TAG, "space: $space, width: $width, height: $height, yTop: $yTop, yBottom: $yBottom")

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

fun AccessibilityNodeInfo.click(service: AccessibilityService?) {
    if (service == null)  {
        this.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return
    }
    if (service.touchModeEnabled()) {
        this.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return
    }
    val r = this.bound()
    click(service, r.centerX(), r.centerY(), 0, 10)
}

// pass null/empty text to clear current text
fun AccessibilityNodeInfo.setContent(content: String?, retryInterval: Long = 1000, retryCount: Int = 3): JSONObject {
    val ret = JSONObject()
    if (content.isNullOrEmpty()) {
        this.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT)
        ret["result"] = "content cleared"
        return ret
    }

    var cnt = 0

    do {
        cnt++
        this._setContent(content)
        this.refresh()
        if (traceOther) Log.v(TAG, "setContent: curr ${this.text} <---> $content ")
        if (!this.text.isNullOrEmpty() && this.text.toString() == content) {
            break
        }
        sleep(retryInterval)
    } while(cnt < retryCount + 1)

    if (cnt > retryCount) {
        ret["error"] = "content not set content of ${this.string()} to $content after tried $cnt times"
        return ret
    }
    ret["result"] = "success"
    return ret
}

fun AccessibilityNodeInfo.findClickableParent(): AccessibilityNodeInfo? {
    val w = NodeWalker()
    return w.findParent(this, newBoolFilter { it.isClickable })
}

fun AccessibilityNodeInfo._setContent(text: String?) {
    if (text.isNullOrEmpty()) {
        this.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT)
        return
    }
    val b = Bundle()
    b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
    this.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    if (traceOther) Log.v(TAG, "${this.string()}: set text to ${text}")
}

fun click(service: AccessibilityService?, x: Int, y: Int, start: Long, dur: Long) {
    val path = Path()
    path.moveTo(x.toFloat(), y.toFloat())
    Log.v(TAG, "click: point ($x, $y)")

    doGesture(service, path, start, dur)
}

fun AccessibilityService.doubleClick(x: Int, y: Int) {
    val builder = GestureDescription.Builder()
    val path = Path()
    path.moveTo(x.toFloat(), y.toFloat())
    Log.v(TAG, "double click: point ($x, $y)")
    builder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
    builder.addStroke(GestureDescription.StrokeDescription(path, 200, 100))
    this.dispatchGesture(builder.build(), null, null)
}

fun AccessibilityService.getHierarchyString(): String {
    val top = getTopAppNode(this)
    val ret = top.getHierarchyString()
    top?.recycle()
    return ret
}

fun AccessibilityService.tryRootInActiveWindow(): AccessibilityNodeInfo? {
    return try {
        this.rootInActiveWindow
    } catch (e: Exception) {
        Log.e(TAG, "tryRootInActiveWindow: exception occurred ${e.message}")
        null
    }
}

fun AccessibilityNodeInfo?.getHierarchyString(): String {
    if (this == null)
        return ""

    val sb = StringBuilder()
    val w = NodeWalker()
    w.find(this, newFilter{
        sb.append("${it.string()}\n")
        FilterResult.Continue
    })
    return sb.toString()
}

fun AccessibilityNodeInfo.bound(): Rect {
    val rect = Rect()
    this.getBoundsInScreen(rect)
    return rect
}

/**
 * return "null" if viewIdResourceName is null; otherwise, the parts after "id/"
 */
fun AccessibilityNodeInfo.viewID(): String? {
    return if (viewIdResourceName != null)
        viewIdResourceName.substringAfter(":").removePrefix("id/")
    else
        null
}

// AccessibilityNodeInfo.parent in kotlin may leads to exception is the returned parent is null
// as kotlin declare the return value as AccessibilityNodeInfo!!
fun AccessibilityNodeInfo.tryParent(): AccessibilityNodeInfo? {
  return try {
    this.parent
  } catch (e: Exception) {
    Log.w(TAG, "parent for ${this.string()} leads to exception: ${e.message}")
    null
  }
}

// AccessibilityNodeInfo.getChild in kotlin may leads to exception is the returned child is null
// as kotlin declare the return value of as AccessibilityNodeInfo!!
fun AccessibilityNodeInfo.tryGetChild(idx: Int): AccessibilityNodeInfo? {
  return try {
    this.getChild(idx)
  } catch (e: Exception) {
    Log.w(TAG, "child($idx) for ${this.string()} leads to exception: ${e.message}")
    null
  }
}

fun AccessibilityNodeInfo.tryRecycle() {
    try {
        this.recycle()
    } catch (e: Exception) {
        Log.w(TAG, "recycle leads to exception:\n${Log.getStackTraceString(e)}")
    }
}
