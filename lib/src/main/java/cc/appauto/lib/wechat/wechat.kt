package cc.appauto.lib.wechat

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import cc.appauto.lib.*
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import java.text.SimpleDateFormat
import java.util.*

val BottomTabViewMe = arrayOf("我", "Me")
val BottomTabViewDiscover = arrayOf("发现", "Discover")
val BottomTabViewContacts = arrayOf("通讯录", "Contacts")
val BottomTabViewChats = arrayOf("微信", "Chats")

val LabelDiscoverMoments = arrayOf("朋友圈", "Moments")
val LabelDiscoverTopStories = arrayOf("看一看", "Top Stories")

val LabelWechatID = arrayOf("微信号", "ID")
val MoreFunctionButtons = arrayOf("更多功能按钮", "More function buttons")
val MoreFuncTransfer = "转账"
val MoreFuncRedPacket = "红包"
val MoreFuncMyFavorite = "我的收藏"

val LabelSearch = arrayOf("搜索", "Search")
val LabelSearchContacts = arrayOf("联系人", "Contacts")

val PagePersonalInformation = arrayOf("个人信息", "My Profile")
val PageMoreInfo = arrayOf("更多信息","More Info")

val LabelAlias = arrayOf("昵称", "Name")
val LabelMore = arrayOf("更多", "More")

val LabelGender = arrayOf("性别", "Gender")
val LabelRegion = arrayOf("地区", "Region")
val LabelWhatsUp = arrayOf("个性签名","What's Up")

data class MomentSearchEntry(val date: String, val content: String)

// on success: node field is set to the contact control in wechat bottom navigation pane
fun openContacts(srv: AccessibilityService, customWalker: NodeWalker?): JSONObject {
    val ret = JSONObject()

    val f = MultipleNodeFilter(
            newNamedFilter("me") { elem: AccessibilityNodeInfo? -> classTextFilter(elem!!, TextViewClassName, "我") },
            newNamedFilter("contacts") { elem: AccessibilityNodeInfo? -> classTextFilter(elem!!, TextViewClassName, "通讯录") }
    )

    val w: NodeWalker = customWalker ?: NodeWalker()

    openApp(srv, srv.applicationContext, WechatPackageName, 3000)
    w.expectRetry = Runnable {
        quitApp(srv, WechatPackageName, 5)
        openApp(srv, srv.applicationContext, WechatPackageName, 3000)
    }
    val node = w.expect(srv, f, 1000, 3, WechatPackageName)
    if (node == null) {
        ret["error"] = "expect wechat home page timeout"
        return ret
    }
    f.result["me"]?.recycle()
    ret["result"] = "ok"
    ret["node"] = f.result["contacts"]
    f.clear()
    return ret
}

