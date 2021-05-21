package cc.appauto.lib.ng.wechat

import android.util.Log
import cc.appauto.lib.ng.*
import com.alibaba.fastjson.JSONObject


object Wechat {
    private val bottomLables = setOf("微信", "通讯录", "发现", "我")

    @JvmStatic
    fun openContactPage(): JSONObject {
        val ret = JSONObject()

        val automator = AppAutoContext.automatorOf(::openContactPage.name) ?: return ret.also {
            ret["error"] = "create automator failed"
        }

        automator.stepOfOpenWechatHome()

        val contactLables = setOf("新的朋友", "通讯录")
        automator.stepOf("goto_contact_page").setupActionNode("contact") { tree ->
            tree.classHierarchySelector("${ClassName.RelativeLayout}>${ClassName.TextView}").text("通讯录")
                .clickableParent()
        }.action {
            it.getActionNodeInfo("contact").click(null)
        }.expect { tree, _ ->
            tree.classHierarchySelector("${ClassName.Linearlayout}>${ClassName.TextView}").selector {
                contactLables.contains(it.text)
            }.size >= contactLables.size
        }

        automator.run()
        Log.i(TAG, "openContactPage: ${automator.allStepsSucceed}, message: ${automator.message}")
        automator.close()

        if (!automator.allStepsSucceed) return ret.also {
            ret["error"] = automator.message
            ret["hierarchy"] = automator.failedHierarchyString
        }

        return ret.also {
            ret["result"] = "success"
        }
    }

    @JvmStatic
    fun contactPageOpenUser(user: String): JSONObject {
        val ret = JSONObject()

        val automator = AppAutoContext.automatorOf(::contactPageOpenUser.name) ?: return ret.also {
            ret["error"] = "create automator failed"
        }

        automator.stepOf("click_global_search").setupActionNode("search") { tree ->
            tree.classHierarchySelector("${ClassName.RelativeLayout}").contentDescription("搜索")
        }.action {
            it.getActionNodeInfo("search").click(null)
        }.expect { tree, _ ->
            when {
                tree.classHierarchySelector("${ClassName.EditText}").text("搜索").isEmpty() -> false
                tree.classHierarchySelector("${ClassName.RelativeLayout}>${ClassName.TextView}")
                    .text("朋友圈").isEmpty() -> false
                else -> true
            }
        }.postActionDelay(2000)

        automator.stepOf("search_user").setupActionNode("input") { tree ->
            tree.classHierarchySelector("${ClassName.EditText}").text("搜索")
        }.action {
            it.getActionNodeInfo("input").setContent(user)
        }.expect { tree, step ->
            val node = step.getActionNodeInfo("input")
            node.refresh()
            node.text.toString() == user
        }

        automator.stepOf("click_user_entry").setupActionNode("user") {
            it.classHierarchySelector("${ClassName.ListView}>${ClassName.RelativeLayout}>${ClassName.TextView}").text(user, true).clickableParent()
        }.preActionDelay(1000).action {
            it.getActionNodeInfo("user").click(null)
        }.expect { tree, _ ->
            tree.classHierarchySelector("${ClassName.ImageButton}").contentDescription("更多功能按钮，已折叠").isNotEmpty()
        }

        automator.run()
        Log.i(TAG, "contactPageOpenUser: ${automator.allStepsSucceed}, message: ${automator.message}")
        automator.close()

        if (!automator.allStepsSucceed) return ret.also {
            ret["error"] = automator.message
            ret["hierarchy"] = automator.failedHierarchyString
        }

        return ret.also {
            ret["result"] = "success"
        }
    }

    @JvmStatic
    fun chatPageSendText(msg: String): JSONObject {
        val ret = JSONObject()

        val automator = AppAutoContext.automatorOf(::chatPageSendText.name) ?: return ret.also {
            ret["error"] = "create automator failed"
        }

        automator.stepOf("input_msg").setupOptionalActionNode("switch_to_keyboard") {
            it.classHierarchySelector("${ClassName.ImageButton}").contentDescription("切换到键盘")
        }.setupOptionalActionNode("text_input") {
            it.classHierarchySelector("${ClassName.EditText}")
        }.action {
            if (it.actionTargetIsFound("text_input")) {
                it.getActionNodeInfo("text_input").setContent(msg)
            } else if (it.actionTargetIsFound("switch_to_keyboard")) {
                it.getActionNodeInfo("switch_to_keyboard").click(null)
            }
        }.expect { _, step ->
            if (step.actionTargetIsFound("switch_to_keyboard")) {
                false
            } else if (step.actionTargetIsFound("text_input")){
                val node = step.getActionNodeInfo("text_input")
                node.refresh()
                node.text.toString() == msg
            } else
                false
        }

        automator.stepOf("send_msg").setupOptionalActionNode("send") {
            it.classHierarchySelector("${ClassName.Button}").text("发送")
        }.setupActionNode("text_input") {
            it.classHierarchySelector("${ClassName.EditText}")
        }.action {
            if (it.actionTargetIsFound("send")) {
                it.getActionNodeInfo("send").click(null)
            }
        }.expect { _, step ->
            if (step.actionTargetIsFound("send")) {
                false
            } else {
                val node = step.getActionNodeInfo("text_input")
                node.refresh()
                node.text == null
            }
        }

        automator.run()
        Log.i(TAG, "chatPageSendText: ${automator.allStepsSucceed}, message: ${automator.message}")
        automator.close()

        if (!automator.allStepsSucceed) return ret.also {
            ret["error"] = automator.message
            ret["hierarchy"] = automator.failedHierarchyString
        }

        return ret.also {
            ret["result"] = "success"
        }
    }

