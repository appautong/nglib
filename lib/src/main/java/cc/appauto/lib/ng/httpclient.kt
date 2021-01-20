package cc.appauto.lib.ng

import android.content.Context
import android.util.Log
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.android.volley.RequestQueue
import com.android.volley.toolbox.RequestFuture
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import java.util.concurrent.TimeUnit

object HttpClient {
    private lateinit  var ctx: Context
    private lateinit  var queue: RequestQueue
    private var inited = false

    @Synchronized
    fun init(ctx: Context) {
        if (inited) return

        this.ctx = ctx
        queue = Volley.newRequestQueue(ctx.applicationContext)
        inited = true
    }

    fun get(url: String, timeoutSec: Long = 10) : JSONObject {
        val ret = JSONObject()
        if (!inited) {
            ret["error"] = "HttpClient is not initialized, please invoke init first"
            return ret
        }
        val future = RequestFuture.newFuture<String>()
        val req = object: StringRequest(Method.GET, url, future, future) {
            override fun getPriority(): Priority {
                return Priority.IMMEDIATE
            }
        }
        queue.add(req)
        try {
            val data = future.get(timeoutSec, TimeUnit.SECONDS)
            ret["result"] = data
        } catch (e: Exception) {
            ret["error"] = "get leads to exception: ${Log.getStackTraceString(e)}"
            Log.e(TAG, ret.getString("error"))
        }
        return ret
    }

    fun post(url: String, body: JSON, timeoutSec: Long = 10): JSONObject {
        val ret = JSONObject()
        if (!inited) {
            ret["error"] = "HttpClient is not initialized, please invoke init first"
            return ret
        }
        val future = RequestFuture.newFuture<String>()
        val req = object: StringRequest(Method.POST, url, future, future) {
            override fun getPriority(): Priority {
                return Priority.IMMEDIATE
            }

            override fun getBodyContentType(): String {
                return "application/json"
            }

            override fun getBody(): ByteArray {
                return JSON.toJSONBytes(body)
            }
        }
        queue.add(req)
        try {
            val data = future.get(timeoutSec, TimeUnit.SECONDS)
            ret["result"] = data
        } catch (e: Exception) {
            ret["error"] = "get leads to exception: ${Log.getStackTraceString(e)}"
            Log.e(TAG, ret.getString("error"))
        }
        return ret
    }
}