package cc.appauto.lib.ng

import android.content.Context
import android.graphics.*
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import cc.appauto.lib.R
import cc.appauto.lib.ng.AppAutoContext.ERR_NOT_READY
import com.alibaba.fastjson.JSONObject


private const val name = "autodraw"

class AutoDrawView @JvmOverloads constructor (ctx: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0): androidx.appcompat.widget.AppCompatImageView(ctx, attrs, defStyleAttr) {

   private var bitmap: Bitmap? = null
   private var canvas: Canvas = Canvas()

   companion object {
      private val defaultStrokePaint = Paint()
      init {
         defaultStrokePaint.color = Color.RED
         defaultStrokePaint.strokeWidth = 2f
         defaultStrokePaint.style = Paint.Style.STROKE
      }
   }

   override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
      bitmap?.recycle()
      Log.i(TAG, "$name: onSizeChanged, ($oldw, $oldh) -> ($w, $h)")
      bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      setImageBitmap(bitmap)
      canvas.setBitmap(bitmap)
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

      super.onSizeChanged(w, h, oldw, oldh)
   }

   fun drawRectStroke(bound: Rect, paint: Paint? = null) {
      val p = paint ?: defaultStrokePaint
      canvas.drawRect(bound, p)
      postInvalidate()
   }

   fun reset() {
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
      invalidate()
   }
}

object AutoDraw {
   val canDrawOverlay
      get() = initialized && Settings.canDrawOverlays(ctx)

   // ready flag is set when the autodraw imagevie is added by window manager successfully
   // PS. in ready state, the user could still cancel the overlay draw permission, in
   // such cases, a warning message will be printed every time
   var ready: Boolean = false
      private set

   lateinit var autoDrawImage: AutoDrawView
      private set
   lateinit var ctx: Context
      private set

   private lateinit var autoDrawRoot: View
   private var autoDrawWindowParams = WindowManager.LayoutParams()

   private var initialized: Boolean = false

   init {
      initAutoDrawParam()
   }

   internal fun setup(appContext: Context) {
      autoDrawRoot = LayoutInflater.from(AppAutoContext.appContext).inflate(R.layout.autodraw, null)
      autoDrawImage = autoDrawRoot.findViewById(R.id.imgAutoDraw)
      ctx = appContext
      initialized = true
      Log.i(TAG, "$name: autodraw initialized")
   }

   private fun initAutoDrawParam() {
      // initialize the window parameters for the autodraw view
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         autoDrawWindowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      } else {
         autoDrawWindowParams.type = WindowManager.LayoutParams.TYPE_PHONE
      }
      autoDrawWindowParams.gravity = Gravity.START or Gravity.TOP
      autoDrawWindowParams.format = PixelFormat.RGBA_8888
      autoDrawWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
      autoDrawWindowParams.width = WindowManager.LayoutParams.MATCH_PARENT
      autoDrawWindowParams.height = WindowManager.LayoutParams.MATCH_PARENT
      autoDrawWindowParams.x = 0
      autoDrawWindowParams.y = 0
   }

   /*** add the autodraw imageview to window manager
    *  return true if draw overlay permission acquired and the auto draw root view added;
    *  otherwise, return false
    */
   @Synchronized
   internal fun addAutoDrawImageView(): Boolean {
      if (ready) return true

      if (!initialized) return false
      if (!canDrawOverlay) return false

      try {
         AppAutoContext.windowManager.addView(autoDrawRoot, autoDrawWindowParams)
         ready = true
         Log.i(TAG, "$name: addAutoDrawImageView successfully")
      } catch (e: Exception) {
         Log.e(TAG, "$name: addAutoDrawImageView leads to exception: ${Log.getStackTraceString(e)}")
      }
      return true
   }

   fun markBound(bound: Rect, paint: Paint? = null): JSONObject {
      val ret = JSONObject()
      if (!initialized) return ret.also { it["error"] = ERR_NOT_READY }
      if (!canDrawOverlay) return ret.also { it["error"] = "need draw overlay permission, invoke openOverlayPermissionSetting first"}
      if (!ready && !addAutoDrawImageView()) return ret.also { it["error"] = "add autodraw imageview failed" }

      autoDrawImage.drawRectStroke(bound, paint)
      return ret.also { ret["result"] = "success" }
   }
}