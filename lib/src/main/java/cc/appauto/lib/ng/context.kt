package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.accessibility.AccessibilityManager
import java.security.InvalidParameterException

class AppAutoContext private constructor(val name: String) {
    private lateinit var appContext: Context
    private lateinit var mgr: AccessibilityManager
    private lateinit var workHandler: Handler
    private lateinit var workThread: HandlerThread

    private var accessibilityEnabled = false

    companion object {
        private val contexts: MutableMap<String, AppAutoContext> = mutableMapOf()

        const val DEFAULT_CONTEXT_NAME = "default_context"
        val defaultContext: AppAutoContext?
            get() = contexts[DEFAULT_CONTEXT_NAME]

        operator fun get(key: String) : AppAutoContext? {
            return contexts.get(key)
        }

        @Synchronized
        fun init(ctx: Context, name: String = DEFAULT_CONTEXT_NAME): AppAutoContext {
            if (contexts.containsKey(name)) throw InvalidParameterException("app context with name <$name> is already existed")
            val obj = AppAutoContext(name)
            obj.appContext = ctx
            obj.mgr = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            obj.workThread = HandlerThread("${TAG}_${obj.name}_thread")
            obj.workThread.start()
            obj.workHandler = Handler(obj.workThread.looper)

            obj.mgr.addAccessibilityStateChangeListener {
                obj.workHandler.postAtFrontOfQueue { obj.onStateChange(it) }
            }
            obj.accessibilityEnabled = obj.mgr.isEnabled
            Log.i(TAG, "$name: accessibilityEnabled: ${obj.mgr.isEnabled}")

            contexts[obj.name] = obj
            return obj
        }
    }

    private fun onStateChange(enabled: Boolean) {
        Log.i(TAG, "$name: accessibility service state changed to $enabled")
        this.accessibilityEnabled =  enabled
    }

    // dump the top app node resurively in appauto context's work thread
    fun dumpTopActiveApp(srv: AccessibilityService) {
        this.workHandler.post {
            val top = getTopAppNode(srv) ?: return@post
            val ht = HierarchyTree.from(top)
            ht.print()
            ht.recycle()
        }
    }

    // run the work in appauto context's work thread
    fun runWork(r: Runnable) {
        this.workHandler.post(r)
    }
}