// on success: node field is set to the given discovery item control in discovery page
fun openDiscoveryItem(srv: AccessibilityService, customWalker: NodeWalker?, discoveryItem: String): JSONObject {
    val ret = JSONObject()

    var f = MultipleNodeFilter(
            newNamedFilter("me") { elem: AccessibilityNodeInfo? -> classTextFilter(elem!!, TextViewClassName, "我") },
            newNamedFilter("discovery") { elem: AccessibilityNodeInfo? -> classTextFilter(elem!!, TextViewClassName, "发现") }
    )

    val w: NodeWalker = customWalker ?: NodeWalker()

    openApp(srv, srv.applicationContext, WechatPackageName, 3000)
    w.expectRetry = Runnable {
        quitApp(srv, WechatPackageName, 5)
        openApp(srv, srv.applicationContext, WechatPackageName, 3000)
    }
    var node = w.expect(srv, f, 1000, 3, WechatPackageName)
    if (node == null) {
        ret["error"] = "expect wechat discover page timeout"
        f.recycleAllResult()
        f.clear()
        return ret
    }

    f.result["me"]?.recycle()
    node = f.result.remove("discovery")
    f.clear()
    val discovery = node?.findClickableParent()

    if (discovery == null) {
        ret["error"] = "can not find clickable parent for ${node?.string()}"
        node?.recycle()
        return ret
    }

    w.expectRetry = Runnable {
        discovery.click(null)
    }
    sleep(500, 2000)
    w.expectRetry!!.run()

    f = MultipleNodeFilter (
            newNamedFilter("discovery") { elem: AccessibilityNodeInfo? -> classTextFilter(elem!!, TextViewClassName, "发现") },
            newNamedFilter("morefuncs") { elem: AccessibilityNodeInfo? -> classContentDescriptionFilter(elem!!, RelativeLayoutClassName, "更多功能按钮") },
            newNamedFilter("item") { elem: AccessibilityNodeInfo? -> classTextFilter(elem!!, TextViewClassName, discoveryItem) },
    )

    node = w.expect(srv, f, 1000, 3, WechatPackageName)
    discovery.recycle()
    if (node == null) {
        ret["error"] = "expect wechat discover item ${discoveryItem} timeout"
        f.recycleAllResult()
        f.clear()
        return ret
    }

    f.result.remove("discovery")?.recycle()
    f.result.remove("morefuncs")?.recycle()
    node =  f.result.remove("item")!!

    val tmp = node.findClickableParent()
    if (tmp == null) {
        ret["error"] = "can not find clickable parent for ${node.string()}"
        node.recycle()
        return ret
    }
    ret["node"] = tmp
    ret["result"] = "ok"

    return ret
}

fun openChannelAccountPage(srv: AccessibilityService, name: String): JSONObject {
    val w = NodeWalker()

    var ret = openDiscoveryItem(srv, w, "视频号")
    if (ret.containsKey("error")) return ret

    val channel = ret.remove("node") as AccessibilityNodeInfo
    ret.clear()

    // click the channel item in discovery page and try going to channel page
    var mnf = MultipleNodeFilter(
            newNamedFilter("followed") { classTextFilter(it, TextViewClassName, "关注") },
            newNamedFilter("friend") { classTextFilter(it, TextViewClassName, "朋友") },
            newNamedFilter("recommend") { classTextFilter(it, TextViewClassName, "推荐") },
    )

    w.expectRetry = Runnable {
        channel.click(null)
    }
    w.expectRetry!!.run()
    sleep(2000,5000)

    var node =  w.expect(srv, mnf, 3000, 3, WechatPackageName)
    channel.recycle()
    if (node == null) {
        ret["error"] = "wait channel page time out"
        mnf.recycleAllResult()
        mnf.clear()
        return ret
    }
    mnf.recycleAllResult()
    mnf.clear()

    w.expectRetry = null
    var classHierarchy = String.format("%s,%s,%s", FrameLayoutClassName, FrameLayoutClassName, FrameLayoutClassName)
    node = w.expect(srv, newFilter {
        if (w.classesStr.endsWith(classHierarchy) && it.isClickable)
            FilterResult.Match
        else
            FilterResult.Continue
    }, 1000, 3, WechatPackageName)

    if (node == null) {
        ret["error"] = "wait btnSearch timeout"
        return ret
    }
    val btnSearch = node

    // click btnSearch to show the search textview control
    w.expectRetry = Runnable {
        btnSearch.click(null)
    }
    w.expectRetry!!.run()
    sleep(1000)

    node = w.expect(srv, newFilter { classTextFilter(it, TextViewClassName, "搜索") }, 1000, 3, WechatPackageName)
    btnSearch.recycle()

    if (node == null) {
        ret["error"] = "wait search textview control timeout"
        return ret
    }

    // lcick the search textview to show the search edit control
    val tvSearh = node.findClickableParent()
    if (tvSearh == null) {
        node.recycle()
        ret["error"] = "can not find clickable parent for search textview control"
        return ret
    }
    node.recycle()

    w.expectRetry = Runnable {
        tvSearh.click(null)
    }
    w.expectRetry!!.run()
    sleep(1000)

    node = w.expect(srv, newFilter { classTextFilter(it, EditTextClassName, "搜索") }, 1000, 3, WechatPackageName)
    tvSearh.recycle()

    if (node == null) {
        ret["error"] = "wait search edittext control timeout"
        return ret
    }

    ret = node.setContent(name)
    if (ret.containsKey("error")) {
        node.recycle()
        return ret
    }
    ret.clear()

    var imeFound = false
    for (i in 0 until 3) {
        val ime = getInputIMEWindow(srv)
        if (ime != null) {
            ime.recycle()
            imeFound = true
            break
        }
        node.click(srv)
        sleep(500, 1000)
    }

    if (!imeFound) {
        Log.w(TAG, "wait the ime window timeout")
    }

    // try get the input IME node layout and click the enter key in right bottom
    // todo will not work if the enter key is not in the right bottom of the IME node
    val r = getAppBound(srv)
    if (r == null) {
        ret["error"] = "can not got app bound"
        return ret
    }

    // click the enter key and wait the search result page
    w.expectRetry = Runnable {
        click(srv, r.right-30, r.bottom-30, 0, 10)
    }
    sleep(2000, 4000)

    node = w.expect(srv, newFilter {
        classTextParentClassFilter(it, TextViewClassName, LinearLayoutClassName, "帐号")
    }, 3000, 3, WechatPackageName)

    if (node == null) {
        ret["error"] = "wait search result page timeout"
        return ret
    }
    node.recycle()

    // find the account entry with given name
    w.expectRetry = null
    node = w.find(srv, newFilter {
        classTextFilter(it, TextViewClassName, name)
    })

    if (node == null) {
        ret["error"] = "can not find account node with name: ${name}"
        return ret
    }

    val accountNode = node.findClickableParent()
    if (accountNode == null) {
        ret["error"] = "can not find clickable parent for node: ${node.string()}"
        node.recycle()
        return ret
    }
    node.recycle()


    // click the account node in search result page and goto channel page for the given account
    w.expectRetry = Runnable {
        accountNode.click(null)
    }
    w.expectRetry!!.run()
    sleep(1000, 2000)

    node = w.expect(srv, newFilter { classTextParentClassFilter(it, TextViewClassName, FrameLayoutClassName, "私信") }, 2000, 3, WechatPackageName)
    accountNode.recycle()
    if (node == null) {
        ret["error"] = "wait channel account page for ${name} timeout"
        return ret
    }
    node.recycle()

    ret["result"] = "success"
    return ret
}



