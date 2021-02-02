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
import com.alibaba.fastjson.JSONObject
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import org.mozilla.javascript.Context as JSContext

object AppAutoContext: Executor {
    private const val name = "autoctx"

    // current accessibility service and notification listener service
    var autoSrv: AppAutoService? = null
        internal set
    var accessibilityConnected: Boolean = false
        internal set

    var notiSrv: AppAutoNotificationService? = null
        internal set
    var listenerConnected: Boolean = false
        internal set

    // android application context related
    lateinit var appContext: Context
    // set when connected to accessibility service or notification listener service
    var appContextInited = false

    lateinit var httpClient: HttpClient
        private set

    internal lateinit var jsContext: JSContext
        private set

    internal lateinit var jsGlobalScope: ScriptableObject
        private set

    var workHandler: Handler
        private set

    private var workThread: HandlerThread = HandlerThread("${TAG}_${name}_thread")
    private lateinit var accessibilityMgr: AccessibilityManager
    private lateinit var notificationMgr: NotificationManager
    private lateinit var windowManager: WindowManager
    private lateinit var autoDrawRoot: View
    private lateinit var autoDrawImage: AutoDraw
    private var autoDrawWindowParams = WindowManager.LayoutParams()
    private var autoDrawReady = false

    init {
        workThread.start()
        workHandler = Handler(workThread.looper)

        // initialize javascript context in work thread
        runWork {
            jsContext = JSContext.enter()
            // make all Packages in global scope sealed
            jsGlobalScope = jsContext.initStandardObjects(null, true)
            jsContext.optimizationLevel = -1
            Log.i(TAG, "$name: js context inited")
        }

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

    @Synchronized
    fun initAppContext(ctx: Context) {
        if (!appContextInited) {
            appContext = ctx.applicationContext
            accessibilityMgr = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            notificationMgr = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            httpClient = HttpClient(appContext)
            accessibilityMgr.addAccessibilityStateChangeListener {
                workHandler.postAtFrontOfQueue { onStateChange(it) }
            }

            windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            autoDrawRoot = LayoutInflater.from(appContext).inflate(R.layout.autodraw, null)
            autoDrawImage = autoDrawRoot.findViewById(R.id.imgAutoDraw)

            setupAutoDrawOverlay()

            appContextInited = true
        }
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

    val topAppHierarchyString: String
        get() {
            if (!accessibilityConnected) {
                return "accessibility service not connected yet"
            }
            return autoSrv.getHierarchyString()
        }

    // run the work in appauto context's work thread
    internal fun runWork(r: Runnable) {
        workHandler.post(r)
    }

    fun markBound(bound: Rect, paint: Paint? = null) {
        if (!appContextInited) {
            Log.w(TAG, "$name: markBound: context is not initialized, please retry after accessibility service connected")
            return
        }
        if (!autoDrawReady)  {
            setupAutoDrawOverlay()
            return
        }
        autoDrawImage.drawRectStroke(bound, paint)
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
        if (!appContextInited) {
            val log = "executeScript: context is not initialized, please retry after accessibility service connected"
            ret["error"] = log
            Log.e(TAG, "$name: $log")
            return ret
        }
        try {
            val s = newScope(jsGlobalScope)
            val obj = jsContext.evaluateString(s, src, "<execute>", -1, null)
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
        Log.i(TAG, "$name: executeScript, tid: $tid, work thread id: ${workThread.threadId}")
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
}