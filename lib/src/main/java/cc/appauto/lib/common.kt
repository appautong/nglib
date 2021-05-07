package cc.appauto.lib

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.random.Random

private val r: Random = Random(System.currentTimeMillis())

enum class Position {
    Left, HMiddle, Right,
    Top, VMiddle, Bottom
}

fun sleep(minMS: Long, maxMs: Long = minMS) {
    val num: Long = if (maxMs > minMS) r.nextInt((maxMs - minMS).toInt()) + minMS  else minMS
    try {
        Thread.sleep(num)
    } catch (e: InterruptedException) {
        Log.e(TAG, "sleep($minMS, $maxMs) leads to exception: ${Log.getStackTraceString(e)}")
    }
}

fun Rect.inLeft(parent: Rect, leftEdge:Boolean = true): Boolean {
    val end = parent.width()/3 + parent.left
    val checkPos = if (leftEdge) this.left else this.right
    return checkPos in parent.left .. end
}

fun Rect.inHMiddle(parent: Rect): Boolean {
    val w = parent.width()/3
    val start = parent.left + w
    val end = parent.right - w

    return this.centerX() in start .. end
}

fun Rect.inRight(parent: Rect, rightEdge: Boolean = true): Boolean {
    val start = parent.right - parent.width()/3

    val checkPos = if (rightEdge) this.right else this.left
    return checkPos in start .. parent.right
}

fun Rect.inTop(parent: Rect, topEdge:Boolean = true): Boolean {
    val end = parent.height()/3 + parent.top
    val checkPos = if (topEdge) this.top else this.bottom
    return checkPos in parent.top .. end
}

fun Rect.inVMiddle(parent: Rect): Boolean {
    val h = parent.height()/3
    val start = parent.top + h
    val end = parent.bottom - h

    return this.centerY() in start .. end
}

fun Rect.inBottom(parent: Rect, bottomEdge:Boolean = true): Boolean {
    val start = parent.bottom - parent.height()/3
    val checkPos = if (bottomEdge) this.bottom else this.top
    return checkPos in start .. parent.bottom
}

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

// return random [min, max)
fun randomLong(min: Long, max: Long) : Long {
    return r.nextLong(min, max)
}

// return random [min, max)
fun randomInt(min: Int, max: Int) : Int {
    return r.nextInt(min, max)
}

fun randomFloat(): Float {
    return r.nextFloat()
}
