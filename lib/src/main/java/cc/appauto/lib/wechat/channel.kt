package cc.appauto.lib.wechat

import android.accessibilityservice.AccessibilityService
import cc.appauto.lib.*
import cc.appauto.lib.ng.ClassName
import cc.appauto.lib.ng.HierarchyNode
import cc.appauto.lib.ng.HierarchyTree
import com.alibaba.fastjson.JSONObject

fun channelPageFollow(srv: AccessibilityService): JSONObject {
    val ret = JSONObject()

    val ht = HierarchyTree.from(srv)
    if (ht == null) {
        ret["error"] = "setup hierarchy tree failed"
        return ret
    }
    val hnode = ht.classNameSelector("${ClassName.Linearlayout}>${ClassName.FrameLayout}>${ClassName.TextView}").text("私信").firstOrNull()
    if (hnode == null) {
        ret["error"] = "channelPageFollow: find 私信 control failed"
        ht.recycle()
        return ret
    }

    val hmsg = hnode.ancestor(1)!!
    val hfollow = hmsg.sibling(1)
    if (hfollow == null) {
        ret["error"] = "channelPageFollow: no hnode for follow found"
        ht.recycle()
        return ret
    }

    val followNode = ht.getAccessibilityNodeInfo(hfollow)!!

    val w = NodeWalker()
    w.expectRetry = Runnable {
        followNode.click(null)
    }
    w.expectRetry!!.run()
    sleep(1000)
    var node = w.expect(srv, newFilter { classTextParentClassFilter(it, TextViewClassName, LinearLayoutClassName, "取消") }, 1000, 4, WechatPackageName)
    ht.recycle()

    if (node == null) {
        ret["error"] = "wait cancel follow UI to confirm the follow action successfully timeout"
        return ret
    }
    val cancel = node.findClickableParent()
    if (cancel == null) {
        ret["error"] = "can not find clickable parent for cancel text view"
        node.recycle()
        return ret
    }
    node.recycle()

    w.expectRetry = Runnable {
        cancel.click(null)
    }
    w.expectRetry!!.run()
    sleep(500)
    node = w.expect(srv, newFilter { classTextParentClassFilter(it, TextViewClassName, FrameLayoutClassName, "私信") })
    cancel.recycle()

    if (node == null) {
        ret["error"] = "wait channel account page after confirm account followed timeout"
        return ret
    }
    node.recycle()

    ret["result"] = "success"
    return ret
}

// goto trend video node with given video descriptoin.
// if video description is not given, goto the first trend video entry
fun channelPageGotoTrendVideo(srv: AccessibilityService, name: String, videoDescription: String?): JSONObject {
    val ret = JSONObject()
    val top = getTopAppNode(srv, WechatPackageName)
    if (top == null) {
        ret["error"] = "get valid wechat top node timeout"
        return ret
    }

    val ht = HierarchyTree.from(top)
    val nodes = ht.classNameSelector("${ClassName.RecyclerView}>${ClassName.Linearlayout}>${ClassName.TextView}").selector {
        when {
            it.text.isNullOrEmpty() -> false
            else -> !it.text.toString().startsWith("#")
        }
    }
    if (nodes.isEmpty()) {
        ret["error"] = "no trend video entry found"
        ht.recycle()
        return ret
    }

    var hnode: HierarchyNode?
    if (videoDescription == null) {
        hnode = nodes.first()
    } else {
        hnode = nodes.text(videoDescription).firstOrNull()
        if (hnode == null) {
            ret["error"] = "no trend video entry found with given description: ${videoDescription}"
            ht.recycle()
            return ret
        }
    }
    val entry = hnode.clickableAncestor
    if (entry == null) {
        ret["error"] = "no clickable ancestor for ${hnode.string}"
        ht.recycle()
        return ret
    }
    val trendEntry = ht.getAccessibilityNodeInfo(entry)!!

    val w = NodeWalker()
    w.expectRetry = Runnable {
        trendEntry.click(null)
    }
    w.expectRetry!!.run()
    sleep(1000, 2000)

    var node = w.expect(srv, newFilter { classContentDescriptionFilter(it, FrameLayoutClassName, "当前所在页面,$name") }, 2000, 3, WechatPackageName)
    ht.recycle()
    if (node == null) {
        ret["error"] = "wait channel video detail page timeout"
        return ret
    }
    node.recycle()
    ret["result"] = "success"
    return ret
}

