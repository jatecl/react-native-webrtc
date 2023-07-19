@file:Suppress("DEPRECATION")
package com.oney.WebRTCModule;

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.AudioManager
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.StatFs
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.webrtc.*
import java.io.File
import java.io.File.separator
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


/**
 * 单次视频存储
 */
/**
 * 创建一个新的存储会话，并马上开始录制
 *
 * @param context       程序上下文
 * @param camera        使用的摄像头
 * @param profile       摄像头描述
 */
@RequiresApi(Build.VERSION_CODES.N)
class LocalRecordSession(
    private val context: Context,
    private val camera: Camera,
    private val profile: CamcorderProfile
) {
    private var mediaRecorder: MediaRecorder? = null

    private var isRecording = false

    var shouldRecreate = false
        private set

    /**
     * 获得一个视频文件的存储路径
     *
     * @return 视频文件的存储路径
     */
    @SuppressLint("SimpleDateFormat")
    private fun createOutputMediaFile(): String {
        var file: File? = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            && Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            try {
                val cls = Class.forName("com.mediatek.storage.StorageManagerEx")
                val method = cls.getMethod("getDefaultPath")
                val path = method.invoke(null) as String
                file = File("$path/DCIM")
                if (!file.exists()) file.mkdirs()
                if (file.canWrite()) checkDiskSize(file)
                else file = null
            } catch (ignore: java.lang.Exception) {
                file = null
                ignore.printStackTrace()
            }
        }
        if (file == null) {
            val dirs = context.externalMediaDirs
            file = if (dirs.isEmpty()) context.filesDir
            else dirs.last()
            if (!file.exists()) file.mkdirs()
            checkDiskSize(file)
        }
        val filepath = file!!.absolutePath
        val timeString = SimpleDateFormat("yyyyMMddHHmmss").format(Date())
        return "$filepath$separator$timeString.3gp"
    }

    init {
        if (prepareVideoRecorder()) {
            try {
                mediaRecorder!!.start()
                isRecording = true
            } catch (exp: Exception) {
                exp.printStackTrace()
                releaseMediaRecorder()
            }

        } else {
            releaseMediaRecorder()
        }
    }

    /**
     * 结束存储
     */
    fun stopRecord() {
        if (isRecording) {
            try {
                updateFileExists(recordingFilename)
                mediaRecorder?.stop()
            } catch (e: RuntimeException) {
                e.printStackTrace()
            }
            if (!releaseMediaRecorder()) camera.lock()
            isRecording = false
        }
    }

    /**
     * 销毁视频存储器
     *
     * @return 是否执行了销毁动作
     */
    private fun releaseMediaRecorder(): Boolean {
        if (mediaRecorder != null) {
            try {
                mediaRecorder!!.reset()
                mediaRecorder!!.release()
                mediaRecorder = null
                camera.lock()
                return true
            } catch (exp: Exception) {
                exp.printStackTrace()
            }

        }
        return false
    }

    private var recordingFilename: String? = null

    /**
     * 初始化视频存储
     *
     * @return 是否初始化成功
     */
    private fun prepareVideoRecorder(): Boolean {
        try {
            Log.i(TAG, "Number of active recording sessions: createMediaRecorder")
            mediaRecorder = MediaRecorder()
            camera.unlock()
            mediaRecorder!!.setCamera(camera)

            if (LocalRecorder.RecordAudio) mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.CAMERA)

            //mediaRecorder!!.setOutputFormat(profile.fileFormat)
            mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            mediaRecorder!!.setVideoFrameRate(profile.videoFrameRate)
            mediaRecorder!!.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)
            mediaRecorder!!.setVideoEncodingBitRate(profile.videoBitRate)
            mediaRecorder!!.setVideoEncoder(profile.videoCodec)

            if (LocalRecorder.RecordAudio) {
                val audioManager = context.getSystemService(Service.AUDIO_SERVICE) as AudioManager
                val configs = audioManager.activeRecordingConfigurations
                if (configs.size > 0) {
                    val config = configs[0]
                    mediaRecorder!!.setAudioEncodingBitRate(profile.audioBitRate)
                    mediaRecorder!!.setAudioChannels(config.format.channelCount)
                    mediaRecorder!!.setAudioSamplingRate(config.format.sampleRate)
                    mediaRecorder!!.setAudioEncoder(config.format.encoding)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        mediaRecorder!!.setAudioSource(config.audioSource)
                    }
                }
                else {
                    mediaRecorder!!.setAudioEncodingBitRate(profile.audioBitRate)
                    mediaRecorder!!.setAudioChannels(profile.audioChannels)
                    mediaRecorder!!.setAudioSamplingRate(profile.audioSampleRate)
                    mediaRecorder!!.setAudioEncoder(profile.audioCodec)
                }
            }

            /*
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && profile.fileFormat == MediaRecorder.OutputFormat.MPEG_4) {
                Console.error("mediaRecorder shouldRecreate false")
                mediaRecorder!!.setMaxFileSize(100000000L)//536870912L
                mediaRecorder!!.setOnErrorListener { mr, num1, num2 ->
                    if (mr == mediaRecorder) shouldRecreate = true
                    Console.error("mediaRecorder setOnErrorListener $num1 $num2")
                }
                mediaRecorder!!.setOnInfoListener { mr, what, _ ->
                    if (mr != mediaRecorder) return@setOnInfoListener
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                        try {
                            val nextFile = createOutputMediaFile()
                            mr?.setNextOutputFile(File(nextFile))
                            updateFileExists(recordingFilename)
                            recordingFilename = nextFile
                            Console.error("mediaRecorder next file $nextFile")
                        } catch (e: Throwable) {
                            Console.error("mediaRecorder Exception " + e.message)
                            shouldRecreate = true
                            e.printStackTrace()
                        }
                    }
                }
            } else {
                Console.error("mediaRecorder shouldRecreate true")
                shouldRecreate = true
            }
            */
            shouldRecreate = true

            val firstFile = createOutputMediaFile()
            mediaRecorder!!.setOutputFile(firstFile)
            recordingFilename = firstFile
            mediaRecorder!!.prepare()
        } catch (e: Exception) {
            e.printStackTrace()
            releaseMediaRecorder()
            return false
        }

        return true
    }

    private fun updateFileExists(file: String?) {
        if (file == null) return
        val mtm = MimeTypeMap.getSingleton()
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file),
            arrayOf(mtm.getMimeTypeFromExtension(file.substring(file.lastIndexOf(".") + 1)))
        ) { _, _ ->
            //Log.d("", "刷新完毕");
        }
    }

    /**
     * 当内存卡容量少于1024M时，自动删除视频列表里面的第一个文件
     */
    @SuppressLint("DefaultLocale")
    private fun checkDiskSize(path: File) {
        if (!path.canWrite()) {
            Log.i(TAG, "无写入权限")
            return
        }
        val statFs = StatFs(path.path)
        var minSize = 1073741824L   //1Gb‬
        if (context.externalMediaDirs.size <= 1) {
            minSize += statFs.totalBytes / 10
            Log.i(TAG, "没有插入TF卡")
        }
        else {
            Log.i(TAG, "找到TF卡")
        }
        if (statFs.availableBytes < minSize) {
            val childFile = path.list()
            var deleted = 0
            if (childFile.isNotEmpty()) {
                val fileList = ArrayList<String>()
                fileList.addAll(childFile)
                fileList.sortWith(Comparator { o1, o2 ->
                    o1.compareTo(o2)
                })

                var bytes = statFs.availableBytes
                for (name in fileList) {
                    val file = File(path, name)
                    if (file.isDirectory) continue
                    val extension = file.extension.toLowerCase()
                    if (extension != "3gp" && extension != "mp4") continue
                    val length = file.length()
                    if (file.delete()) {
                        updateFileExists(file.absolutePath)
                        ++deleted
                        Log.i(TAG, "已删除$name,$length")
                        bytes += length
                        if (bytes >= minSize) {
                            Log.i(TAG, "磁盘空间足够了")
                            break
                        }
                    } else {
                        Log.i(TAG, "删除失败$name")
                    }
                }
            }
            if (deleted == 0) {
                Log.i(TAG, "没有可删除的文件")
            }
        } else {
            Log.i(TAG, "磁盘空间足够了")
        }
    }

    companion object {
        private val TAG = "LocalRecordSession"
    }
}

