package cc.appauto.lib.ng

import android.content.Context
import android.util.Log
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import fi.iki.elonen.NanoHTTPD
import java.io.File

class Httpd internal constructor(val ctx: Context, val port: Int=8900): NanoHTTPD(port) {
    private val name = "httpd"

    private fun handleResetJsRequire(session: IHTTPSession): JSONObject {
        if (!AppAutoContext.initialized) return JSONObject().also { it["error"] = AppAutoContext.ERR_NOT_READY }
        return AppAutoContext.jsRuntime.resetRequire(AppAutoContext.jsRuntime.require)
    }

    private fun handleResetJsGlobal(session: IHTTPSession): JSONObject {
        if (!AppAutoContext.initialized) return JSONObject().also { it["error"] = AppAutoContext.ERR_NOT_READY }
        val js = session.parms["file"]
        if (js.isNullOrEmpty()) return JSONObject().also { it["error"] = "parameter file is required"}

        return AppAutoContext.executeTask {
            val jsRuntime = AppAutoContext.jsRuntime
            val scope = jsRuntime.ctx.initStandardObjects(null, true)
            val require = jsRuntime.installRequire(scope, JavascriptRuntime.requireBuilder)
            jsRuntime.require = require
            jsRuntime.scopeGlobal = scope

            val content = File(js).readText()
            jsRuntime.evaluateJavascript(content, scope).also {
                if (it.containsKey("error")) Log.e(TAG, "$name load script $js failed: ${it.toJSONString()}")
                else Log.i(TAG, "$name: load script $js successfully ")
            }
        }

    }

    private fun handleExecJS(session: IHTTPSession): JSONObject {
        val js = session.parms["file"]
        val ret = JSONObject()
        if (js.isNullOrEmpty()) {
            ret["error"] = "parameter file is required"
            return ret
        }
        if (!AppAutoContext.initialized) return ret.also { it["error"] = AppAutoContext.ERR_NOT_READY }

        return File(js).run { AppAutoContext.jsRuntime.execScript(this) }
    }

    override fun serve(session: IHTTPSession): Response {
        session.parseBody(null)

        // session.queryParameterString is the body content
        // session.parameters are the query parameters or post multipart form fields

        val ret = when(session.parms.get("method")) {
            "exec_js" -> handleExecJS(session)
            "reset_jsrequire" -> handleResetJsRequire(session)
            "reset_jsglobal" -> handleResetJsGlobal(session)
            else -> JSONObject().apply { this["error"] = "invalid or unsupported request: ${JSON.toJSONString(session.parameters)}" }
        }

        return newFixedLengthResponse(ret.toJSONString()).also {
            it.addHeader("Access-Control-Allow-Origin", "*")
        }
    }
}