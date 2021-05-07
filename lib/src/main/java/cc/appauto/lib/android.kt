package cc.appauto.lib

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo


const val WechatPackageName =  "com.tencent.mm"
const val RelativeLayoutClassName = "android.widget.RelativeLayout"
const val LinearLayoutClassName = "android.widget.LinearLayout"
const val TextViewClassName = "android.widget.TextView"
const val EditTextClassName = "android.widget.EditText"
const val FrameLayoutClassName = "android.widget.FrameLayout"
const val ImageViewClassName = "android.widget.ImageView"
const val ImageButtonClassName = "android.widget.ImageButton"
const val ViewClassName = "android.view.View"
const val ViewGroupClassName = "android.view.ViewGroup"
const val ListViewClassName = "android.widget.ListView"
const val WebViewClassName = "android.webkit.WebView"
const val ButtonClassName = "android.widget.Button"

// todo controls use classes in android support library may be changed in future
// e.g: migrate to latest support library version
const val RecyclerViewClassName = "android.support.v7.widget.RecyclerView"
const val ViewPagerClassName = "android.support.v4.view.ViewPager"

enum class ClassName(val qualifiedName: String) {
    FrameLayout(FrameLayoutClassName),
    RelativeLayout(RelativeLayoutClassName),
    Linearlayout(LinearLayoutClassName),
    TextView(TextViewClassName),
    EditTextClass(EditTextClassName),
    Button(ButtonClassName),
    ImageView(ImageViewClassName),
    ImageButton(ImageButtonClassName),
    View(ViewClassName),
    ViewGroup(ViewGroupClassName),
    ListView(ListViewClassName),
    WebView(WebViewClassName),
    RecyclerView(RecyclerViewClassName),
    ViewPager(ViewPagerClassName);

    override fun toString(): String {
        return this.qualifiedName
    }
}

fun bringFront(ctx: Context) {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    am.appTasks[0].moveToFront()
}


fun openApp(srv: AccessibilityService?, ctx: Context, packageName: String, timeoutMS: Long = 4000): Boolean {
    var now = System.currentTimeMillis()
    val deadline = now + timeoutMS
    var interval = timeoutMS.shr(3)
    if (interval < 500) interval = 500

    val intent = ctx.packageManager.getLaunchIntentForPackage(packageName) ?: return false

    ctx.startActivity(intent)

    var top: AccessibilityNodeInfo?
    do {
        top = getTopAppNode(srv, packageName)
        if (top != null) {
            top.recycle()
            return true
        }
        sleep(interval)
        // retry open the app
        ctx.startActivity(intent)
        now = System.currentTimeMillis()
    } while (now < deadline)
    return false
}

fun quitApp(service: AccessibilityService?, packageName: String, maxBackCount : Int = 10) {
    if (service == null)
        return

    var tmpNode = getTopAppNode(service, packageName)
    var backed = 0
    while (tmpNode != null && backed < maxBackCount) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        tmpNode.recycle()
        backed++
        sleep(200)
        tmpNode = getTopAppNode(service, packageName)
    }
    tmpNode?.recycle()

    if (backed == maxBackCount) {
        Log.w(TAG, "backed ${backed} times without quit ${packageName}, press home")
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }
}

fun getTopAppNode(srv: AccessibilityService?, packageName: String? = null): AccessibilityNodeInfo? {
    if (srv == null) return null;

    var node: AccessibilityNodeInfo? = null
    var tried = 0
    while(node == null && tried < 3) {
        tried++
        node = srv.tryRootInActiveWindow()
        if (node != null && (packageName == null || packageName == node.packageName)) {
            return node
        }

        node = try {
            srv.windows.find {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root != null && (packageName == null || it.root.packageName == packageName)
            }?.root
        } catch (e: Exception) {
            Log.e(TAG, "getTopAppNode leads to exception: ${e.message}")
            null
        }
        sleep(100)
    }
    return node
}


/** MUST be called in thread other than main UI thread to get the proper result
 * it's strange that there is no input method window in srv.windows
 * however, when check the srv.windows sometime later in main thread,
 * the input method window can be found. I'm guessing that that srv.windows
 * will be refreshed/updated only in main UI thread, so, the current function shall
 * not be call in main thread
 *
 **/
fun getInputIMEWindow(srv: AccessibilityService?) : AccessibilityWindowInfo? {
    if (srv == null) return null

    return try {
        srv.windows.find {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
    } catch (e: Exception) {
        Log.e(TAG, "getInputIMEWindow: ${Log.getStackTraceString(e)}")
        null
    }
}

private var cachedAppWinRect: Rect? = null

fun getAppBound(srv: AccessibilityService?): Rect? {
    if (cachedAppWinRect != null) return cachedAppWinRect

    if (srv == null) return null

    val ret = Rect()

    val wm = srv.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    var p = Point()
    wm.defaultDisplay.getRealSize(p)

    ret.left = 0
    ret.right = p.x

    // enumerate the windows to exclude any top/bottom navigation bar
    srv.windows.forEach {
        if (it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.layer > 0) {
            var r = Rect()
            it.getBoundsInScreen(r)
            if (r.top == 0) {
                ret.top = r.bottom
            } else if (r.bottom == p.y) {
                ret.bottom = r.top
            }
        }
    }
    cachedAppWinRect = ret

    return ret
}