    fun AppAutomator.stepOfOpenWechatHome() {
        this.stepOf("open_wechat_home").action {
            quitApp(this.srv, WechatPackageName)
            openApp(this.srv, WechatPackageName)
        }.expect { tree, _ ->
            tree.classHierarchySelector("${ClassName.RelativeLayout}>${ClassName.TextView}").selector {
                bottomLables.contains(it.text)
            }.size >= bottomLables.size
        }.postActionDelay(0)
    }

    @JvmStatic
    fun openMePage(): JSONObject {
        val ret = JSONObject()

        val automator = AppAutoContext.automatorOf(::openMePage.name) ?: return ret.also {
            ret["error"] = "create automator failed"
        }

        automator.stepOfOpenWechatHome()

        automator.stepOf("open_me_page").setupActionNode("me") { tree ->
            tree.classHierarchySelector("${ClassName.RelativeLayout}>${ClassName.TextView}").text("我")
                .clickableParent()
        }.action {
            it.getActionNodeInfo("me").click(null)
        }.expect { tree, _ ->
            tree.classHierarchySelector("${ClassName.Linearlayout} > ${ClassName.TextView}").selector {
                (it.text != null) && (it.text == "设置" || it.text!!.contains("微信号"))
            }.size >= 2
        }

        automator.stepOf("open_personal_info_page").setupActionNode("info") {
            val info = it.classHierarchySelector("${ClassName.Linearlayout} > ${ClassName.TextView}").text("微信号")
            if (info.isNotEmpty()) ret["wxid"] = info.first().text!!.split("：").last()
            info
        }.action {
            it.getActionNodeInfo("info").click(null)
        }.expect { tree, _ ->
            tree.classHierarchySelector("${ClassName.TextView}").text("个人信息").isNotEmpty()
        }

        automator.stepOf("extract_basic_info").setupActionNode("alias") {
            it.classHierarchySelector("${ClassName.Linearlayout} > ${ClassName.TextView}").text("昵称").sibling(1)
        }.setupActionNode("wxid") {
            it.classHierarchySelector("${ClassName.Linearlayout} > ${ClassName.TextView}").text("微信号").sibling(1)
        }.setupActionNode("more") {
            it.classHierarchySelector("${ClassName.Linearlayout} > ${ClassName.TextView}").text("更多").clickableParent()
        }.action {
            ret["alias"] = it.getActionNodeInfo("alias").text.toString()
            ret["wxid"] = it.getActionNodeInfo("wxid").text.toString()
            it.getActionNodeInfo("more").click(null)
        }.expect { tree, _ ->
            tree.classHierarchySelector("${ClassName.TextView}").text("更多信息").isNotEmpty()
        }

        automator.stepOf("extract_more_info").setupActionNode("gender") {
            it.classHierarchySelector("${ClassName.Linearlayout} > ${ClassName.TextView}").text("性别").sibling(1)
        }.setupActionNode("region") {
            it.classHierarchySelector("${ClassName.Linearlayout} > ${ClassName.TextView}").text("地区").sibling(1)
        }.setupActionNode("signature") {
            it.classHierarchySelector("${ClassName.Linearlayout} > ${ClassName.TextView}").text("性签名").sibling(1)
        }.action {
            ret["gender"] = it.getActionNodeInfo("gender").text.toString()
            ret["region"] = it.getActionNodeInfo("region").text.toString()
            ret["signature"] = it.getActionNodeInfo("signature").text.toString()
        }.postActionDelay(500).expect { _, _ -> true }

        automator.run()
        Log.i(TAG, "openMePage: ${automator.allStepsSucceed}, message: ${automator.message}, ret: ${ret.toJSONString()}")
        automator.close()

        if (!automator.allStepsSucceed) return ret.also {
            ret["error"] = automator.message
            ret["hierarchy"] = automator.failedHierarchyString
        }

        return ret
    }
}