package cc.appauto.lib.ng

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log

class AutoDraw @JvmOverloads constructor (ctx: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0): androidx.appcompat.widget.AppCompatImageView(ctx, attrs, defStyleAttr) {
   private val name = "autodraw"

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
