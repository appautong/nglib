package cc.appauto.lib.ng.wechat

import android.accessibilityservice.AccessibilityService
import cc.appauto.lib.ng.*
import com.alibaba.fastjson.JSONObject


object Wechat {
    fun openContactPage(srv: AccessibilityService): JSONObject {
        val ret = JSONObject()

        val automator = AppAutoContext.automatorOf(::openContactPage.name) ?: return ret.also {
            ret["error"] = "create automator failed"
        }

        automator.stepOf("open_wechat_home")
        return ret
    }

    val stepOfOpeningWechatHome: AutomationStep by lazy {
        val step = AutomationStep("open_wechat_home")
        step.action {
            quitApp(step.automator?.srv, WechatPackageName)
        }
        step
    }
}