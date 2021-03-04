package cc.appauto.lib.ng

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private const val name = "automedia"

@SuppressLint("StaticFieldLeak")
object MediaRuntime {
    lateinit var ctx: Context
        private set

    val displayMetrics = DisplayMetrics()

    val ready: Boolean
        get() = initialized && mediaProjection != null && display != null

    lateinit private var requestLauncher: ActivityResultLauncher<Intent>

    private var mediaProjection: MediaProjection? = null
    lateinit private var imageReader: ImageReader
    private var display: VirtualDisplay? = null
    private var initialized: Boolean = false

    internal fun setup(activity: AppCompatActivity) {
        ctx = activity.applicationContext

        AppAutoContext.windowManager.defaultDisplay.getMetrics(displayMetrics)

        imageReader = ImageReader.newInstance(displayMetrics.widthPixels, displayMetrics.heightPixels, PixelFormat.RGBA_8888, 3)
        requestLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK && it.data != null) {
                Log.w(TAG, "$name: request media projection approved by user, start media service now")
                val intent = Intent(ctx, AppAutoMediaService::class.java)
                intent.putExtra("data", it.data)
                intent.putExtra("resultCode", it.resultCode)
                intent.putExtra("", it.resultCode)
                ctx.startService(intent)
            }
            else {
                Log.w(TAG, "$name: request media projection denied by user, result code: ${it.resultCode}")
            }
        }
        initialized = true
        Log.i(TAG, "$name: automedia initialized")
    }

    fun requestMediaProjection(): Boolean {
        requestLauncher.launch(AppAutoContext.mediaProjectionManager.createScreenCaptureIntent())
        return true
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
}