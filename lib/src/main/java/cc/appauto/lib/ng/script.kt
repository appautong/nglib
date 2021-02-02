package cc.appauto.lib.ng

import android.util.Log
import com.alibaba.fastjson.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File

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
