package cc.appauto.lib.ng

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import cc.appauto.lib.R
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.commonjs.module.Require
import org.mozilla.javascript.commonjs.module.RequireBuilder
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import org.mozilla.javascript.Context as JSContext

object AppAutoContext: Executor {
    internal const val name = "autoctx"

    const val ERROR_NOT_READY = "AppAutoContext is not ready: accessibility service is not connected yet"

    // whether automation ready
    val ready
        get() = initialized && accessibilityConnected

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
    var initialized = false

    lateinit var httpd: Httpd
        private set
    lateinit var httpClient: HttpClient
        private set

    // javascript runtime related
    internal lateinit var jsContext: JSContext
        private set
    internal lateinit var jsGlobalScope: ScriptableObject
    internal lateinit var jsRequire: Require
    internal lateinit var jsRequireBuilder: RequireBuilder
        private set

    // separate thread/handler to run the automation javascript
    private var workThread: HandlerThread = HandlerThread("${TAG}_${name}_thread")
    var workHandler: Handler
        private set

    private lateinit var accessibilityMgr: AccessibilityManager
    private lateinit var notificationMgr: NotificationManager
    private lateinit var windowManager: WindowManager
    private lateinit var assetManager: AssetManager

    // autodraw related
    lateinit var autoDrawImage: AutoDraw
        private set
    var autoDrawReady = false
        private set
    private lateinit var autoDrawRoot: View
    private var autoDrawWindowParams = WindowManager.LayoutParams()

    init {
        workThread.start()
        workHandler = Handler(workThread.looper)

        // initialize javascript context in work thread
        submitTask {
            initJavascriptRuntime()
            initAutoDrawParam()
        }
    }

    internal fun setupRuntime(ctx: Context) {
        // double check the flag
        if (initialized) return
        submitTask {
            if (initialized) return@submitTask
            initialized = true

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

            autoDrawRoot = LayoutInflater.from(appContext).inflate(R.layout.autodraw, null)
            autoDrawImage = autoDrawRoot.findViewById(R.id.imgAutoDraw)

            setupAutoDrawOverlay()
            setupJavascriptRuntime()
            Log.i(TAG, "$name: setup context related runtime successfully")
        }
    }

    private fun initJavascriptRuntime() {
        jsContext = JSContext.enter()
        // make all Packages in global scope sealed
        jsGlobalScope = jsContext.initStandardObjects(null, true)
        jsContext.optimizationLevel = -1
        Log.i(TAG, "$name: js context inited")
    }

    private fun initAutoDrawParam() {
        // initialize the window parameters for the autodraw view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            autoDrawWindowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            autoDrawWindowParams.type = WindowManager.LayoutParams.TYPE_PHONE
        }
        autoDrawWindowParams.gravity = Gravity.START or Gravity.TOP
        autoDrawWindowParams.format = PixelFormat.RGBA_8888
        autoDrawWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        autoDrawWindowParams.width = WindowManager.LayoutParams.MATCH_PARENT
        autoDrawWindowParams.height = WindowManager.LayoutParams.MATCH_PARENT
        autoDrawWindowParams.x = 0
        autoDrawWindowParams.y = 0
    }

    // create and install require on the given scope, and load autox/core.js
    internal fun setupJavascriptScope(scope: ScriptableObject)  {
        jsRequire = jsRequireBuilder.createRequire(jsContext, scope)
        jsRequire.install(scope)

        evaluateJavascript(assetManager.open("autox/core.js").readBytes().decodeToString(), scope).also {
            if (it.containsKey("error")) Log.e(TAG, "$name: load autojs/apptuo.js: ${it.toJSONString()}")
            else Log.i(TAG, "$name: load core js files successfully ")
        }
    }

    private fun setupJavascriptRuntime() {
        val modules = mutableListOf<String>()
        modules.add(appContext.filesDir.path)
        Log.i(TAG, "$name: javascript require module path: ${JSON.toJSONString(modules)}")

        appContext.getExternalFilesDir(null)?.let { modules.add(it.path) }
        jsRequireBuilder = createRequireBuilder(modules, false)

        setupJavascriptScope(jsGlobalScope)
    }

    /*** setup overlay draw
     *  return true draw overlay permission acquired and the auto draw root view added;
     *  otherwise, return false
     */
    @Synchronized
    internal fun setupAutoDrawOverlay(): Boolean {
        if (autoDrawReady) return true

        if (!initialized) {
            Log.w(TAG, "$name: setupAutoDrawOverlay: $ERROR_NOT_READY")
            return false
        }

        if (!Settings.canDrawOverlays(appContext)) return false

        try {
            windowManager.addView(autoDrawRoot, autoDrawWindowParams)
            autoDrawReady = true
            Log.i(TAG, "$name: setupAutoDrawOverlay successfully")
        } catch (e: Exception) {
            Log.e(TAG, "$name: setupAutoDrawOverlay: add autodraw root leads to exception: ${Log.getStackTraceString(e)}")
        }
        return true
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
        get() = if (!ready) ERROR_NOT_READY else autoSrv.getHierarchyString()


    fun markBound(bound: Rect, paint: Paint? = null): JSONObject {
        val ret = JSONObject()
        if (!ready) {
            return ret.also { it["error"] = ERROR_NOT_READY }
        }
        if (!autoDrawReady)  {
            return ret.also { it["error"] = "need draw overlay permission, invoke openOverlayPermissionSetting first"}
        }
        autoDrawImage.drawRectStroke(bound, paint)
        return ret.also { ret["result"] = "success" }
    }

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


    // execute task in appauto context's automation work thread and return the result
    fun<V> executeTask(c: Callable<V>): V {
        val tid = android.os.Process.myTid()
        if (tid == workThread.threadId) {
            return c.call()
        }
        return FutureTask<V> { c.call() }.let {
            execute(it)
            it.get()
        }
    }

    // submit the work in appauto context's automation work thread and return immediately
    fun submitTask(r: Runnable) {
        workHandler.post(r)
    }

    override fun execute(command: Runnable?) {
        command?.let { submitTask(command) }
    }
}