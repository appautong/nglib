package cc.appauto.lib.ng

import android.util.Log
import com.alibaba.fastjson.JSONObject
import org.mozilla.javascript.Context
import java.io.File

fun AppAutoContext.executeScript(f: File): JSONObject {
    return try {
        val src = f.readText()
        execute(src)
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
    return execute(data)
}

private fun AppAutoContext.execute(src: String): JSONObject {
    val ret = JSONObject()
    try {
        val obj = jsContext.evaluateString(jsGlobalScope, src, "<execute>", 0, null)
        ret["result"] = Context.toString(obj)
    } catch (e: Exception) {
        ret["error"] = "execute script leads to exception: ${Log.getStackTraceString(e)}"
        Log.e(TAG, ret.getString("error"))
    }
    return ret
}