// on success: node field is set to the search EditText
fun invokeGlobalSearch(srv: AccessibilityService, customWalker: NodeWalker?): JSONObject {
    val w: NodeWalker = customWalker ?: NodeWalker()

    var ret = openContacts(srv, w)

    if (ret.containsKey("error")) return ret

    val contactNode = ret.remove("node") as AccessibilityNodeInfo

    w.expectRetry = Runnable { contactNode.click(srv) }
    var node = w.expect(srv, newFilter { elem: AccessibilityNodeInfo -> classContentDescriptionFilter(elem, RelativeLayoutClassName, "搜索") }, 1000, 3, WechatPackageName)
    contactNode.recycle()
    if (node == null)  {
        ret["error"] = "expect search button timeout"
        return ret
    }

    val searchNode: AccessibilityNodeInfo = node
    searchNode.click(srv)
    sleep(500, 2000)

    w.expectRetry = Runnable { searchNode.click(srv) }

    val f = MultipleNodeFilter(
            newNamedFilter("search") { elem: AccessibilityNodeInfo -> classTextFilter(elem, EditTextClassName, "搜索") },
            newNamedFilter("moment") { elem: AccessibilityNodeInfo -> classTextFilter(elem, TextViewClassName, "朋友圈") },
    )

    node = w.expect(srv, f, 2000, 3, WechatPackageName)
    searchNode.recycle()
    if (node == null) {
        ret["error"] = "expect search edit text timeout"
        return ret
    }
    f.result["moment"]?.recycle()
    ret["node"] = f.result["search"]
    f.clear()

    ret["result"] = "success"
    return ret
}

