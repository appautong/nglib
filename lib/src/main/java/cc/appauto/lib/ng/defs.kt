package cc.appauto.lib.ng

const val TAG = "autong"

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
    EditText(EditTextClassName),
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
