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

        automator.stepOf("open_wechat_home").action {
            quitApp(automator.srv, WechatPackageName)
            openApp(automator.srv, WechatPackageName)
        }.expect { tree, _ ->
            tree.classHierarchySelector("${ClassName.RelativeLayout}>${ClassName.TextView}").selector {
                bottomLables.contains(it.text)
            }.size >= bottomLables.size
        }.postActionDelay(0)

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
        }.preActionDelay(0)

        automator.run()
        Log.i(TAG, "openContactPage: ${automator.allStepsSucceed}, message: ${automator.message}")
        automator.close()

        if (!automator.allStepsSucceed) return ret.also {
            ret["error"] = automator.message
            ret["hierarchy"] = automator.failedHierachyString
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
        }.preActionDelay(0).postActionDelay(2000)

        automator.stepOf("search_user").setupActionNode("input") { tree ->
            tree.classHierarchySelector("${ClassName.EditText}").text("搜索")
        }.action {
            it.getActionNodeInfo("input").setContent(user)
        }.expect { tree, step ->
            val node = step.getActionNodeInfo("input")
            node.refresh()
            node.text.toString() == user
        }.preActionDelay(0)

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
            ret["hierarchy"] = automator.failedHierachyString
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
            ret["hierarchy"] = automator.failedHierachyString
        }

        return ret.also {
            ret["result"] = "success"
        }
    }
}