package cc.appauto.lib.ng

import android.util.Log
import cc.appauto.lib.ng.AppAutoContext.ERR_NOT_READY
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.faendir.rhino_android.RhinoAndroidHelper
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.commonjs.module.Require
import org.mozilla.javascript.commonjs.module.RequireBuilder
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

object JavascriptRuntime {
    private const val name = "js_runtime"

    internal lateinit var ctx: Context
        private set
    internal lateinit var scopeGlobal: ScriptableObject
    internal lateinit var require: Require
    internal lateinit var requireBuilder: RequireBuilder
        private set

    internal var initialized: Boolean = false
        private set

    internal fun setup(appContext: android.content.Context) {
        if (initialized) return

        AppAutoContext.executor.submitTask {
            if (initialized) return@submitTask
            initialized = true

            Log.i(TAG, "initializing javascript runtime using cache dir: ${AppAutoContext.appContext.filesDir}")
            ctx = RhinoAndroidHelper(AppAutoContext.appContext.filesDir).enterContext()
            ctx.optimizationLevel = -1

            val modules = mutableListOf<String>()
            modules.add(AppAutoContext.appContext.filesDir.path)
            appContext.getExternalFilesDir(null)?.let { modules.add(it.path) }
            Log.i(TAG, "$name: javascript require module path: ${JSON.toJSONString(modules)}")
            requireBuilder = createRequireBuilder(modules, false)

            // make all property in the scopeGlobal sealed
            scopeGlobal = ctx.initStandardObjects(null, true)
            require = installRequire(scopeGlobal, requireBuilder)
            val js = "autox/core.js"
            val content = AppAutoContext.assetManager.open(js).readBytes().decodeToString()
            evaluateJavascript(content, scopeGlobal).also {
                if (it.containsKey("error")) Log.e(TAG, "$name load script $js failed: ${it.toJSONString()}")
                else Log.i(TAG, "$name: load script $js successfully ")
            }
        }
    }

    internal fun createRequireBuilder(modulePath: List<String>, sandbox: Boolean): RequireBuilder {
        val rb = RequireBuilder()
        rb.setSandboxed(sandbox)
        val uris = mutableListOf<URI>()
        modulePath.forEach {
            try {
                var uri = URI(it)
                if (!uri.isAbsolute) {
                    // call resolve("") to canonify the path
                    uri = File(it).toURI().resolve("")
                }
                // make sure URI always ends with slash to avoid loading from unintended locations
                if (!uri.toString().endsWith("/")) uri = URI("$uri/")
                uris.add(uri)
            } catch (e: Exception) {
                Log.e(TAG, "$name: install require with module path $it leads to exception:\n${Log.getStackTraceString(e)}")
            }
        }
        rb.setModuleScriptProvider(
            SoftCachingModuleScriptProvider(
                UrlModuleSourceProvider(uris, null)
            )
        )
        return rb
    }

    // install require on the given scope with require builder
    internal fun installRequire(scope: ScriptableObject, builder: RequireBuilder): Require {
        require = builder.createRequire(ctx, scope)
        require.install(scope)
        return require
    }

    // create a new scope based on given scope object
    private fun newScope(scope: ScriptableObject): Scriptable {
        val obj = ctx.newObject(scope)
        obj.parentScope = null
        obj.prototype = scope
        return obj
    }

    // use reflect to clear exported module cache, so the module will be reloaded from disk
    // at next require
    internal fun resetRequire(require: Require): JSONObject {
        val ret = JSONObject()
        if (!initialized) return ret.also {  it["error"] = ERR_NOT_READY }

        val field = Require::class.java.getDeclaredField("exportedModuleInterfaces")
        field.isAccessible = true
        val m = field.get(require)
        if (m is ConcurrentHashMap<*, *>) {
            m.clear()
            ret["result"] = "clear the exportedModuleInterfaces successfully"
        } else {
            ret["error"] = "can not clear exportedModuleInterfaces: $m"
        }
        return ret
    }


    /** evaluate javascript in work thread. if scope is given, all global changes
     * made by script will be kept in the scope; otherwise use a share scope
     * created from global scope to avoid changes made on the global scope.
     */
    fun evaluateJavascript(content: String, scope: ScriptableObject? = null): JSONObject {
        if (!initialized) {
            val log = "evaluateJavaScript: $ERR_NOT_READY"
            Log.e(TAG, "$name: $log")
            return JSONObject().also { it["error"] = log }
        }
        return AppAutoContext.executor.executeTask {
            val ret = JSONObject()
            try {

                val s = scope ?: newScope(scopeGlobal)
                val obj = ctx.evaluateString(s, content, "<evaluateJavaScript>", -1, null)
                ret["result"] = Context.toString(obj)
            } catch (e: Exception) {
                ret["error"] = "$name: execute script leads to exception: ${Log.getStackTraceString(e)}"
                Log.e(TAG, ret.getString("error"))
            }
            ret
        }
    }

    fun execScript(f: File): JSONObject {
        return try {
            evaluateJavascript(f.readText())
        } catch (e: Exception) {
            val ret = JSONObject()
            ret["error"] = "executeScript leads to exception: ${Log.getStackTraceString(e)}"
            Log.e(TAG, ret.getString("error"))
            ret
        }
    }

    fun execScriptFrom(url: String?): JSONObject {
        var ret = JSONObject()
        if (url.isNullOrEmpty()) {
            ret["error"] = "null or empty url passed"
            return ret
        }
        ret = AppAutoContext.httpClient.get(url)
        if (ret.containsKey("error")) return ret

        val data = ret.getString("result")
        return evaluateJavascript(data)
    }
}

