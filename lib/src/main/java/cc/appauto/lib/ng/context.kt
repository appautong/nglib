package cc.appauto.lib.ng

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import org.mozilla.javascript.Scriptable
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.commonjs.module.Require
import java.util.concurrent.ConcurrentHashMap
import org.mozilla.javascript.Context as JSContext

object AppAutoContext: Executor {
    internal const val name = "autoctx"

    const val ERROR_NOT_READY = "AppAutoContext is not ready: accessibility service is not connected yet"

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
    lateinit var appContext: Context
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
        private set
    internal lateinit var jsRequire: Require
        private set

    // separate thread/handler to run the automation javascript
    private var workThread: HandlerThread = HandlerThread("${TAG}_${name}_thread")
    private var workHandler: Handler
        private set

    private lateinit var accessibilityMgr: AccessibilityManager
    private lateinit var notificationMgr: NotificationManager
    private lateinit var windowManager: WindowManager

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
        runWork {
            initJavascriptRuntime()
            initAutoDrawParam()
        }
    }

    internal fun setupRuntime(ctx: Context) {
        // double check the flag
        if (initialized) return
        runWork {
            if (initialized) return@runWork

            appContext = ctx.applicationContext
            accessibilityMgr = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            notificationMgr = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            httpClient = HttpClient(appContext)
            httpd = Httpd(appContext)
            httpd.start()

            accessibilityMgr.addAccessibilityStateChangeListener {
                workHandler.postAtFrontOfQueue { onStateChange(it) }
            }
            windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            autoDrawRoot = LayoutInflater.from(appContext).inflate(R.layout.autodraw, null)
            autoDrawImage = autoDrawRoot.findViewById(R.id.imgAutoDraw)

            setupAutoDrawOverlay()
            setupJavascriptRequire()
            initialized = true
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

    private fun setupJavascriptRequire() {
        val modules = mutableListOf<String>()
        modules.add(appContext.filesDir.path)
        appContext.getExternalFilesDir(null)?.let { modules.add(it.path) }
        jsRequire = installRequire(modules, false)
        Log.i(TAG, "$name: javascript require installed with module path: ${JSON.toJSONString(modules)}")
    }

    @Synchronized
    private fun setupAutoDrawOverlay() {
        if (autoDrawReady) return

        if (Settings.canDrawOverlays(appContext)) {
            try {
                windowManager.addView(autoDrawRoot, autoDrawWindowParams)
                autoDrawReady = true
            } catch (e: Exception) {
                Log.e(TAG, "checkAndSetupAutoDrawOverlay: add autodraw root leads to exception: ${Log.getStackTraceString(e)}")
            }
        } else {
            openOverlayPermissionSetting(null)
        }
    }

    private fun onStateChange(enabled: Boolean) {
        Log.i(TAG, "$name: accessibility service state changed to $enabled")
        accessibilityConnected = enabled
    }

    // dump the top app node recursively in appauto context's work thread
    fun dumpTopActiveApp() {
        val s = autoSrv ?: return;
        runWork {
            val ht = HierarchyTree.from(s) ?: return@runWork
            ht.print()
            ht.recycle()
        }
    }

    val topAppHierarchyString
        get() = if (!ready) ERROR_NOT_READY else autoSrv.getHierarchyString()

    // run the work in appauto context's work thread
    internal fun runWork(r: Runnable) {
        workHandler.post(r)
    }

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

    // if ctx is corresponding to a activity, show a alert dialog to confirm go to the permission
    // otherwise, goto overlay permission setting directly
    fun openOverlayPermissionSetting(ctx: Context? = null) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ctx == null) {
            appContext.startActivity(intent)
            return
        }
        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(R.string.appauto_require_permission)
                .setMessage(R.string.appauto_require_overlay_permission)
                .setIcon(android.R.drawable.ic_dialog_alert)
        builder.setPositiveButton(android.R.string.yes) { _, _ ->
            ctx.startActivity(intent)
        }.show()
    }

    private fun _executeScript(src: String): JSONObject {
        val ret = JSONObject()
        if (!ready) {
            val log = "executeScript: $ERROR_NOT_READY"
            ret["error"] = log
            Log.e(TAG, "$name: $log")
            return ret
        }
        try {
            val s = newScope(jsGlobalScope)
            val obj = jsContext.evaluateString(s, src, "<executeScript>", -1, null)
            ret["result"] = org.mozilla.javascript.Context.toString(obj)
        } catch (e: Exception) {
            ret["error"] = "execute script leads to exception: ${Log.getStackTraceString(e)}"
            Log.e(TAG, ret.getString("error"))
        }
        return ret
    }

    // execute the javascript in the work thread
    fun executeScript(src: String): JSONObject {
        val tid = android.os.Process.myTid()
        if (tid == workThread.threadId) {
            return _executeScript(src)
        }
        val f = FutureTask {
            _executeScript(src)
        }
        execute(f)

        return f.get()
    }

    override fun execute(command: Runnable?) {
        command?.let { runWork(command) }
    }

    fun resetJavascriptRequire(): JSONObject {
        val ret = JSONObject()
        if (!initialized) return ret.also {  it["error"] = ERROR_NOT_READY}

        val field = Require::class.java.getDeclaredField("exportedModuleInterfaces")
        field.isAccessible = true
        val m = field.get(jsRequire)
        if (m is ConcurrentHashMap<*, *>) {
            m.clear()
            ret["result"] = "clear the exportedModuleInterfaces successfully"
        } else {
            ret["error"] = "can not clear exportedModuleInterfaces: $m"
        }
        return ret
    }
}