private fun clearSetMomentSearchText(srv: AccessibilityService, w: NodeWalker, edText: AccessibilityNodeInfo, name: String) : JSONObject {
    var ret = JSONObject()
    w.expectRetry = Runnable {
        ret = edText.setContent(name)
        if (ret.containsKey("error")) {
            return@Runnable
        }
        sleep(500, 100)
        val imageButton = w.find(srv, newFilter { elem -> classFilter(elem, ImageButtonClassName)  })
        if (imageButton == null) {
            ret["error"] = "can not find clear image button"
            return@Runnable
        }
        imageButton.click(srv)
        sleep(500, 100)
        edText.setContent(name)
        if (ret.containsKey("error")) {
            ret["error"] = "set content again after clear failed: ${ret["error"]}"
            return@Runnable
        }
        imageButton.recycle()
        sleep(1000, 2000)
    }

    w.expectRetry!!.run()
    val node = w.expect(srv, newFilter { elem -> classTextFilter(elem, ViewClassName, "搜索好友的朋友圈") }, 1000, 5)
    if (node == null) {
        ret["error"] = "clearSetMomentSearchText failed: ${ret["error"]}"
    } else {
        node.recycle()
        ret["result"] = "success"
    }
    return ret
}

fun browseUserMoment(srv: AccessibilityService, customWalker: NodeWalker?, name: String): JSONObject {
    val w: NodeWalker = customWalker ?: NodeWalker()

    // find the global search button and click it
    var ret = invokeGlobalSearch(srv, w)
    if (ret.containsKey("error")) {
        return ret
    }
    (ret.remove("node") as AccessibilityNodeInfo).recycle()
    ret.clear()


    // find the 朋友圈 button and click to show search moment UI
    var node = w.expect(srv, newFilter { elem: AccessibilityNodeInfo -> classTextFilter(elem, TextViewClassName, "朋友圈") }, 1000, 3, WechatPackageName)
    if (node == null) {
        ret["error"] = "expect moment search button timeout"
        return ret
    }
    ret.clear()

    // find the input edit text control in moment search UI
    val momentSearch = node
    momentSearch.click(srv)
    sleep(500, 2000)
    w.expectRetry = Runnable {
        momentSearch.refresh()
        if (momentSearch.isVisibleToUser) momentSearch.click(srv)
    }
    node = w.expect(srv, newFilter { elem -> classTextFilter(elem, EditTextClassName, "搜索朋友圈") }, 2000, 3, WechatPackageName)
    momentSearch.recycle()
    if (node == null) {
        ret["error"] = "expect moment search edit text timeout, ${srv.getHierarchyString()}"
        return ret
    }
    ret.clear()

    // input given text in the search input edit text control and wait the control "搜索好友的朋友圈"
    ret = clearSetMomentSearchText(srv, w, node, name)
    node.recycle()
    if (ret.containsKey("error")) {
        return ret
    }
    ret.clear()

    // check the "搜索好友的朋友圈" for given name
    w.expectRetry = null
    node = w.expect(srv, newFilter { elem -> classTextFilter(elem, ViewClassName, name) })
    if (node == null) {
        ret["error"] = "expect moment search entry for $name timeout: ${srv.getHierarchyString()}"
        return ret
    }

    // click the clickable parent for given user moment search entry
    val userMomentSearchEntry = w.findParent(node, newBoolFilter { elem -> elem.isClickable })
    if (userMomentSearchEntry == null) {
        node.recycle()
        ret["error"] = "can not find clickable parent (userMomentSearchEntry) for ${node}"
        return ret
    }
    node.recycle()
    userMomentSearchEntry.click(srv)
    sleep(1000, 3000)

    // expect the moment list for given user
    w.expectRetry = Runnable {
        userMomentSearchEntry.refresh()
        if (userMomentSearchEntry.isVisibleToUser) userMomentSearchEntry.click(srv)
    }

    node = w.expect(srv, newFilter { elem -> classIDFilter(elem, ViewClassName, "search_result") }, 3000)
    if (node == null) {
        ret["error"] = "expect search_result container control timeout"
        return ret
    }
    w.expectRetry = null
    node.recycle()

    sleep(3000)

    val r = Regex("""(\d{4}-\d{1,2}-\d{1,2})\s+(.*)""")
    val r2 = Regex("""(\d{1,2})天前\s+(.*)""")
    val r3 = Regex("""(\d{1,2})个月前\s+(.*)""")
    val l = mutableListOf<MomentSearchEntry>()
    var tried = 0
    var lastAddIndex = 0

    do {
        val tmpIdx = lastAddIndex

        node = w.find(srv, newFilter {
            classTextFilter(it, ViewClassName, "未搜索到相关朋友圈")
        })
        if (node != null) {
            ret["error"] = "未搜索到相关朋友圈"
            break
        }

        w.find(srv, newFilter {
            if (it.text.isNullOrEmpty()) {
                return@newFilter FilterResult.Continue
            }
            val text = it.text.toString()
            var isDay = false
            var isMonth = false
            var res = r.matchEntire(text)
            if (res == null) {
                res = r2.matchEntire(text)
                isDay = true
            }
            if (res == null) {
                res = r3.matchEntire(text)
                isMonth = true
            }
            if (res != null) {
                val index = w.ids.last().toInt()
                if (lastAddIndex < index) {
                    Log.d(TAG, "add moment entry: $index ${res.groupValues[1]}, ${res.groupValues[2]}")

                    val c = GregorianCalendar(Locale.CHINA)
                    val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    when {
                        isDay -> {
                            c.add(Calendar.DATE, -res.groupValues[1].toInt())
                            l.add(MomentSearchEntry(df.format(c.time), res.groupValues[2]))
                        }
                        isMonth -> {
                            c.add(Calendar.MONTH, -res.groupValues[1].toInt())
                            l.add(MomentSearchEntry(df.format(c.time), res.groupValues[2]))
                        }
                        else -> {
                            l.add(MomentSearchEntry(res.groupValues[1], res.groupValues[2]))
                        }
                    }

                    lastAddIndex = index
                } else {
                    Log.d(TAG, "skip added moment entry: $index ${res.groupValues[1]}, ${res.groupValues[2]}")
                }
            }
            FilterResult.Continue
        })
        tried++
        Log.d(TAG, "tried: $tried, found: ${l.size}")
        if (lastAddIndex > tmpIdx) {
            // try scroll up and find the search_result node
            srv.scrollUpDown(true, 0.8f, 1000)
            Log.d(TAG, "found: ${l.size} and scroll up")
        }
        if (tried < 3) {
            sleep(2000, 4000)
        } else {
            break
        }
    } while(true)

    Log.d(TAG, "moments: ${JSON.toJSONString(l)}")
    if (l.size > 0) {
        ret["moments"] = JSON.toJSONString(l)
    } else if (!ret.containsKey("error")) {
        ret["error"] = "no moments found: ${srv.getHierarchyString()}"
    }
    return ret
}

