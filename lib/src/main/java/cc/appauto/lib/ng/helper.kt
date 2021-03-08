package cc.appauto.lib.ng

import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.*
import java.lang.Runnable
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
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

// get current date string with given format. e.g.: 2016_03_04_11_23_45
// the default format is 'yyyy_MM_dd_HH_mm_ss
@JvmOverloads
fun getDateStr(format: String = "yyyy_MM_dd_HH_mm_ss"): String {
    val df = SimpleDateFormat(format, Locale.US)
    val c = GregorianCalendar(Locale.CHINA)
    return df.format(c.time)
}

class HandlerExecutor (val name: String): Executor {
    val workThread: HandlerThread = HandlerThread(name)
    val workHandler: Handler
    val dispatcher: CoroutineDispatcher
    val scope: CoroutineScope

    init {
        workThread.start()
        workHandler = Handler(workThread.looper)
        dispatcher = this.asCoroutineDispatcher()
        scope = GlobalScope + dispatcher
    }

    private val inWorkThread: Boolean
        get() = android.os.Process.myTid() == workThread.threadId

    // execute task in work thread and return the result
    fun<V> executeTask(c: Callable<V>): V {
        if (inWorkThread) return c.call()
        return FutureTask<V>(c::call).let {
            execute(it)
            it.get()
        }
    }

    // submit the runnable in work thread and return.
    // if it is already in work thread, execute the runnable immediately
    fun submitTask(r: Runnable) {
        if (inWorkThread) r.run()
        else workHandler.post(r)
    }

    override fun execute(command: Runnable?) {
        command?.let { submitTask(command) }
    }
}