package cc.appauto.lib.ng

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.*
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.alibaba.fastjson.JSONObject
import java.io.ByteArrayOutputStream

@SuppressLint("StaticFieldLeak")
object MediaRuntime {
    private val name = "media"
    private val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_HEVC
    private val frameRate = 10

    val displayMetrics = DisplayMetrics()

    // executor to run media work
    var executor = HandlerExecutor("${TAG}_${name}")

    internal lateinit var imageReader: ImageReader
    internal var codec: MediaCodec? = null

    private lateinit var requestLauncher: ActivityResultLauncher<Intent>
    private lateinit var ctx: Context

    private var lastImageSaveTs: Long = 0
    private var lastImageSaveMs: Long = 0

    private var screenshots: MutableList<Bitmap> = mutableListOf()
    private var maxScreenshotCount = 5

    private var requestFinishMutex = Object()
    private var requestStatus: Int = -1
    private var initialized: Boolean = false

    private fun prepareMediaRequestLauncher(activity: AppCompatActivity) {
        requestLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK && it.data != null) {
                Log.w(TAG, "$name: request media projection approved by user, start media service now")
                val intent = Intent(ctx, AppAutoMediaService::class.java)
                intent.putExtra("data", it.data)
                intent.putExtra("resultCode", it.resultCode)
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
        Log.i(TAG, "$name: display metrics: w: ${displayMetrics.widthPixels} h: ${displayMetrics.heightPixels}")

        imageReader = ImageReader.newInstance(displayMetrics.widthPixels, displayMetrics.heightPixels, PixelFormat.RGBA_8888, 5)
        imageReader.setOnImageAvailableListener({ onImageAvailable(it)}, executor.workHandler)

        prepareMediaRequestLauncher(activity)

        initialized = true
        Log.i(TAG, "$name: initialized")
    }

    private val codecCallback = object: MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            Log.i(TAG, "$name: onInputBufferAvailable: $index")
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                // codec specific data shall abe skiped
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
                val img = codec.getOutputImage(index)
                Log.i(TAG, "$name: onOutputBufferAvailable: ${info.flags} ${img}")
                img?.apply { saveImage(this, System.currentTimeMillis())}
            }
            codec.releaseOutputBuffer(index, false)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.i(TAG, "$name: onError: ${codec.name} : ${e.message}")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            Log.i(TAG, "$name: onOutputFormatChanged: ${codec.name} $format")
        }
    }

    internal fun releaseVideoEncoder() {
        codec?.stop()
        codec?.release()
        codec = null
    }

    internal fun setupVideoEncoder() {
        val w = if (displayMetrics.widthPixels % 2 == 0)  displayMetrics.widthPixels else displayMetrics.widthPixels+1
        val h = if (displayMetrics.heightPixels % 2 == 0)  displayMetrics.heightPixels else displayMetrics.heightPixels+1
        val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, w, h)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_CAPTURE_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000/ frameRate)
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        try {
            codec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
            codec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec?.setCallback(codecCallback, executor.workHandler)
        } catch (e: Exception) {
            Log.e(TAG, "$name: prepare video encoder leads to exceptoin:\n${Log.getStackTraceString(e)}")
        }
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
        val ms = System.currentTimeMillis()
        val image = imageReader.acquireLatestImage() ?: return
        if (ms - lastImageSaveMs < 100 ) {
            image.close()
            return
        }
        if (image.planes.isEmpty()) {
            image.close()
            return
        }
        saveImage(image, ms)
    }

    fun getScreenShot(): JSONObject {
        val ret = JSONObject()
        if (!initialized) return ret.also { it["error"] = "$name: not initialized yet"}

        val hexString = executor.executeTask {
            screenshots.lastOrNull()?.compressToBase64HexString()
        } ?: return ret.also { it["error"] = "empty screenshot queue"}

        return ret.also { ret["result"] = hexString}
    }

    private fun Bitmap.compressToBase64HexString() : String {
        val bos = ByteArrayOutputStream(65536)
        this.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.DEFAULT)
    }

    private fun saveImage(image: Image, ms: Long) {
        //  replace the last image with the same second, keep only the last image in one second.
        var replaceLast = false
        val ts = ms/1000

        lastImageSaveMs = ms
        if (lastImageSaveTs == ts) replaceLast = true else lastImageSaveTs = ts

        Log.i(TAG, "$name: save image at $ms, replace: $replaceLast")

        val plane = image.planes[0]
        val width = plane.rowStride/plane.pixelStride

        // Log.v(TAG, "$name: image available, plane: ${plane.rowStride}/${plane.pixelStride}, image: ${image.width} x ${image.height}")

        var bitmap = Bitmap.createBitmap(width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(plane.buffer)

        try {
            // crop the bitmap to image.width/image.height
            if (width != image.width) {
                val tmp = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                bitmap.recycle()
                bitmap = tmp
            }
            when {
                replaceLast -> screenshots.removeLast().recycle()
                screenshots.size > maxScreenshotCount -> screenshots.removeFirst().recycle()
            }
            screenshots.add(bitmap)
        } catch (e: Exception){
            Log.e(TAG, "$name: save screenshot bitmap leads to exception:\n${Log.getStackTraceString(e)}")
        }
        image.close()
    }
}