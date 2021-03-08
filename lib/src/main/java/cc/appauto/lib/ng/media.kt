package cc.appauto.lib.ng

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.alibaba.fastjson.JSONObject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import kotlin.system.measureTimeMillis

private data class ImageSaveEntry(val ts: Long, val img: Image) {
    val unix = ts/1000
}

@SuppressLint("StaticFieldLeak")
object MediaRuntime {
    private val name = "media"

    val displayMetrics = DisplayMetrics()

    // executor to run media work
    var executor = HandlerExecutor("${TAG}_${name}")

    internal lateinit var imageReader: ImageReader

    private lateinit var requestLauncher: ActivityResultLauncher<Intent>
    private lateinit var ctx: Context

    private var lastImageSaveTs: Long = 0
    // screenshots: list of base64 encoded jpeg data
    private var screenshots: MutableList<String> = mutableListOf()
    private var maxScreenshotCount = 5

    private var requestFinishMutex = Object()
    private var requestStatus: Int = -1
    private var initialized: Boolean = false

    private var saveJob: Job? = null
    private var imageSaveChannel: Channel<ImageSaveEntry> = Channel(4, BufferOverflow.DROP_OLDEST) {
        Log.i(TAG, "undelivered: ${it.ts}")
        it.img.close()
    }

    private fun prepareMediaRequestLauncher(activity: AppCompatActivity) {
        requestLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK && it.data != null) {
                Log.w(TAG, "$name: request media projection approved by user, start media service now")
                val intent = Intent(ctx, AppAutoMediaService::class.java)
                intent.putExtra("data", it.data)
                intent.putExtra("resultCode", it.resultCode)
                intent.putExtra("surface", imageReader.surface)
                ctx.startService(intent)
                requestStatus = 1
            }
            else {
                Log.w(TAG, "$name: request media projection denied by user, result code: ${it.resultCode}")
                requestStatus = 0
            }
            synchronized(requestFinishMutex) {
                requestFinishMutex.notify()
            }
        }
    }

    // if setup invoked again after initialized, just update the request launcher with given activity and return
    @Synchronized
    internal fun setup(activity: AppCompatActivity) {
        if (initialized) {
            requestLauncher.unregister()
            prepareMediaRequestLauncher(activity)
            Log.i(TAG, "$name: update media request launcher with latest activity")
            return
        }

        ctx = activity.applicationContext

        AppAutoContext.windowManager.defaultDisplay.getMetrics(displayMetrics)
        imageReader = ImageReader.newInstance(displayMetrics.widthPixels, displayMetrics.heightPixels, PixelFormat.RGBA_8888, 5)
        prepareMediaRequestLauncher(activity)

        saveJob = executor.scope.launch {
            imageSaveChannel.consumeAsFlow().collect {
                saveImage(it)
            }
        }

        initialized = true
        Log.i(TAG, "$name: initialized")
    }

    /**
     * requestMediaProjection must not be called in main thread, otherwise, it will
     * block the main thread infinitely or at least the given timeout. Because the
     * request result callback will run in the main thread, and, if the function runs
     * in main thread, the "wait" operation below will block the main thread to "wait"
     * the notification in request result callback, OOPs...
     * @param timeout wait given timeout in unit of milli-second (0 means infinitely)
     * @return -2: can not call this function in main thread; -1 timeout; 0: user denied; 1: user approved
     */
    @Synchronized
    @JvmOverloads
    fun requestMediaProjection(timeout: Long = 0): Int {
        if (Looper.getMainLooper().isCurrentThread) return -2

        requestStatus = -1
        val ms = if(timeout < 0)  0 else timeout

        requestLauncher.launch(AppAutoContext.mediaProjectionManager.createScreenCaptureIntent())
        synchronized(requestFinishMutex) {
            requestFinishMutex.wait(ms)
        }
        return requestStatus
    }

    // the producer onImageAvailable is run in executor.workHandler, as the same as consumer getScreenShot
    // so  it's not required to protect the screenshots with lock
    fun onImageAvailable(imageReader: ImageReader) {
        val image = imageReader.acquireLatestImage() ?: return
        if (image.planes.isEmpty()) {
            image.close()
            return
        }

        executor.scope.launch {
            imageSaveChannel.send(ImageSaveEntry(System.currentTimeMillis(), image))
        }
    }

    fun getScreenShot(n: Int = 0): JSONObject {
        val ret = JSONObject()
        if (!initialized) return ret.also { it["error"] = "$name: not initialized yet"}

        val screenshot = executor.executeTask {
           screenshots.lastOrNull()
        }
        if (screenshot.isNullOrEmpty()) return ret.also { it["error"] = "empty screenshot queue"}

        return ret.also { ret["result"] = screenshot}
    }

    private fun saveImage(entry: ImageSaveEntry) {
        Log.i(TAG, "$name: save image ${entry.ts}")
        //  replace the last image if dateStr is not changed, that is, for image with the same second, keep only the last image.

        var replace = false
        if (lastImageSaveTs == entry.unix) {
            replace = true
        } else {
            lastImageSaveTs = entry.unix
        }

        val image = entry.img
        val plane = image.planes[0]
        val width = plane.rowStride/plane.pixelStride

        // Log.v(TAG, "$name: image available, plane: ${plane.rowStride}/${plane.pixelStride}, image: ${image.width} x ${image.height}")

        var bitmap = Bitmap.createBitmap(width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(plane.buffer)

        try {
            val bos = ByteArrayOutputStream(65536)

            // crop the bitmap to image.width/image.height
            if (width != image.width) {
                val tmp = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                bitmap.recycle()
                bitmap = tmp
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos)
            val screenshot = Base64.encodeToString(bos.toByteArray(), Base64.DEFAULT)

            if (replace) {
                screenshots.set(screenshots.lastIndex, screenshot)
            } else {
                if (screenshots.size > maxScreenshotCount) {
                    screenshots.removeFirst()
                }
                screenshots.add(screenshot)
            }
        } catch (e: Exception){
            Log.e(TAG, "$name: save screenshot bitmap leads to exception:\n${Log.getStackTraceString(e)}")
        }
        bitmap.recycle()
        image.close()
    }
}