/**
 * 本地存储
 */
class LocalRecorder {
    private var context: Context? = null
    private var camera: Camera? = null
    private var recordSession: LocalRecordSession? = null
    private var profile: CamcorderProfile? = null
    private val locker = Any()
    private var isRecording = true
    private var recordVersion = 0

    private val handler = Handler()

    @RequiresApi(Build.VERSION_CODES.N)
    fun startRecord(context: Context, camera: Camera, profile: CamcorderProfile) {
        this.context = context
        this.camera = camera
        this.profile = profile
        synchronized(locker) {
            isRecording = true
            this.createSession()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun createSession() {
        this.stopRecordPrivate()

        if (Record) {
            recordSession = LocalRecordSession(context!!, camera!!, profile!!)
            setNext()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setNext() {
        ++recordVersion
        val currentVersion = recordVersion
        handler.postDelayed({
            synchronized(locker) {
                if (isRecording && currentVersion == recordVersion) {
                    if (recordSession != null && !recordSession!!.shouldRecreate)
                        setNext()
                    else
                        createSession()
                }
            }
        }, 300000)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun stopRecordPrivate() {
        if (recordSession != null) {
            recordSession!!.stopRecord()
            recordSession = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun stopRecord() {
        synchronized(locker) {
            isRecording = false
            stopRecordPrivate()
        }
    }

    companion object {
        public var Record: Boolean = false
        public var RecordAudio: Boolean = false
        public var RecordResolution: Int = 0
        public var LockOrientation: Int = 90
    }
}

internal interface CameraSessionHandler {
    fun startCamera(context: Context, camera: Camera, profile: CamcorderProfile)
    fun stopCamera(camera: Camera)
}

internal abstract class AbstractCameraSession private constructor(
    private val events: Events,
    private val applicationContext: Context,
    surfaceTextureHelper: SurfaceTextureHelper,
    private val cameraId: Int,
    private val camera: Camera,
    private val profile: CamcorderProfile
) {
    interface Events {
        fun onCameraOpening()
        fun onCameraError(sessionLocal: AbstractCameraSession, error: String)
        fun onCameraDisconnected(sessionLocal: AbstractCameraSession)
        fun onCameraClosed(sessionLocal: AbstractCameraSession)
    }

    interface CreateSessionCallback {
        fun onDone(sessionLocal: AbstractCameraSession)
        fun onFailure(error: String)
    }

    private val cameraThreadHandler: Handler
    private var state: SessionState? = null

    private enum class SessionState {
        RUNNING, STOPPED
    }

    init {
        Logging.d(TAG, "Create new camera1 session on camera $cameraId")
        this.cameraThreadHandler = Handler()
        surfaceTextureHelper.setTextureSize(profile.videoFrameWidth, profile.videoFrameHeight)
        startCapturing()
    }

    fun stop() {
        Logging.d(TAG, "Stop camera1 session on camera $cameraId")
        checkIsOnCameraThread()
        if (state != SessionState.STOPPED) {
            stopInternal()
        }
    }

    private fun startCapturing() {
        Logging.d(TAG, "Start capturing")
        checkIsOnCameraThread()
        state = SessionState.RUNNING
        camera.setErrorCallback { error, _ ->
            val errorMessage: String = if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                "Camera server died!"
            } else {
                "Camera error: $error"
            }
            Logging.e(TAG, errorMessage)
            stopInternal()
            if (error == Camera.CAMERA_ERROR_EVICTED) {
                events.onCameraDisconnected(this@AbstractCameraSession)
            } else {
                events.onCameraError(this@AbstractCameraSession, errorMessage)
            }
        }
        try {
            camera.startPreview()
        } catch (e: RuntimeException) {
            stopInternal()
            e.message?.let { events.onCameraError(this, it) }

            return
        }

        startCamera(applicationContext, camera, profile)
    }

    protected abstract fun startCamera(context: Context, camera: Camera, profile: CamcorderProfile)

    protected abstract fun stopCamera()

    private fun stopInternal() {
        Logging.d(TAG, "Stop internal")
        checkIsOnCameraThread()
        if (state == SessionState.STOPPED) {
            Logging.d(TAG, "Camera is already stopped")
            return
        }
        stopCamera()
        state = SessionState.STOPPED

        camera.stopPreview()
        camera.release()
        events.onCameraClosed(this)
        Logging.d(TAG, "Stop done")
    }

    private fun checkIsOnCameraThread() {
        if (Thread.currentThread() !== cameraThreadHandler.looper.thread) {
            throw IllegalStateException("Wrong thread")
        }
    }

    companion object {
        private const val TAG = "AbstractCameraSession"


        fun create(
            callback: CreateSessionCallback, events: Events,
            applicationContext: Context,
            surfaceTextureHelper: SurfaceTextureHelper,
            cameraId: Int,
            handler: CameraSessionHandler? = null
        ) {
            Logging.d(TAG, "Open camera $cameraId")
            events.onCameraOpening()
            val camera: Camera?
            try {
                camera = Camera.open(cameraId)
            } catch (e: RuntimeException) {
                callback.onFailure(e.message!!)
                return
            }

            if (camera == null) {
                callback.onFailure("android.hardware.Camera.open returned null for camera id = $cameraId")
                return
            }
            try {
                camera.setPreviewTexture(surfaceTextureHelper.surfaceTexture)
            } catch (e: Exception) {
                camera.release()
                callback.onFailure(e.message!!)
                return
            }


            // Use the same size for recording profile.
            var pro: CamcorderProfile? = null
            val formats = arrayOf(
                CamcorderProfile.QUALITY_1080P,
                CamcorderProfile.QUALITY_720P,
                CamcorderProfile.QUALITY_480P
            )
            for (i in getRecordResolution(applicationContext)..formats.size) {
                try {
                    pro = CamcorderProfile.get(formats[i])
                    break
                } catch (e: java.lang.Exception) {
                }
            }


            val info = Camera.CameraInfo()
            Camera.getCameraInfo(cameraId, info)
            val captureFormat: CameraEnumerationAndroid.CaptureFormat
            try {
                val parameters = camera.parameters
                captureFormat = findClosestCaptureFormat(
                    parameters,
                    pro!!.videoFrameWidth,
                    pro.videoFrameHeight,
                    pro.videoFrameRate
                )
                val pictureSize = findClosestPictureSize(parameters, pro.videoFrameWidth, pro.videoFrameHeight)
                updateCameraParameters(camera, parameters, captureFormat, pictureSize)

                surfaceTextureHelper.setTextureSize(captureFormat.width, captureFormat.height)
            } catch (e: Exception) {
                camera.release()
                e.message?.let { callback.onFailure(it) }
                return
            }

            camera.setDisplayOrientation(0)
            surfaceTextureHelper.setFrameRotation(getFrameOrientation(applicationContext, info))
            callback.onDone(
                object : AbstractCameraSession(
                    events, applicationContext, surfaceTextureHelper,
                    cameraId, camera, pro
                ) {
                    override fun startCamera(
                        context: Context,
                        camera: Camera,
                        profile: CamcorderProfile
                    ) {
                        handler?.startCamera(context, camera, profile)
                    }

                    override fun stopCamera() {
                        handler?.stopCamera(camera)
                    }

                }
            )
        }

        private fun getRecordResolution(applicationContext: Context): Int {
            return LocalRecorder.RecordResolution
        }

        private fun getFrameOrientation(applicationContext: Context, info: Camera.CameraInfo): Int {
            val lockOrientation = LocalRecorder.LockOrientation
            var rotation: Int
            if (lockOrientation in 0..3) {
                rotation = lockOrientation * 90
            } else {
                rotation = getDeviceOrientation(applicationContext)
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    rotation = 360 - rotation
                }
                rotation += info.orientation
            }
            return rotation % 360
        }

        fun getDeviceOrientation(context: Context): Int {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return when (wm.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                Surface.ROTATION_0 -> 0
                else -> 0
            }
        }

        private fun updateCameraParameters(
            camera: Camera,
            parameters: Camera.Parameters,
            captureFormat: CameraEnumerationAndroid.CaptureFormat,
            pictureSize: Size
        ) {
            val focusModes = parameters.supportedFocusModes

            parameters.setPreviewFpsRange(captureFormat.framerate.min, captureFormat.framerate.max)
            parameters.setPreviewSize(captureFormat.width, captureFormat.height)
            parameters.setPictureSize(pictureSize.width, pictureSize.height)
            parameters.previewFormat = captureFormat.imageFormat

            if (parameters.isVideoStabilizationSupported) {
                parameters.videoStabilization = true
            }
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            }
            camera.parameters = parameters
        }

        private fun findClosestCaptureFormat(
            parameters: Camera.Parameters, width: Int, height: Int, frameRate: Int
        ): CameraEnumerationAndroid.CaptureFormat {
            val supportedFrameRates = convertFrameRates(parameters.supportedPreviewFpsRange)
            Logging.d(TAG, "Available fps ranges: $supportedFrameRates")

            val fpsRange = CameraEnumerationAndroid.getClosestSupportedFramerateRange(
                supportedFrameRates,
                frameRate
            )

            val previewSize = CameraEnumerationAndroid.getClosestSupportedSize(
                convertSizes(parameters.supportedPreviewSizes), width, height
            )

            return CameraEnumerationAndroid.CaptureFormat(
                previewSize.width,
                previewSize.height,
                fpsRange
            )
        }

        private fun convertFrameRates(arrayRanges: List<IntArray>): List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> {
            val ranges = ArrayList<CameraEnumerationAndroid.CaptureFormat.FramerateRange>()
            for (range in arrayRanges) {
                ranges.add(
                    CameraEnumerationAndroid.CaptureFormat.FramerateRange(
                        range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                        range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
                    )
                )
            }
            return ranges
        }

        private fun findClosestPictureSize(
            parameters: Camera.Parameters, width: Int, height: Int
        ): Size {
            return CameraEnumerationAndroid.getClosestSupportedSize(
                convertSizes(parameters.supportedPictureSizes), width, height
            )
        }

        private fun convertSizes(cameraSizes: List<Camera.Size>): List<Size> {
            val sizes = ArrayList<Size>()
            for (size in cameraSizes) {
                sizes.add(Size(size.width, size.height))
            }
            return sizes
        }
    }
}

internal class CameraHandler(
    private val applicationContext: Context,
    private val surfaceTextureHelper: SurfaceTextureHelper,
    private val cameraId: Int,
    private val sessionHandler: CameraSessionHandler? = null
) {
    private val callback = object : AbstractCameraSession.CreateSessionCallback {
        override fun onDone(sessionLocal: AbstractCameraSession) {
            synchronized(locker) {
                this@CameraHandler.sessionLocal = sessionLocal
            }
        }

        override fun onFailure(error: String) {
            setNext()
        }

    }
    private val events = object : AbstractCameraSession.Events {
        override fun onCameraOpening() {

        }

        override fun onCameraError(sessionLocal: AbstractCameraSession, error: String) {

        }

        override fun onCameraDisconnected(sessionLocal: AbstractCameraSession) {

        }

        override fun onCameraClosed(sessionLocal: AbstractCameraSession) {
            if (sessionLocal == this@CameraHandler.sessionLocal) setNext()
        }
    }
    private var sessionLocal: AbstractCameraSession? = null

    private val locker = Any()
    var isRunning = false
        private set
    private var recordVersion = 0

    private val handler = Handler()

    fun start() {
        synchronized(locker) {
            isRunning = true
            this.createSession()
        }
    }

    private fun createSession() {
        ++recordVersion
        this.stopRecordPrivate()
        AbstractCameraSession.create(
            callback,
            events,
            applicationContext,
            surfaceTextureHelper,
            cameraId,
            sessionHandler
        )
    }

    private fun setNext() {
        if (!isRunning) return
        ++recordVersion
        val currentVersion = recordVersion
        handler.postDelayed({
            synchronized(locker) {
                if (isRunning && currentVersion == recordVersion) {
                    createSession()
                }
            }
        }, 60000)
    }

    private fun stopRecordPrivate() {
        if (sessionLocal != null) {
            sessionLocal!!.stop()
            sessionLocal = null
        }
    }

    fun stop() {
        synchronized(locker) {
            isRunning = false
            stopRecordPrivate()
        }
    }

    fun preview(view: SurfaceViewRenderer?) {
        if (view != null) {
            surfaceTextureHelper.stopListening()
            surfaceTextureHelper.startListening(view)
        } else {
            surfaceTextureHelper.stopListening()
        }
    }
}