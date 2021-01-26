package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Context as JSContext

private const val name = "autoctx"

class AppAutoContext: AccessibilityService() {
    companion object {
        // current AppAutoContext
        var G: AppAutoContext? = null
            private set

        lateinit var httpClient: HttpClient
        lateinit  var jsContext: JSContext
            private set
        lateinit var jsGlobalScope: ScriptableObject
            private set

        var accessibilityEnabled: Boolean = false
            private set


        var workHandler: Handler
            private set

        private var workThread: HandlerThread = HandlerThread("${TAG}_${name}_thread")
        private lateinit var accessibilityMgr: AccessibilityManager
        private lateinit var notificationMgr: NotificationManager

        // set when first connected to accessibility service
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
        private fun initContext(ctx: Context) {
            if (!inited) {
                accessibilityMgr = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                notificationMgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                httpClient = HttpClient(ctx.applicationContext)
                accessibilityMgr.addAccessibilityStateChangeListener {
                    workHandler.postAtFrontOfQueue { onStateChange(it) }
                }
                inited = true
            }
        }

        private fun onStateChange(enabled: Boolean) {
            Log.i(TAG, "$name: accessibility service state changed to $enabled")
            accessibilityEnabled = enabled
        }

        // dump the top app node recursively in appauto context's work thread
        fun dumpTopActiveApp() {
            val srv = G ?: return;
            workHandler.post {
                val ht = HierarchyTree.from(srv) ?: return@post
                ht.print()
                ht.recycle()
            }
        }

        val topAppHierarchyString: String
            get() {
                if (!accessibilityEnabled) {
                    return "accessibility not enabled"
                }
                return G.getHierarchyString()
            }

        // run the work in appauto context's work thread
        fun runWork(r: Runnable) {
            workHandler.post(r)
        }
    }

   override fun onCreate() {
        super.onCreate()
        initContext(this)
        accessibilityEnabled = accessibilityMgr.isEnabled
        G = this
        Log.i(TAG, "$name: onCreate ${System.identityHashCode(this)} accessibilityEnabled: ${accessibilityMgr.isEnabled}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        Log.i(TAG, "$name: onDestroy ${System.identityHashCode(this)}")
        accessibilityEnabled = false
        G = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "$name: onServiceConnected")
        accessibilityEnabled = true
    }
}