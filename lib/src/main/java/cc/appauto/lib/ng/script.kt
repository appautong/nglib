package cc.appauto.lib.ng

import android.util.Log
import com.alibaba.fastjson.JSONObject
import org.mozilla.javascript.Context
import java.io.File

fun executeScript(f: File): JSONObject {
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

fun executeScript(url: String?): JSONObject {
    var ret = JSONObject()
    if (url.isNullOrEmpty()) {
        ret["error"] = "null or empty url passed"
        return ret
    }
    ret = HttpClient.get(url)
    if (ret.containsKey("error")) return ret

    val data = ret.getString("result")
    return execute(data)
}

private fun execute(src: String): JSONObject {
    val ret = JSONObject()
    val ctx = Context.enter()
    ctx.optimizationLevel = -2
    try {
        val scope = ctx.initStandardObjects()
        val obj = ctx.evaluateString(scope, src, "<execute>", 0, null)
        ret["result"] = Context.toString(obj)
    } catch (e: Exception) {
        ret["error"] = "execute script leads to exception: ${Log.getStackTraceString(e)}"
        Log.e(TAG, ret.getString("error"))
    }

    Context.exit()
    return ret
}