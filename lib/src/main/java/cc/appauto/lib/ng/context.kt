package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.app.AppCompatActivity
import cc.appauto.lib.R
import com.alibaba.fastjson.JSONObject

@SuppressLint("StaticFieldLeak")
object AppAutoContext {
    internal const val name = "autoctx"

    const val ERR_NOT_READY = "appauto context not ready: accessibility service is not connected yet"

    var HttpdPort = 8900

    // current accessibility service and notification listener service
    var autoSrv: AccessibilityService? = null
        internal set

    var accessibilityServiceConnected: Boolean = false
        internal set

    var notiSrv: NotificationListenerService? = null
        internal set
    var notificationListenerConnected: Boolean = false
        internal set

    // android application context
    internal lateinit var appContext: Context
    internal var currentActivity: AppCompatActivity? = null

    // flag indicates that whether all the underline runtime of appauto context is initialized
    internal var initialized = false

    internal lateinit var accessibilityMgr: AccessibilityManager
    internal lateinit var notificationMgr: NotificationManager
    internal lateinit var windowManager: WindowManager
    internal lateinit var assetManager: AssetManager
    internal lateinit var mediaProjectionManager: MediaProjectionManager

    internal lateinit var httpd: Httpd
        private set
    internal lateinit var httpClient: HttpClient
        private set

    // separate thread/handler to run the automation javascript
    @JvmField
    var executor = HandlerExecutor("${TAG}_$name")

    // javascript runtime related
    @JvmField
    val jsRuntime = JavascriptRuntime

    // autodraw runtime
    @JvmField
    val autodraw = AutoDraw

    // media runtime
    @JvmField
    val mediaRuntime = MediaRuntime

    // setupRuntime shall be called in onCreate
    @Synchronized
    @JvmStatic
    fun setupRuntime(activity: AppCompatActivity) {
        currentActivity = activity

        // if setupRuntime invoked again after initialized, only update things dependent on activity
        if (initialized) {
            mediaRuntime.setup(activity)
            return
        }

        // things depend on application context will only be initialized once
        val ctx = activity.applicationContext

        appContext = ctx.applicationContext
        accessibilityMgr = appContext.getSystemService(AccessibilityManager::class.java)
        notificationMgr = appContext.getSystemService(NotificationManager::class.java)
        windowManager = appContext.getSystemService(WindowManager::class.java)
        assetManager = appContext.assets
        mediaProjectionManager = appContext.getSystemService(MediaProjectionManager::class.java)

        accessibilityMgr.addAccessibilityStateChangeListener {
            executor.workHandler.postAtFrontOfQueue { onStateChange(it) }
        }


        httpClient = HttpClient(appContext)

        restartHttpd(HttpdPort)

        jsRuntime.setup(appContext)

        autodraw.setup(appContext)

        mediaRuntime.setup(activity)

        initialized = true
        Log.i(TAG, "$name: setup runtime successfully")
    }

    fun restartHttpd(port: Int) {
        if (this::httpd.isInitialized) {
            httpd.closeAllConnections()
            httpd.stop()
        }
        try {
            HttpdPort = port
            httpd = Httpd(appContext, HttpdPort)
            httpd.start()
            Log.i(TAG, "httpd started on $HttpdPort")
        } catch (e: Exception) {
            Log.e(TAG, "start httpd on $HttpdPort failed, ${e.message}")
        }
    }

    @Synchronized
    @JvmStatic
    fun connectAccessibilityService(srv: AccessibilityService?, connected: Boolean = false) {
        autoSrv = srv
        accessibilityServiceConnected = if (srv != null) connected else false
    }

    @Synchronized
    @JvmStatic
    fun connectNotificationListenerService(srv: NotificationListenerService?, connected: Boolean = false) {
        notiSrv = srv
        notificationListenerConnected = if (srv != null) connected else false
    }

    private fun onStateChange(enabled: Boolean) {
        Log.i(TAG, "$name: accessibility service state changed to $enabled")
        accessibilityServiceConnected = enabled
    }

    // dump the top app node recursively in appauto context's work thread
    @JvmStatic
    fun dumpTopActiveApp() {
        val s = autoSrv ?: return
        executor.submitTask {
            HierarchyTree.from(s)?.let {
                Log.i(TAG, "dump top active app, package: ${it.packageName} window: ${it.windowId}")
                it.print()
                it.recycle()
            }
        }
    }

    val topAppHierarchyString
        get() = if (!accessibilityServiceConnected) ERR_NOT_READY else autoSrv.getHierarchyString()


    fun setContent(node: AccessibilityNodeInfo, content: String): JSONObject {
        return node.setContent(content)
    }

    @JvmOverloads
    fun click(node: AccessibilityNodeInfo, useCoordinate: Boolean = false) {
        if (useCoordinate) {
            node.click(autoSrv)
            return
        }
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // scroll duration shall not less than 40ms, as it will accelerate the scroll process hugely
    fun scrollUpDown(isUp: Boolean, screenPercent: Float, duration: Long) {
        autoSrv?.scrollUpDown(isUp, screenPercent, duration)
    }

    @JvmStatic
    fun automatorOf(name: String = "NA"): AppAutomator? {
        val s = autoSrv ?: return null
        return AppAutomator(s, name)
    }

    fun openOverlayPermissionSetting(ctx: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ctx !is Activity) {
            appContext.startActivity(intent)
            return
        }
        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(R.string.appauto_require_permission)
                .setMessage(R.string.appauto_require_overlay_permission)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok) { _, _ -> ctx.startActivity(intent) }
                .setNegativeButton(android.R.string.cancel) { _,_ -> /* no-op */}
                .show()
    }

    fun openAccessibilitySetting(ctx: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ctx !is Activity) {
            ctx.startActivity(intent)
            return
        }
        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(R.string.appauto_require_permission)
                .setMessage(R.string.appauto_require_accessibility_permission)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok) { _, _ -> ctx.startActivity(intent) }
                .setNegativeButton(android.R.string.cancel) { _,_ -> /* no-op */}
                .show()
    }
}