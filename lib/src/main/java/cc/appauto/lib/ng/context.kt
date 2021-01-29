package cc.appauto.lib.ng

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.accessibility.AccessibilityManager
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Context as JSContext

object AppAutoContext {
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
    lateinit var httpClient: HttpClient
        private set

    lateinit var jsContext: JSContext
        private set

    lateinit var jsGlobalScope: ScriptableObject
        private set

    var workHandler: Handler
        private set

    private var workThread: HandlerThread = HandlerThread("${TAG}_${name}_thread")
    private lateinit var appContext: Context
    private lateinit var accessibilityMgr: AccessibilityManager
    private lateinit var notificationMgr: NotificationManager

    // set when connected to accessibility service or notification listener service
    private var inited = false
    init {
        workThread.start()
        workHandler = Handler(workThread.looper)

        // initialize javascript context in work thread
        workHandler.post {
            jsContext = JSContext.enter()
            // make all Packages in global scope sealed
            jsGlobalScope = jsContext.initStandardObjects(null, true)
            jsContext.optimizationLevel = -1
            Log.i(TAG, "$name: js context inited")
        }
    }

    @Synchronized
    fun initAppContext(ctx: Context) {
        if (!inited) {
            appContext = ctx.applicationContext
            accessibilityMgr = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            notificationMgr = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            httpClient = HttpClient(appContext)
            accessibilityMgr.addAccessibilityStateChangeListener {
                workHandler.postAtFrontOfQueue { onStateChange(it) }
            }
            inited = true
        }
    }

    private fun onStateChange(enabled: Boolean) {
        Log.i(TAG, "$name: accessibility service state changed to $enabled")
        accessibilityConnected = enabled
    }

    // dump the top app node recursively in appauto context's work thread
    fun dumpTopActiveApp() {
        val s = autoSrv ?: return;
        workHandler.post {
            val ht = HierarchyTree.from(s) ?: return@post
            ht.print()
            ht.recycle()
        }
    }

    val topAppHierarchyString: String
        get() {
            if (!accessibilityConnected) {
                return "accessibility not enabled"
            }
            return autoSrv.getHierarchyString()
        }

    // run the work in appauto context's work thread
    fun runWork(r: Runnable) {
        workHandler.post(r)
    }
}