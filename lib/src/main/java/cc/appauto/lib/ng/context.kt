package cc.appauto.lib.ng

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import cc.appauto.lib.R
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask

object AppAutoContext: Executor {
    internal const val name = "autoctx"

    const val ERR_NOT_READY = "appauto context not ready: accessibility service is not connected yet"

    // current accessibility service and notification listener service
    var autoSrv: AppAutoService? = null
        internal set
    var accessibilityConnected: Boolean = false
        internal set

    var notiSrv: AppAutoNotificationService? = null
        internal set
    var listenerConnected: Boolean = false
        internal set

    // android application context
    internal lateinit var appContext: Context

    // flag indicates that whether all the underline runtime of appauto context is initialized
    // set when setupRuntime invoked after accessibility service is connected at the first time
    internal var initialized = false

    internal lateinit var accessibilityMgr: AccessibilityManager
    internal lateinit var notificationMgr: NotificationManager
    internal lateinit var windowManager: WindowManager
    internal lateinit var assetManager: AssetManager
    internal lateinit var httpd: Httpd
        private set
    internal lateinit var httpClient: HttpClient
        private set

    // separate thread/handler to run the automation javascript
    private var workThread: HandlerThread = HandlerThread("${TAG}_${name}_thread")
    var workHandler: Handler
        private set

    // javascript runtime related
    val jsRuntime = JavascriptRuntime

    // autodraw runtime
    val autodraw = AutoDraw

    init {
        workThread.start()
        workHandler = Handler(workThread.looper)
    }

    internal fun setupRuntime(ctx: Context) {
        // double check the flag
        if (initialized) return

        submitTask {
            if (initialized) return@submitTask

            appContext = ctx.applicationContext
            accessibilityMgr = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            notificationMgr = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            assetManager = appContext.assets

            accessibilityMgr.addAccessibilityStateChangeListener {
                workHandler.postAtFrontOfQueue { onStateChange(it) }
            }


            httpClient = HttpClient(appContext)
            httpd = Httpd(appContext)
            httpd.start()

            jsRuntime.setup(appContext)
            autodraw.setup(appContext)

            initialized = true
            Log.i(TAG, "$name: setup runtime successfully")
        }
    }

    private fun onStateChange(enabled: Boolean) {
        Log.i(TAG, "$name: accessibility service state changed to $enabled")
        accessibilityConnected = enabled
    }

    // dump the top app node recursively in appauto context's work thread
    fun dumpTopActiveApp() {
        val s = autoSrv ?: return;
        submitTask {
            val ht = HierarchyTree.from(s) ?: return@submitTask
            ht.print()
            ht.recycle()
        }
    }

    val topAppHierarchyString
        get() = if (!initialized) ERR_NOT_READY else autoSrv.getHierarchyString()


    fun automatorOf(name: String = "NA"): AppAutomator? {
        val s = autoSrv ?: return null;
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
                .setNegativeButton(android.R.string.cancel) { _,_ -> {}}
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
                .setNegativeButton(android.R.string.cancel) { _,_ -> {}}
                .show()
    }


    private val inWorkThread: Boolean
        get() = android.os.Process.myTid() == workThread.threadId

    // execute task in appauto context's automation work thread and return the result
    fun<V> executeTask(c: Callable<V>): V {
        if (inWorkThread) {
            return c.call()
        }
        return FutureTask<V> { c.call() }.let {
            execute(it)
            it.get()
        }
    }

    // submit the work in appauto context's automation work thread and return
    // if current thread is already work thread, execute the runnable immediately
    fun submitTask(r: Runnable) {
        if (inWorkThread) r.run()
        else workHandler.post(r)
    }

    override fun execute(command: Runnable?) {
        command?.let { submitTask(command) }
    }
}