// on success: edMessage field is set to the message ditText
fun openChatPage(srv: AccessibilityService, customWalker: NodeWalker?, name: String): JSONObject {
    val w: NodeWalker = customWalker ?: NodeWalker()

    var ret = invokeGlobalSearch(srv, w)
    if (ret.containsKey("error")) {
        return ret
    }

    var node: AccessibilityNodeInfo? = ret["node"] as AccessibilityNodeInfo?

    ret = node!!.setContent(name)
    node.recycle()
    if (ret.containsKey("error")) {
        return ret
    }
    ret.clear()

    // find specific user node
    w.expectRetry = null
    val classHierarchy = String.format("%s,%s,%s", ListViewClassName, RelativeLayoutClassName, TextViewClassName)
    node = w.expect(srv, newFilter {
        if (classTextFilter(it, TextViewClassName, name) === FilterResult.Match && w.classesStr.contains(classHierarchy))
            FilterResult.Match
        else
            FilterResult.Continue
    })

    if (node == null) {
        ret["error"] = "does not find the corresponding user: $name"
        return ret
    }

    val userNode = node

    // delay a little while before click the user node
    sleep(500, 1000)
    userNode.click(srv)
    w.expectRetry = Runnable {
        userNode.refresh()
        if (userNode.isVisibleToUser) userNode.click(srv)
    }
    node = w.expect(srv, newFilter { elem: AccessibilityNodeInfo ->
        if (classContentDescriptionFilter(elem, ImageButtonClassName, "切换到按住说话") == FilterResult.Match) {
            FilterResult.Match
        } else if (classContentDescriptionFilter(elem, ImageButtonClassName, "切换到键盘") == FilterResult.Match) {
            elem.click(srv)
            FilterResult.Continue
        } else {
            FilterResult.Continue
        }
    }, 1000, 3, WechatPackageName)
    userNode.recycle()
    if (node == null) {
        ret["error"] = "expect user message page timeout"
        return ret
    }
    node.recycle()

    val edMessage = w.expect(srv, newFilter { classFilter(it, EditTextClassName) })
    if (edMessage == null) {
        ret["error"] = "can not find message edit text control"
        return ret
    }
    ret["edMessage"] = edMessage
    ret["result"] = "success"
    return ret
}


