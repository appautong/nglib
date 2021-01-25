package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.accessibility.AccessibilityManager
import java.security.InvalidParameterException

import org.mozilla.javascript.Context as JSContext
import org.mozilla.javascript.ScriptableObject

class AppAutoContext private constructor(val name: String, val appContext: Context) {
    var workHandler: Handler
        private set

    var srv: AccessibilityService? = null

    var accessibilityEnabled: Boolean
        get() = if (srv == null) false
        else field
        private set

    var jsContext: JSContext
        private set

    var jsGlobalScope: ScriptableObject
        private set

    val httpClient: HttpClient = HttpClient(appContext)

    private var mgr: AccessibilityManager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private var workThread: HandlerThread = HandlerThread("${TAG}_${name}_thread")

    init {
        workThread.start()
        workHandler = Handler(workThread.looper)
        mgr.addAccessibilityStateChangeListener {
            workHandler.postAtFrontOfQueue { onStateChange(it) }
        }
        accessibilityEnabled = mgr.isEnabled
        Log.i(TAG, "$name: accessibilityEnabled: ${mgr.isEnabled}")

        jsContext = JSContext.enter()

        // make all Packages in global scope sealed
        jsGlobalScope = jsContext.initStandardObjects(null, true)
        jsContext.optimizationLevel = -1

        Log.i(TAG, "$name: init the js context")
    }

    private var closed = false

    @Synchronized
    fun close() {
        if (closed) return

        closed = true
        JSContext.exit()
        httpClient.close()
    }

    protected fun finalize() {
       close()
    }

    companion object {
        private val contexts: MutableMap<String, AppAutoContext> = mutableMapOf()

        const val DEFAULT_CONTEXT_NAME = "default_context"
        val defaultContext: AppAutoContext?
            get() = contexts[DEFAULT_CONTEXT_NAME]

        operator fun get(key: String) : AppAutoContext? {
            return contexts.get(key)
        }

        @Synchronized
        fun of(ctx: Context, name: String = DEFAULT_CONTEXT_NAME): AppAutoContext {
            if (contexts.containsKey(name)) throw InvalidParameterException("app context with name <$name> is already existed")
            val obj = AppAutoContext(name, ctx)

            contexts[obj.name] = obj
            return obj
        }
    }

    private fun onStateChange(enabled: Boolean) {
        Log.i(TAG, "$name: accessibility service state changed to $enabled")
        this.accessibilityEnabled = enabled
    }

    // dump the top app node resurively in appauto context's work thread
    fun dumpTopActiveApp(srv: AccessibilityService) {
        this.workHandler.post {
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
            return srv.getHierarchyString()
        }

    // run the work in appauto context's work thread
    fun runWork(r: Runnable) {
        this.workHandler.post(r)
    }
}