fun videoDetailLikeComment(srv: AccessibilityService, comment: String?, like: Boolean = true): JSONObject {
    var ret = JSONObject()
    val top = getTopAppNode(srv, WechatPackageName)
    if (top == null) {
        ret["error"] = "get valid wechat top node timeout"
        return ret
    }
    val ht = HierarchyTree.from(top)
    var hnodes = ht.classNameSelector("${ClassName.RecyclerView}>${ClassName.FrameLayout}>${ClassName.Linearlayout}>${ClassName.Linearlayout}>${ClassName.TextView}").isVisibleToUser()
    if (hnodes.isEmpty()) {
        ht.recycle()
        ret["error"] = "can not find expected video action entries"
        return ret
    }
    if (hnodes.size != 3) {
        ht.recycle()
        ret["error"] = "invalid video action entry size: ${hnodes.size}, expected 3"
        return ret
    }


    val hnLike = hnodes[1]!!.parent!!
    val hnComment = hnodes[2]!!.parent!!

    val nodeLike = ht.getAccessibilityNodeInfo(hnLike)!!

    if (like) {
        val r = getAppBound(srv)
        if (r == null)
            nodeLike.click(null)
        else {
            srv.doubleClick(r.centerX(), r.centerY())
        }
    }
    if (comment == null) {
        ht.recycle()
        ret["result"] = "success"
        return ret
    }

    val nodeComment = ht.getAccessibilityNodeInfo(hnComment)!!

    val w = NodeWalker()
    // click the comment button and wait the edittext control in comment page
    w.expectRetry = Runnable { nodeComment.click(null)}
    w.expectRetry!!.run()
    sleep(1000)
    var node = w.expect(srv, newFilter { classTextFilter(it, EditTextClassName, "发表评论") }, 1500, 3, WechatPackageName)

    ht.recycle()
    if (node == null) {
        ret["error"] = "wait edittext control in comment page timeout"
        return ret
    }

    val edComment = node
    ret = edComment.setContent(comment)
    if (ret.containsKey("error")) {
        edComment.recycle()
        return ret
    }
    ret.clear()

    // input comment content in the edit text control
    w.expectRetry = Runnable {
        edComment.setContent(comment)
    }
    node = w.expect(srv, newFilter { classTextFilter(it, TextViewClassName, "回复") })
    edComment.recycle()
    if (node == null) {
        ret["error"] = "wait reply button in comment page timeout"
        return ret
    }

    // click the reply button to send the comment
    val replyButton = node
    w.expectRetry = Runnable {
        replyButton.click(null)
    }
    w.expectRetry!!.run()
    sleep(1000)

    node = w.expect(srv, newFilter { classTextFilter(it, EditTextClassName, "发表评论")  })
    replyButton.recycle()
    if (node == null) {
        ret["error"] = "error occurred when clicking the reply button to send the comment"
        return ret
    }

    ret["result"] = "success"
    return  ret
}

fun videoDetailComment(srv: AccessibilityService, content: String): JSONObject {
    val w = NodeWalker()
    w.expectRetry = null
    var ret = JSONObject()

    // first, find the comment textview
    var node = w.find(srv, newFilter { classTextParentClassFilter(it, TextViewClassName, LinearLayoutClassName, "评论") })
    if (node == null) {
        ret["error"] = "can not find comment action in video list page"
        return ret
    }

    val comment = node.findClickableParent()
    node.recycle()
    if (comment == null) {
        ret["error"] = "can not find clickable parent for ${node.string()}"
        return ret
    }

    // click the comment and wait the edittext control in comment page
    w.expectRetry = Runnable { comment.click(null)}
    node = w.expect(srv, newFilter { classTextFilter(it, EditTextClassName, "发表评论") }, 1500, 3, WechatPackageName)
    comment.recycle()
    if (node == null) {
        ret["error"] = "wait edittext control in comment page timeout"
        return ret
    }

    ret = node.setContent(content)
    if (ret.containsKey("error")) {
        node.recycle()
        return ret
    }

    return ret
}