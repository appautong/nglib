package cc.appauto.lib.wechat

import android.accessibilityservice.AccessibilityService
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import cc.appauto.lib.*
import cc.appauto.lib.ng.AppAutomator
import cc.appauto.lib.ng.HierarchyTree
import com.alibaba.fastjson.JSONObject

private fun getKeyboardPostions(root: Rect, collapseKeyboard: Rect): Map<String, Point> {
    val x1 = root.left
    var y1 = collapseKeyboard.bottom
    var x2 = root.right
    var y2 = root.bottom

    val m = mutableMapOf<String, Point>()

    val keyWidth = (x2 - x1) / 3
    val keyHeight = (y2 - y1) / 4

    // centerX/centerY: the center point of digit 1
    var centerX = x1 + keyWidth / 2
    var centerY = y1 + keyHeight / 2

    m["1"] = Point(centerX, centerY)
    m["2"] = Point(centerX+keyWidth, centerY)
    m["3"] = Point(centerX+keyWidth*2, centerY)
    m["4"] = Point(centerX, centerY+keyHeight)
    m["5"] = Point(centerX+keyWidth, centerY+keyHeight)
    m["6"] = Point(centerX+keyWidth*2, centerY+keyHeight)
    m["7"] = Point(centerX, centerY+keyHeight*2)
    m["8"] = Point(centerX+keyWidth, centerY+keyHeight*2)
    m["9"] = Point(centerX+keyWidth*2, centerY+keyHeight*2)
    m["0"] = Point(centerX+keyWidth, centerY+keyHeight*3)
    m["x"] = Point(centerX+keyWidth*2, centerY+keyHeight*3)
    return m
}

fun chatPageSendFavoriteMessage(srv: AccessibilityService, customWalker: NodeWalker?, msg: String): JSONObject {
    val w = customWalker ?: NodeWalker()

    var ret = chatPageOpenMoreFunction(srv, w, MoreFuncMyFavorite)
    if (ret.containsKey("error")) {
        return ret
    }
    val func = ret.remove("func") as AccessibilityNodeInfo
    w.expectRetry = Runnable {
        func.click(null)
    }
    w.expectRetry!!.run()
    sleep(1000)

    val mnf = MultipleNodeFilter(
            newNamedFilter("title") { classTextFilter(it, TextViewClassName, "发送收藏内容") },
            newNamedFilter("favmsg") { classTextFilter(it, TextViewClassName, msg) },
    )
    var node = w.expect(srv,  mnf, 1000, 3, WechatPackageName)
    if (node == null) {
        mnf.recycleAllResult()
        mnf.clear()
        ret["error"] = "wait favorite entry timeout: $msg"
        return ret
    }

    node = mnf.result["favmsg"] as AccessibilityNodeInfo
    val favmsg = node.findClickableParent()
    if (favmsg == null) {
        ret["error"] = "can not find clickable parent for favorite entry: ${node.string()}"
        mnf.recycleAllResult()
        mnf.clear()
        return ret
    }
    mnf.recycleAllResult()
    mnf.clear()

    w.expectRetry = Runnable {
        favmsg.click(null)
    }
    w.expectRetry!!.run()
    sleep(1000)
    node = w.expect(srv, newFilter { classTextFilter(it, ButtonClassName, "发送") }, 1000, 3, WechatPackageName)
    favmsg.recycle()
    if (node == null) {
        ret["error"] = "wait send button timeout after clicked the favorite message entry"
        return ret
    }

    val favSendButton = node
    w.expectRetry = Runnable {
        favSendButton.click(null)
    }
    w.expectRetry!!.run()
    sleep(3000)
    node = w.expect(srv, newFilter {  classContentDescriptionFilter(it, ImageButtonClassName, "更多功能按钮") }, 3000, 3, WechatPackageName)
    favSendButton.recycle()
    if (node == null) {
        ret["error"] = "wait chat page timeout after sending the favorite message"
        return ret
    }
    node.recycle()
    ret["result"] = "success"
    return ret
}

