package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.app.NotificationCompat
import cc.appauto.lib.R

fun AccessibilityService?.getHierarchyString(): String {
    if (this == null) return "null accessibility service"
    val tree = HierarchyTree.from(this) ?: return "null returned by getTopAppNode in HierarchyTree.from"
    return tree.hierarchyString
}

// check gesture capability first then dispatch builder.build()
private fun AccessibilityService.doGesture(builder: GestureDescription.Builder) {
    if(this.serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES == 0) {
        Log.e(TAG, "doGesture:require CAPABILITY_CAN_PERFORM_GESTURES")
        return
    }
    this.dispatchGesture(builder.build(), null, null)
}

// scroll duration shall not less than 40ms, as it will accelerate the scroll process hugely
fun AccessibilityService.scroll(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, start: Long, dur: Long) {
    val path = Path()
    path.moveTo(xFrom.toFloat(), yFrom.toFloat())
    path.lineTo(xTo.toFloat(), yTo.toFloat())
    Log.v(TAG, "scroll: stroke path from ($xFrom, $yFrom) to ($xTo, $yTo)")
    val builder = GestureDescription.Builder()
    builder.addStroke(GestureDescription.StrokeDescription(path, start, dur))

    this.doGesture(builder)
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
    val p = Point()
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

fun AccessibilityService.click(x: Int, y: Int, times: Int = 1) {
    val path = Path()
    path.moveTo(x.toFloat(), y.toFloat())
    Log.v(TAG, "click point of ($x, $y) $times times")
    val builder = GestureDescription.Builder()

    for (i in 0 until times) {
        builder.addStroke(GestureDescription.StrokeDescription(path, (i*200).toLong(), 100))
    }
    this.doGesture(builder)
}

fun getTopAppNode(srv: AccessibilityService?, packageName: String? = null): AccessibilityNodeInfo? {
    if (srv == null) return null

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

class AppAutoAccessibilityService: AccessibilityService() {
    private val name = "appauto_accessibility_service"
    override fun onCreate() {
        super.onCreate()

        AppAutoContext.autoSrv = this
        Log.i(TAG, "$name: onCreate ${System.identityHashCode(this)}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        Log.i(TAG, "$name: onDestroy ${System.identityHashCode(this)}")
        AppAutoContext.accessibilityConnected = false
        AppAutoContext.autoSrv = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "$name: onServiceConnected")
        AppAutoContext.accessibilityConnected = true
    }
}

class AppAutoNotificationService: NotificationListenerService() {
    private val name = "appauto_noti_service"

    override fun onCreate() {
        super.onCreate()

        AppAutoContext.notiSrv = this
        Log.i(TAG, "$name: onCreate ${System.identityHashCode(this)}")
    }

    override fun onDestroy() {
        super.onDestroy()

        AppAutoContext.notiSrv = null
        AppAutoContext.listenerConnected = false
        Log.i(TAG, "$name: onDestroy ${System.identityHashCode(this)}")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
    }

    override fun onListenerConnected() {
        Log.i(TAG, "$name: on listener connected ${System.identityHashCode(this)}")
        AppAutoContext.listenerConnected = true
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "$name: on listener disconnected ${System.identityHashCode(this)}")
        AppAutoContext.listenerConnected = false
    }
}

class AppAutoMediaService: Service() {
    private val name = "appauto_media_service"
    private val channelId = "default channel"
    private var mediaProjection: MediaProjection? = null
    private var surface: Surface? = null
    private val useMediaCodec = false

    private var display: VirtualDisplay? = null

    val foregroundNotificatioinId = 1

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "$name: onStartCommand(${System.identityHashCode(this)}): $intent $flags $startId")
        if (intent == null) {
            Log.w(TAG, "$name: onStartCommand with null intent, skipping...")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!intent.hasExtra("resultCode") || !intent.hasExtra("data") ) {
            Log.w(TAG, "$name: onStartCommand with invalid intent, missing mandatory fields: resultCode and data, skipping...")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        val data = intent.getParcelableExtra<Intent>("data")

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "$name: onStartCommand with invalid intent, resultCode: $resultCode, data: $data, skipping...")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val noti = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle(getString(R.string.appauto_media_service_notification_title))
            .setContentText(getString(R.string.appauto_media_service_notification_content))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, getString(R.string.appauto_media_service_default_notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            AppAutoContext.notificationMgr.createNotificationChannel(channel)
        }

        // start foreground before acquiring the media projection as it requires foreground service context
        startForeground(foregroundNotificatioinId, noti)
        if (mediaProjection != null) stopMediaProjection()

        mediaProjection = AppAutoContext.mediaProjectionManager.getMediaProjection(resultCode, data)
        Log.i(TAG, "$name: onStartCommand, acquired media projection: $mediaProjection")

        if (mediaProjection != null) {
            startMediaProjection()
        }
        return START_NOT_STICKY
    }

    fun pauseMediaProjection() {
        display?.surface = null
    }

    fun resumeMediaProjection() {
        Log.i(TAG, "$name: resume media projection")
        display?.surface = surface
    }

    fun stopMediaProjection() {
        Log.i(TAG, "$name: stop media projection")
        pauseMediaProjection()
        display?.release()
        display = null

        if (useMediaCodec) {
            MediaRuntime.releaseVideoEncoder()
            surface?.release()
        }

        surface = null
    }

    fun startMediaProjection() {
        Log.i(TAG, "$name: start media projection")
        if (useMediaCodec) {
            MediaRuntime.setupVideoEncoder()
            surface = MediaRuntime.codec?.createInputSurface()
        } else {
            surface = MediaRuntime.imageReader.surface
        }
        display = mediaProjection?.createVirtualDisplay(
            "screenshot",
            MediaRuntime.displayMetrics.widthPixels,
            MediaRuntime.displayMetrics.heightPixels,
            MediaRuntime.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )
        if (useMediaCodec) MediaRuntime.codec?.start()
    }

    override fun onCreate() {
        Log.i(TAG, "$name: onCreate(${System.identityHashCode(this)})")
    }

    override fun onDestroy() {
        Log.i(TAG, "$name: onDestroy(${System.identityHashCode(this)})")
    }
}