fun sendRedPacket(srv: AccessibilityService, customWalker: NodeWalker?, name: String, amount: String, password: String, description: String? = null): JSONObject {
    val w = customWalker ?: NodeWalker()

    var ret = openChatPage(srv, w, name)
    if (ret.containsKey("error")) {
        return ret
    }
    (ret.remove("edMessage") as AccessibilityNodeInfo).recycle()

    return chatPageSendRedPacket(srv, w, amount, password, description)
}

fun isFriend(srv: AccessibilityService, customWalker: NodeWalker?, name: String): JSONObject {
    val w = customWalker ?: NodeWalker()
    val ret = transfer(srv, w, name, 0.01f)
    if (ret.containsKey("error")) {
        return ret
    }
    val btnTransfer = ret.remove("transfer") as AccessibilityNodeInfo
    ret.clear()

    w.expectRetry = Runnable{
        btnTransfer.refresh()
        if (btnTransfer.isVisibleToUser) btnTransfer.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    w.expectRetry!!.run()
    sleep(3000, 5000)

    var node = w.expect(srv, newFilter {
        if (classTextFilter(it, TextViewClassName, "你不是收款方好友") == FilterResult.Match) {
            ret["result"] = false
            ret["message"] = it.string()
            return@newFilter FilterResult.Match
        }
        if (classTextFilter(it, TextViewClassName, "支付方式") == FilterResult.Match) {
            ret["result"] = true
            ret["message"] = it.string()
            return@newFilter FilterResult.Match
        }
        FilterResult.Continue
    }, 4000)
    if (node == null) {
        ret["error"] = "determine friend in transfer page timeout, ${srv.getHierarchyString()}"
        return ret
    }
    node.recycle()

    if (ret["result"] == false) {
        w.expectRetry = null
        node = w.expect(srv, newFilter {
            if (classTextFilter(it, ButtonClassName, "确定", "知道") == FilterResult.Match) {
                it.click(srv)
                return@newFilter FilterResult.Continue
            }
            if (classTextFilter(it, TextViewClassName, "添加转账说明") == FilterResult.Match) {
                return@newFilter FilterResult.Match
            }
            FilterResult.Continue
        }, 2000, 5)
        node?.recycle()
        return ret
    }

    // deal with no bank card case, must click the "放弃" button in "放弃本次支付" dialogue
    w.expectRetry = null
    node = w.expect(srv, newFilter { classTextFilter(it, TextViewClassName, "立即支付") })
    if (node == null) {
        return ret
    }
    node.recycle()

    w.expectRetry = Runnable {
        srv.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    w.expectRetry!!.run()
    sleep(1000, 2000)
    node = w.expect(srv, newFilter { classTextFilter(it, ButtonClassName, "放弃") })
    if (node == null) {
        ret["message"] = "can not find abandon button, ${srv.getHierarchyString()}"
    } else {
        val btnAbandon = node
        w.expectRetry = Runnable {
            btnAbandon.refresh()
            if (btnAbandon.isVisibleToUser) btnAbandon.click(srv)
        }
        w.expectRetry!!.run()
        sleep(1000, 2000)
        node = w.expect(srv, newFilter { classTextFilter(it, TextViewClassName, "添加转账说明") })
        node?.recycle()
        btnAbandon.recycle()
    }
    return ret
}

// field "transfer" of returned JSONObject is set the to transfer button control when success
fun transfer(srv: AccessibilityService, customWalker: NodeWalker?, name: String, amount: Float): JSONObject {
    val w: NodeWalker = customWalker ?: NodeWalker()
    var ret = openMoreFunction(srv, w, name, MoreFuncTransfer)
    if (ret.containsKey("error")) {
        return ret
    }
    val transfer = ret.remove("func") as AccessibilityNodeInfo
    ret.clear()

    // goto the transfer page
    w.expectRetry = Runnable {
        transfer.refresh()
        if (transfer.isVisibleToUser) {
            transfer.click(srv)
        }
    }
    w.expectRetry!!.run()
    sleep(1000, 2000)

    var node = w.expect(srv, newFilter { classTextFilter(it, TextViewClassName, "转账金额") }, 2000)
    transfer.recycle()
    if (node == null) {
        ret["error"] = "wait transfer page timeout: ${srv.getHierarchyString()}"
        return ret
    }
    node.recycle()

    // find the edit text control in tranfer page
    w.expectRetry = null
    node = w.expect(srv, newFilter { classFilter(it, EditTextClassName) })
    if (node == null) {
        ret["error"] = "find amount edit text control in transfer page timeout: ${srv.getHierarchyString()}"
        return ret
    }

    ret = node.setContent("$amount")
    if (ret.containsKey("error")) {
        return ret
    }
    node.recycle()
    ret.clear()

    w.expectRetry = null
    val classHierarchy = String.format("%s,%s,%s", FrameLayoutClassName, LinearLayoutClassName, TextViewClassName)
    node = w.expect(srv, newFilter {
        if (classTextFilter(it, TextViewClassName, "转账") === FilterResult.Match && w.classesStr.endsWith(classHierarchy))
            FilterResult.Match
        else
            FilterResult.Continue
    })
    if (node == null) {
        ret["error"] = "find transfer button timeout after input transfer amount: ${srv.getHierarchyString()}"
        return ret
    }
    ret["transfer"] = node
    ret["success"] = true
    return ret
}

// func is set to the clickable function control with given name in returned JSONObject when success
fun openMoreFunction(srv: AccessibilityService, customWalker: NodeWalker?, name: String, func: String): JSONObject {
    val w: NodeWalker = customWalker ?: NodeWalker()
    val ret = openChatPage(srv, w, name)
    if (ret.containsKey("error")) {
        return ret
    }
    (ret.remove("edMessage") as AccessibilityNodeInfo).recycle()

    return chatPageOpenMoreFunction(srv, w, func)

}

// edMessage and btnSend will be set in returned JSONObject if success
fun sendTextMessage(srv: AccessibilityService, customWalker: NodeWalker?, name: String, message: String): JSONObject {
    val w: NodeWalker = customWalker ?: NodeWalker()
    var ret = openChatPage(srv, w, name)
    if (ret.containsKey("error")) return ret

    val edMessage = ret["edMessage"] as AccessibilityNodeInfo
    val espcaedMsg = message.replace("""\n""", "\n")
    ret = edMessage.setContent(espcaedMsg)
    if (ret.containsKey("error")) {
        edMessage.recycle()
        return ret
    }
    ret.clear()

    w.expectRetry = null
    val btnSend = w.expect(srv, newFilter { classTextFilter(it, ButtonClassName, "发送") })
    if (btnSend == null) {
        ret["error"] = "can not find send button control"
        edMessage.recycle()
        return ret
    }

    // random sleep some while before click the button
    sleep(500, 1000)
    btnSend.click(srv)

    ret["btnSend"] = btnSend
    ret["edMessage"] = edMessage
    ret["result"] = "success"
    return ret
}