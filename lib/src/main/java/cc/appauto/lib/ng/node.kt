package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.alibaba.fastjson.JSONObject

fun AccessibilityNodeInfo.string(): String {
    val r = this.bound()
    var parent: AccessibilityNodeInfo?

    parent = try {
        this.parent
    } catch (e: Exception) {
        Log.w(TAG, "AccessibilityNodeInfo.string: parent leads to exception: ${Log.getStackTraceString(e)}")
        null
    }

    val id = if (parent != null) "${this.windowId} ${this.viewID()} ${parent.viewID()}" else "${this.windowId} ${this.viewID()} null"
    return "$id ${this.className}, ${this.text}/${this.contentDescription}, v/c/e/s:${this.isVisibleToUser}/${this.isClickable}/${this.isEditable}/${this.isScrollable}, ${r.toShortString()}, ${this.childCount}"
}

fun AccessibilityNodeInfo.click(srv: AccessibilityService?) {
    if (srv == null)  {
        this.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return
    }
    if (srv.touchModeEnabled()) {
        this.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return
    }
    val r = this.bound()
    srv.click(r.centerX(), r.centerY())
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

internal fun AccessibilityNodeInfo._setContent(text: String?) {
    if (text.isNullOrEmpty()) {
        this.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT)
        return
    }
    val b = Bundle()
    b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
    this.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
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
