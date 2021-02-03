package cc.appauto.lib.ng

import android.util.Log
import com.alibaba.fastjson.JSONObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.commonjs.module.Require
import org.mozilla.javascript.commonjs.module.RequireBuilder
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider
import java.io.File
import java.net.URI

fun AppAutoContext.executeScript(f: File): JSONObject {
    return try {
        val src = f.readText()
        executeScript(src)
    } catch (e: Exception) {
        val ret = JSONObject()
        ret["error"] = "executeScript leads to exception: ${Log.getStackTraceString(e)}"
        Log.e(TAG, ret.getString("error"))
        ret
    }
}

fun AppAutoContext.executeScript(url: String?): JSONObject {
    var ret = JSONObject()
    if (url.isNullOrEmpty()) {
        ret["error"] = "null or empty url passed"
        return ret
    }
    ret = httpClient.get(url)
    if (ret.containsKey("error")) return ret

    val data = ret.getString("result")
    return executeScript(data)
}

// create a new scope based on given scope object
internal fun AppAutoContext.newScope(scope: ScriptableObject): Scriptable {
    val obj = jsContext.newObject(scope)
    obj.parentScope = null
    obj.prototype = scope
    return obj
}

internal fun AppAutoContext.installRequire(modulePath: List<String>, sandbox: Boolean): Require {
    val rb = RequireBuilder()
    rb.setSandboxed(sandbox)
    val uris = mutableListOf<URI>();
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
    val require = rb.createRequire(jsContext, jsGlobalScope)
    require.install(jsGlobalScope)
    return require
}

