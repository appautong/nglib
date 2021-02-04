package cc.appauto.lib.ng

import android.content.Context
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import fi.iki.elonen.NanoHTTPD
import java.io.File

class Httpd internal constructor(val ctx: Context, val port: Int=8900): NanoHTTPD(port) {
    private fun handleReset(session: IHTTPSession): JSONObject {
        return AppAutoContext.resetJavascriptRequire()
    }

    private fun handleExecJS(session: IHTTPSession): JSONObject {
        val js = session.parms["file"]
        val ret = JSONObject()
        if (js.isNullOrEmpty()) {
            ret["error"] = "parameter file is required"
            return ret
        }
        return File(js).run { AppAutoContext.execJavascript(this) }
    }

    override fun serve(session: IHTTPSession): Response {
        session.parseBody(null)

        // session.queryParameterString is the body content
        // session.parameters are the query parameters or post multipart form fields

        val ret = when(session.parms.get("method")) {
            "exec_js" -> handleExecJS(session)
            "reset" -> handleReset(session)
            else -> JSONObject().apply { this["error"] = "invalid or unsupported request: ${JSON.toJSONString(session.parameters)}" }
        }

        return newFixedLengthResponse(ret.toJSONString()).also {
            it.addHeader("Access-Control-Allow-Origin", "*")
        }
    }
}