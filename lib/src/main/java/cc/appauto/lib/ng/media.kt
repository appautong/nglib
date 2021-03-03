package cc.appauto.lib.ng

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.ImageWriter
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import com.alibaba.fastjson.JSONObject
import java.io.File
import java.io.FileOutputStream

private const val name = "automedia"

@SuppressLint("StaticFieldLeak")
object MediaRuntime {
    lateinit var ctx: Context
        private set

    val displayMetrics = DisplayMetrics()


    val ready: Boolean
        get() = initialized && mediaProjection != null && display != null

    lateinit private var imageReader: ImageReader
    private var mediaProjection: MediaProjection? = null
    private var display: VirtualDisplay? = null

    private var initialized: Boolean = false

    fun setup(appContext: Context) {
        initialized = true
        ctx = appContext

        AppAutoContext.windowManager.defaultDisplay.getMetrics(displayMetrics)

        imageReader = ImageReader.newInstance(displayMetrics.widthPixels, displayMetrics.heightPixels, PixelFormat.RGBA_8888, 3)
        Log.i(TAG, "$name: automedia initialized")
    }

    fun startMediaProjection(mp: MediaProjection) {
        if (ready) stopMediaProjection()

        mediaProjection = mp
        display = mediaProjection?.createVirtualDisplay(
            "screenshot",
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )
        imageReader.setOnImageAvailableListener({onImageAvailable(it)}, AppAutoContext.workHandler)
    }

    fun stopMediaProjection() {
        display?.release()
        display = null
        imageReader.setOnImageAvailableListener(null, null)
    }

    fun onImageAvailable(imageReader: ImageReader) {
        val image = imageReader.acquireLatestImage() ?: return
        if (image.planes.isEmpty()) {
            image.close()
            return
        }
        val plane = image.planes[0]
        val width = plane.rowStride/plane.pixelStride
        Log.i(TAG, "$name: image available, plane: ${plane.rowStride}/${plane.pixelStride}, image: ${image.width} x ${image.height}")
        var bitmap = Bitmap.createBitmap(width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(plane.buffer)

        try {
            val dir = ctx.getExternalFilesDir(null)
            val fn = File(dir, "${getDateStr()}.jpg")
            val fos = FileOutputStream(fn)
            // crop the bitmap to image.width/image.height
            if (width != image.width) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        } catch (e: Exception){
            Log.e(TAG, "$name: save screenshot bitmap leads to exception:\n${Log.getStackTraceString(e)}")
        }
        image.close()
    }

    fun takeScreenShot(): JSONObject {
        val ret = JSONObject()
        if (!ready) return ret.also { it["error"] = "$name: media projection is not ready" }



        return ret.also { ret["result"] = "success" }
    }
}