// func is set to the clickable function control with given name in returned JSONObject when success
fun chatPageOpenMoreFunction(srv: AccessibilityService, customWalker: NodeWalker?, func: String): JSONObject {
    val ret = JSONObject()
    val w = customWalker?: NodeWalker()
    w.expectRetry = null
    var node = w.expect(srv, newFilter { classContentDescriptionFilter(it, ImageButtonClassName, *MoreFunctionButtons) })
    if (node == null) {
        ret["error"] = "can not find more functions button in chat page: ${srv.getHierarchyString()}"
        return ret
    }

    val btnMoreFuncs = node
    w.expectRetry = Runnable {
        btnMoreFuncs.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    sleep(1000, 2000)
    w.expectRetry!!.run()
    node = w.expect(srv, newFilter { classTextParentClassFilter(it, TextViewClassName, LinearLayoutClassName, func) })
    if (node == null) {
        ret["error"] = "wait function button $func timeout: ${srv.getHierarchyString()}"
        btnMoreFuncs.recycle()
        return ret
    }
    btnMoreFuncs.recycle()
    val funcNode = w.findParent(node, newBoolFilter { it.isClickable })
    if (funcNode == null) {
        ret["error"] = "find clickable parent for ${node.string()} failed: ${srv.getHierarchyString()}"
        node.recycle()
        return ret
    }
    node.recycle()

    ret["func"] = funcNode
    ret["result"] = "success"
    return ret
}

fun chatPageSendRedPacket(srv: AccessibilityService, customWalker: NodeWalker?, amount: String, password: String, description: String? = null): JSONObject {
    var ret = JSONObject()
    if (password.length != 6) {
        ret["error"] = "invalid password: $password"
        return ret
    }
    if (amount.toFloatOrNull() == null) {
        ret["error"] = "invalid amount: $amount, shall be 0.01 - 1.00"
        return ret
    }

    val w = customWalker ?: NodeWalker()
    ret = chatPageOpenMoreFunction(srv, w, MoreFuncRedPacket)
    if (ret.containsKey("error")) {
        return ret
    }
    val redPacket = ret.remove("func") as AccessibilityNodeInfo
    ret.clear()

    // wait red packet setting page
    w.expectRetry = Runnable {
        redPacket.refresh()
        if (redPacket.isVisibleToUser) redPacket.click(srv)
    }
    var mnf = MultipleNodeFilter(
            newNamedFilter("amount") { classTextFilter(it, EditTextClassName, "0.00") },
            newNamedFilter("desc") { classTextFilter(it, EditTextClassName, "恭喜发财") },
            newNamedFilter("fill") { classTextFilter(it, ButtonClassName, "塞钱进红包") }
    )
    w.expectRetry!!.run()
    sleep(1000,2000)

    var node = w.expect(srv, mnf, 2000)
    redPacket.recycle()
    if (node == null) {
        ret["error"] = "wait red packet page timeout, ${srv.getHierarchyString()}"
        mnf.recycleAllResult()
        mnf.clear()
        return ret
    }

    // fill red packet amount and description
    val amountNode = mnf.result["amount"] as AccessibilityNodeInfo
    val descNode = mnf.result["desc"] as AccessibilityNodeInfo
    val btnFill = mnf.result["fill"] as AccessibilityNodeInfo


    ret = amountNode.setContent(amount)
    if (ret.containsKey("errror")) {
        mnf.recycleAllResult()
        return ret
    }

    ret = descNode.setContent(description)
    if (ret.containsKey("errror")) {
        mnf.recycleAllResult()
        return ret
    }
    mnf.clear()
    amountNode.recycle()
    descNode.recycle()

    // wait red packet pay page
    w.expectRetry = Runnable {
        btnFill.refresh()
        if (btnFill.isVisibleToUser) btnFill.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    w.expectRetry!!.run()

    sleep(3000, 5000)
    mnf = MultipleNodeFilter(
            newNamedFilter("close") { classContentDescriptionFilter(it, ViewGroupClassName, "关闭") },
            newNamedFilter("prompt") { classTextFilter(it, TextViewClassName, "请输入支付密码") },
            newNamedFilter("keyboard") { classContentDescriptionFilter(it, ImageViewClassName, "收起键盘") }
    )
    node = w.expect(srv, mnf, 3000)
    btnFill.recycle()
    if (node == null) {
        ret["error"] = "wait red packet pay page timeout: ${srv.getHierarchyString()}"
        mnf.recycleAllResult()
        return ret
    }

    // calculates all key potion in digitial keyboard of pay page
    val close = mnf.result["close"]
    val keyboard = mnf.result["keyboard"]
    val rootLayout = close!!.parent
    val keyboardLayout =  keyboard!!.parent
    close.recycle()
    keyboard.recycle()
    mnf.result.remove("prompt")?.recycle()

    val r1 = rootLayout.bound()
    var r2 = keyboardLayout.bound()
    rootLayout.recycle()
    keyboardLayout.recycle()

    val keys = getKeyboardPostions(r1, r2)

    password.forEach {
        click(srv, keys[it.toString()]!!.x, keys[it.toString()]!!.y, 0, 10)
        sleep(500)
    }

    sleep(2000, 4000)

    w.expectRetry = null

    mnf = MultipleNodeFilter(
            newNamedFilter("redpacket") { classTextFilter(it, TextViewClassName, "微信红包") },
            newNamedFilter("morefuncs") { classContentDescriptionFilter(it, ImageButtonClassName, "更多功能按钮") },
    )
    node = w.expect(srv, mnf)
    mnf.recycleAllResult()

    if (node == null) {
        ret["error"] = "can not find red packet sent result: ${srv.getHierarchyString()}"
        return ret
    }

    ret["result"] = "success"
    return ret
}

// add friend from the top notification bar like "对方还不是你的朋友"
fun chatPageAddPeer(srv: AccessibilityService): JSONObject {
    val ret = JSONObject()

    // use automator to click the top notification bar to accept the new peer friend
    val automator = AppAutomator(srv, "chatPageAddPeer")

    var foundNotfiBar = false
    automator.stepOf("click top notification").setupActionNode("top_noti_bar") { tree ->
        tree.classNameSelector("${ClassName.Linearlayout}>${ClassName.TextView}").text("对方还不是你的朋友").clickableParent()
    }.action {
        foundNotfiBar = true
        it.getActionNodeInfo("top_noti_bar").click(null)
    }.postActionDelay(2000).retry(1)

    automator.stepOf("click add to contacts").setupActionNode("add_as_contact") { tree ->
        tree.classNameSelector("${ClassName.Linearlayout}>${ClassName.TextView}").text("添加到通讯录").clickableParent()
    }.action {
        it.getActionNodeInfo("add_as_contact").click(null)
    }.postActionDelay(2000)

    automator.stepOf("click send message").setupActionNode("send_message"){tree ->
        tree.classNameSelector("${ClassName.Linearlayout}>${ClassName.TextView}").text("发消息").clickableParent()
    }.action {
        it.getActionNodeInfo("send_message").click(null)
    }.expect { tree, _ ->
        tree.classNameSelector("${ClassName.Linearlayout}>${ClassName.ImageButton}").contentDescription("更多功能按钮").firstOrNull() != null
    }.postActionDelay(2000)

    automator.run()

    automator.close()

    if (!foundNotfiBar) return ret.also {
        it["code"] = 0
        it["result"] = "can not find the new peer friend notification bar"
        Log.i(TAG, it.getString("result"))
    }
    if (!automator.allStepsSucceed) return ret.also {
        it["error"] = automator.message
        it["code"] = -1
        Log.i(TAG, "chatPageAddPeer failed to add peer friend, ${automator.message}")
    }


    ret["result"] = "ok"
    ret["code"] = 200

    return ret
}