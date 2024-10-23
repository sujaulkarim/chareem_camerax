package com.chareem.camerax.basic.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.location.Location
import android.media.Image
import android.media.Image.Plane
import android.media.MediaActionSound
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture.Metadata
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.chareem.camerax.basic.CameraXActivity
import com.chareem.camerax.basic.KEY_EVENT_ACTION
import com.chareem.camerax.basic.KEY_EVENT_EXTRA
import com.chareem.camerax.basic.R
import com.chareem.camerax.basic.databinding.CameraUiContainerBinding
import com.chareem.camerax.basic.databinding.FragmentCameraBinding
import com.chareem.camerax.basic.facetracker.MultiBoxTracker
import com.chareem.camerax.basic.facetracker.SimilarityClassifier.Recognition
import com.chareem.camerax.basic.facetracker.env.ImageUtils
import com.chareem.camerax.basic.utils.*
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/** Helper type alias used for analysis use case callbacks */
typealias LumaListener = (luma: Double) -> Unit

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class CameraFragment : Fragment(),
    CameraSoundView.OnSoundTypeChangeListener,
    FlashSwitchView.FlashModeSwitchListener {

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private var cameraUiContainerBinding: CameraUiContainerBinding? = null

    private lateinit var outputDirectory: File
    private lateinit var broadcastManager: LocalBroadcastManager

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var tracker: MultiBoxTracker? = null
    private var faceDetector: FaceDetector? = null
    private var computingDetection = false
    private var dialogsMock: AlertDialog? = null
    private var addressText = ""
    private lateinit var contexts: Context
    private lateinit var activity: Activity

    private lateinit var act : CameraXActivity

    private val displayManager by lazy {
        contexts.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private var gps: SimpleLocation? = null
    private var locationCallback : LocationCallback? = null
    private var locationRequest : LocationRequest? = null
    private var fusedLocationClient : FusedLocationProviderClient? = null
    private var UPDATE_INTERVAL = 1000.toLong()
    private val FASTEST_INTERVAL: Long = 20000
    private var currLat = ""
    private var currLon = ""
    private var soundType: Int = CameraSoundView.SOUND_TYPE_ON
    private var flashType: Int = FlashSwitchView.FLASH_AUTO
    private var screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    private val THRESHOLD = 40
    private val PORTRAIT = 0
    private val LANDSCAPE = 270
    private val REVERSE_PORTRAIT = 180
    private val REVERSE_LANDSCAPE = 90

    /** Volume down button receiver used to trigger shutter */
    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                // When the volume down button is pressed, simulate a shutter button click
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    cameraUiContainerBinding?.cameraCaptureButton?.simulateClick()
                }
            }
        }
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        contexts = context
        activity = requireActivity()
        act = requireActivity() as CameraXActivity
        gps = SimpleLocation(contexts, false, false, UPDATE_INTERVAL, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)
        locationRequest = LocationRequest.create()
        locationRequest?.interval = UPDATE_INTERVAL
        locationRequest?.fastestInterval = FASTEST_INTERVAL
        locationRequest?.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest?.maxWaitTime = UPDATE_INTERVAL * 3
        /*requireActivity()
            .onBackPressedDispatcher
            .addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Log.d("plplpppkp", "Fragment back pressed invoked")
                    // Do custom work here

                    // if you want onBackPressed() to be called as normal afterwards
                    if (isEnabled) {
                        isEnabled = false
                        requireActivity().onBackPressed()
                    }
                }
            })*/
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*if (act.isCameraForceLandscape())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE*/
    }

    override fun onStart() {
        super.onStart()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(contexts)) {
            Navigation.findNavController(activity, R.id.fragment_container).navigate(R.id.permissions_fragment)
        } else {
            if (act.isUseTimeStamp()) {
                gps?.let {
                    if (gps?.hasLocationEnabled() == true) {
                        initLocation()
                    } else gps?.openSettings(activity)
                }
            }
        }
        setEnabledView(true)
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        gps?.endUpdates()
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }

        // Shut down our background executor
        cameraExecutor.shutdown()

        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onDestroy() {
        gps?.endUpdates()
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        currLat = act.getLatitude()
        currLon = act.getLongitude()
        return fragmentCameraBinding.root
    }

    private fun setGalleryThumbnail(uri: Uri) {
        // Run the operations in the view's thread
        cameraUiContainerBinding?.photoViewButton?.let { photoViewButton ->
            photoViewButton.post {
                // Remove thumbnail padding
                photoViewButton.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())

                // Load thumbnail into circular button using Glide
                val options: RequestOptions = RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .priority(Priority.HIGH)
                    .placeholder(R.drawable.ic_photo)
                    .error(R.drawable.ic_photo)
                    .circleCrop()
                Glide.with(photoViewButton)
                    .load(uri)
                    .apply(options)
                    .into(photoViewButton)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        // Set up the intent filter that will receive events from our main activity
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)

        // Determine the output directory
        outputDirectory = CameraXActivity.getOutputDirectory(contexts)

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {

            // Keep track of the display in which this view is attached
            displayId = fragmentCameraBinding.viewFinder.display.displayId

            // Build UI controls
            updateCameraUi()

            // Set up the camera and its use cases
            setUpCamera()
        }

        initBoxFace()
        val orientationEventListener: OrientationEventListener =
            object : OrientationEventListener(contexts) {
                override fun onOrientationChanged(orientation: Int) {
                    if(orientation >= 360 + PORTRAIT - THRESHOLD && orientation < 360 ||
                        orientation >= 0 && orientation <= PORTRAIT + THRESHOLD)
                        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else if(orientation >= LANDSCAPE - THRESHOLD && orientation <= LANDSCAPE + THRESHOLD)
                        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else if(orientation >= REVERSE_PORTRAIT - THRESHOLD && orientation <= REVERSE_PORTRAIT + THRESHOLD)
                        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else if(orientation >= REVERSE_LANDSCAPE - THRESHOLD && orientation <= REVERSE_LANDSCAPE + THRESHOLD)
                        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }
        orientationEventListener.enable()
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Rebind the camera with the updated display metrics
        bindCameraUseCases()

        // Enable or disable switching between cameras
        updateCameraSwitchButton()
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(contexts)
        cameraProviderFuture.addListener(Runnable {

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = if (act.getCameraType() == CameraHelper.FACING_FRONT){
                when {
                    hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                    hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                    else -> throw IllegalStateException("Back and front camera are unavailable")
                }
            } else {
                CameraSelector.LENS_FACING_BACK
            }

            // Enable or disable switching between cameras
            updateCameraSwitchButton()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(contexts))
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {
        if (act.isCameraForceLandscape())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = resources.displayMetrics
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = fragmentCameraBinding.viewFinder.display.rotation

        // CameraProvider
        /*val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")*/

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // We request aspect ratio but no resolution to match preview config, but letting
            // CameraX optimize for whatever specific resolution best fits our use cases
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setFlashMode(if (flashType == FlashSwitchView.FLASH_ON) ImageCapture.FLASH_MODE_ON
            else if (flashType == FlashSwitchView.FLASH_OFF) ImageCapture.FLASH_MODE_OFF
            else ImageCapture.FLASH_MODE_AUTO)
            .setTargetRotation(rotation)
            .build()

        // ImageAnalysis
        imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(cameraExecutor, LuminosityAnalyzer{
                    // Values returned from our analyzer are passed to the attached listener
                    // We log image analysis results here - you should do something useful
                    // instead!
                    Log.d(TAG, "Average luminosity: $it")
                })
            }

        // Must unbind the use-cases before rebinding them
        cameraProvider?.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer, imageCapture)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
            observeCameraState(camera?.cameraInfo!!)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun observeCameraState(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
            run {
                when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN -> {
                        // Ask the user to close other camera apps
                        /*Toast.makeText(context,
                                "CameraState: Pending Open",
                                Toast.LENGTH_SHORT).show()*/
                    }
                    CameraState.Type.OPENING -> {
                        // Show the Camera UI
                        /*Toast.makeText(context,
                                "CameraState: Opening",
                                Toast.LENGTH_SHORT).show()*/
                    }
                    CameraState.Type.OPEN -> {
                        // Setup Camera resources and begin processing
                        /*Toast.makeText(context,
                                "CameraState: Open",
                                Toast.LENGTH_SHORT).show()*/
                    }
                    CameraState.Type.CLOSING -> {
                        // Close camera UI
                        /*Toast.makeText(context,
                                "CameraState: Closing",
                                Toast.LENGTH_SHORT).show()*/
                    }
                    CameraState.Type.CLOSED -> {
                        // Free camera resources
                        /*Toast.makeText(context,
                                "CameraState: Closed",
                                Toast.LENGTH_SHORT).show()*/
                    }
                }
            }

            cameraState.error?.let { error ->
                when (error.code) {
                    // Open errors
                    CameraState.ERROR_STREAM_CONFIG -> {
                        // Make sure to setup the use cases properly
                        /*Toast.makeText(context,
                            "Stream config error",
                            Toast.LENGTH_SHORT).show()*/
                    }
                    // Opening errors
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        /*Toast.makeText(context,
                            "Camera in use",
                            Toast.LENGTH_SHORT).show()*/
                    }
                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                       /* Toast.makeText(context,
                            "Max cameras in use",
                            Toast.LENGTH_SHORT).show()*/
                    }
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        /*Toast.makeText(context,
                            "Other recoverable error",
                            Toast.LENGTH_SHORT).show()*/
                    }
                    // Closing errors
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        // Ask the user to enable the device's cameras
                        /*Toast.makeText(context,
                            "Camera disabled",
                            Toast.LENGTH_SHORT).show()*/
                    }
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        // Ask the user to reboot the device to restore camera function
                        /*Toast.makeText(context,
                            "Fatal error",
                            Toast.LENGTH_SHORT).show()*/
                    }
                    // Closed errors
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        /*Toast.makeText(context,
                            "Do not disturb mode enabled",
                            Toast.LENGTH_SHORT).show()*/
                    }
                }
            }
        }
    }

    /**
     *  [androidx.camera.core.ImageAnalysis.Builder] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {

        // Remove previous UI if any
        cameraUiContainerBinding?.root?.let {
            fragmentCameraBinding.root.removeView(it)
        }

        cameraUiContainerBinding = CameraUiContainerBinding.inflate(
            LayoutInflater.from(contexts),
            fragmentCameraBinding.root,
            true
        )

        if (act.isUseTimeStamp()) {
            cameraUiContainerBinding?.locationTv?.isVisible = true
        }

        cameraUiContainerBinding?.flashBt?.setFlashSwitchListener(this)
        flashType = Utils.getFlashType(contexts)
        cameraUiContainerBinding?.flashBt?.setFlashMode(flashType)
        cameraUiContainerBinding?.soundBt?.setOnSoundTypeChangeListener(this)
        soundType = Utils.getSoundType(contexts)
        cameraUiContainerBinding?.soundBt?.setSoundType(soundType)

        cameraUiContainerBinding?.trackingOverlay?.addCallback { canvas -> tracker?.draw(canvas) }

        // In the background, load latest photo taken (if any) for gallery thumbnail
        lifecycleScope.launch(Dispatchers.IO) {
            outputDirectory.listFiles { file ->
                EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
            }?.maxOrNull()?.let {
                setGalleryThumbnail(Uri.fromFile(it))
            }
        }

        // Listener for button used to capture photo
        cameraUiContainerBinding?.cameraCaptureButton?.setOnClickListener {

            setEnabledView(false)
            playSound(soundType)
            // Get a stable reference of the modifiable image capture use case
            imageCapture?.let { imageCapture ->

                // Create output file to hold the image
                val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

                // Setup image capture metadata
                val metadata = Metadata().apply {

                    // Mirror image when using the front camera
                    isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                }

                // Create output options object which contains file + metadata
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                    .setMetadata(metadata)
                    .build()

                // Setup image capture listener which is triggered after photo has been taken
                imageCapture.takePicture(
                    outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                            setEnabledView(true)
                        }

                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                            Log.d(TAG, "Photo capture succeeded: $savedUri")

                            // We can only change the foreground Drawable using API level 23+ API
                           /* if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                // Update the gallery thumbnail with latest picture taken
                                setGalleryThumbnail(savedUri)
                            }*/

                            // Implicit broadcasts will be ignored for devices running API level >= 24
                            // so if you only target API level 24+ you can remove this statement
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                                contexts.sendBroadcast(
                                    Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
                                )
                            }

                            // If the folder selected is an external media directory, this is
                            // unnecessary but otherwise other apps will not be able to access our
                            // images unless we scan them using [MediaScannerConnection]
                            val mimeType = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(savedUri.toFile().extension)
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(savedUri.toFile().absolutePath),
                                arrayOf(mimeType)
                            ) { _, uri ->
                                Log.d(TAG, "Image capture scanned into media store: $uri")
                            }

                            val handler = Handler(Looper.getMainLooper())
                            handler.post {
                                val options = RequestOptions()
                                    .transform()
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    .skipMemoryCache(true)
                                    .priority(Priority.HIGH)
                                    .placeholder(R.drawable.ic_photo)
                                    .error(R.drawable.ic_photo)
                                Glide.with(contexts)
                                    .asBitmap()
                                    .load(savedUri)
                                    .apply(options)
                                    .into(object : CustomTarget<Bitmap?>() {
                                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                                           startAndValidatePreview(savedUri, savedUri.toFile().absolutePath, resource)
                                        }
                                        override fun onLoadCleared(placeholder: Drawable?) {}
                                    })
                            }
                        }
                    })

                // We can only change the foreground Drawable using API level 23+ API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    // Display flash animation to indicate that photo was captured
                    fragmentCameraBinding.root.postDelayed({
                        fragmentCameraBinding.root.foreground = ColorDrawable(Color.WHITE)
                        fragmentCameraBinding.root.postDelayed(
                            { fragmentCameraBinding.root.foreground = null }, ANIMATION_FAST_MILLIS)
                    }, ANIMATION_SLOW_MILLIS)
                }

                /*imageCapture.takePicture(cameraExecutor, object: ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // Use the image, then make sure to close it.
                        image.close()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        val errorType = exception.getImageCaptureError()

                    }
                })*/
            } ?: setEnabledView(true)
        }

        // Setup for button used to switch cameras
        cameraUiContainerBinding?.cameraSwitchButton?.let {

            // Disable the button until the camera is set up
            it.isEnabled = false

            // Listener for button used to switch cameras. Only called if the button is enabled
            it.setOnClickListener {
                lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                // Re-bind use cases to update selected camera
                bindCameraUseCases()
            }
        }

        // Listener for button used to view the most recent photo
        cameraUiContainerBinding?.photoViewButton?.setOnClickListener {
            // Only navigate when the gallery has photos
            if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                val bundle = Bundle()
                bundle.putString(GalleryFragment.root_directory, outputDirectory.absolutePath)
                bundle.putString(GalleryFragment.img_directory, "")
                Navigation.findNavController(
                    activity, R.id.fragment_container
                ).navigate(R.id.gallery_fragment, bundle)
            }
        }
        setLocation()
    }

    private fun setEnabledView(isEnabled: Boolean){
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            cameraUiContainerBinding?.cameraCaptureButton?.isEnabled = isEnabled
            cameraUiContainerBinding?.cameraSwitchButton?.isEnabled = isEnabled
            cameraUiContainerBinding?.photoViewButton?.isEnabled = isEnabled
        }
    }

    /** Helper function used to create a timestamped file */
    private fun createFile(baseFolder: File, format: String, extension: String) = if (act.getImageName().isNotEmpty()){
        val file = File(baseFolder, act.getImageName() + extension)
        if (file.exists()){
            file.delete()
        }
        file
    } else {
        val file = File(baseFolder, SimpleDateFormat(format, Locale.US)
            .format(System.currentTimeMillis()) + extension)
        if (file.exists()){
            file.delete()
        }
        file
    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        try {
            cameraUiContainerBinding?.cameraSwitchButton?.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            cameraUiContainerBinding?.cameraSwitchButton?.isEnabled = false
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    /**
     * Our custom image analysis class.
     *
     * <p>All we need to do is override the function `analyze` with our desired operations. Here,
     * we compute the average luminosity of the image by looking at the Y plane of the YUV frame.
     */
    inner class LuminosityAnalyzer(listener: LumaListener? = null) : ImageAnalysis.Analyzer {
        private var changeCount = 0
        private val frameRateWindow = 8
        private val frameTimestamps = ArrayDeque<Long>(5)
        private val listeners = ArrayList<LumaListener>().apply { listener?.let { add(it) } }
        private var lastAnalyzedTimestamp = 0L
        var framesPerSecond: Double = -1.0
            private set

        /**
         * Used to add listeners that will be called with each luma computed
         */
        fun onFrameAnalyzed(listener: LumaListener) = listeners.add(listener)

        /**
         * Helper extension function used to extract a byte array from an image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        private fun Image.toBitmap(): Bitmap {
            val planes: Array<Plane> = getPlanes()
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            //U and V are swapped
            //U and V are swapped
            yBuffer[nv21, 0, ySize]
            vBuffer[nv21, ySize, vSize]
            uBuffer[nv21, ySize + vSize, uSize]

            val yuvImage =
                YuvImage(nv21, ImageFormat.NV21, getWidth(), getHeight(), null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)

            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

        /**
         * Analyzes an image to produce a result.
         *
         * <p>The caller is responsible for ensuring this analysis method can be executed quickly
         * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
         * images will not be acquired and analyzed.
         *
         * <p>The image passed to this method becomes invalid after this method returns. The caller
         * should not store external references to this image, as these references will become
         * invalid.
         *
         * @param image image being analyzed VERY IMPORTANT: Analyzer method implementation must
         * call image.close() on received images when finished using them. Otherwise, new images
         * may not be received or the camera may stall, depending on back pressure setting.
         *
         */
        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(image: ImageProxy) {
            if (changeCount > 1200) changeCount = 0
            changeCount++
            // If there are no listeners attached, we don't need to perform analysis
            if (listeners.isEmpty()) {
                image.close()
                return
            }

            // Keep track of frames analyzed
            val currentTime = System.currentTimeMillis()
            frameTimestamps.push(currentTime)

            // Compute the FPS using a moving average
            while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
            val timestampFirst = frameTimestamps.peekFirst() ?: currentTime
            val timestampLast = frameTimestamps.peekLast() ?: currentTime
            framesPerSecond = 1.0 / ((timestampFirst - timestampLast) /
                    frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0

            // Analysis could take an arbitrarily long amount of time
            // Since we are running in a different thread, it won't stall other use cases

            lastAnalyzedTimestamp = frameTimestamps.first

            // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance plane
            val buffer = image.planes[0].buffer

            // Extract image data from callback object
            val data = buffer.toByteArray()


            // Convert the data into an array of pixel values ranging 0-255
            val pixels = data.map { it.toInt() and 0xFF }

            // Compute average luminance for the image
            val luma = pixels.average()

            // Call all listeners with new value
            listeners.forEach { it(luma) }

            if (changeCount % 4 == 0 && act.isUseFaceDetection()) {
                //val img: Image? = image.image
                //val bitmap = img?.toBitmap()
                val bitmap = image.toBitmap()
                updateFace(bitmap, image)
            } else image.close()
        }
    }

    companion object {

        private const val TAG = "CameraXBasic"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    private fun initBoxFace() {
        tracker = MultiBoxTracker(contexts)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        faceDetector = FaceDetection.getClient(options)
    }

    private var previewWidth = 0
    private var previewHeight = 0
    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var portraitBmp: Bitmap? = null
    private var faceBmp: Bitmap? = null
    private val TF_OD_API_INPUT_SIZE = 112
    private var sensorOrientation: Int? = null
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null
    private val MAINTAIN_ASPECT = false

    @SuppressLint("UnsafeOptInUsageError")
    fun updateFace(bitmap: Bitmap?, imageProxy: ImageProxy) {
        //sensorOrientation = imageCapture?.targetRotation
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        sensorOrientation = rotationDegrees
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        cameraUiContainerBinding?.trackingOverlay?.postInvalidate()
        previewWidth = bitmap.width
        previewHeight = bitmap.height
        rgbFrameBitmap = bitmap
        sensorOrientation?.let { tracker?.setFrameConfiguration(previewWidth, previewHeight, it) }
        val targetW: Int
        val targetH: Int
        if (sensorOrientation == 90 || sensorOrientation == 270) {
            targetH = previewWidth
            targetW = previewHeight
        } else {
            targetW = previewWidth
            targetH = previewHeight
        }
        val cropW = (targetW / 1.0f).toInt()
        val cropH = (targetH / 1.2f).toInt()
        croppedBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        portraitBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        faceBmp = Bitmap.createBitmap(
            TF_OD_API_INPUT_SIZE,
            TF_OD_API_INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )
        frameToCropTransform = sensorOrientation?.let {
            ImageUtils.getTransformationMatrix(
                previewWidth,
                previewHeight,
                cropW,
                cropH,
                it,
                MAINTAIN_ASPECT
            )
        }
        cropToFrameTransform = Matrix()
        frameToCropTransform?.invert(cropToFrameTransform)
        if (computingDetection) {
            imageProxy.close()
            return
        }
        computingDetection = true

        croppedBitmap?.let {
            val canvas = Canvas(it)
            canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)
        }
        val mediaImage = imageProxy.image
        mediaImage?.let {
            try {
                //val image = InputImage.fromBitmap(rgbFrameBitmap!!, 0)
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                faceDetector?.process(image)
                    ?.addOnSuccessListener(OnSuccessListener { faces ->
                        if (faces.size == 0) {
                            updateResults(100, arrayListOf())
                        } else {
                            onFacesDetected(100, faces)
                        }
                        rgbFrameBitmap?.recycle()
                        bitmap.recycle()
                        croppedBitmap?.recycle()
                        portraitBmp?.recycle()
                        faceBmp?.recycle()
                        imageProxy.close()
                    })?.addOnFailureListener { e ->
                        rgbFrameBitmap?.recycle()
                        bitmap.recycle()
                        croppedBitmap?.recycle()
                        portraitBmp?.recycle()
                        faceBmp?.recycle()
                        imageProxy.close()
                        // Task failed with an exception
                        e.printStackTrace()
                    }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                rgbFrameBitmap?.recycle()
                bitmap.recycle()
                croppedBitmap?.recycle()
                portraitBmp?.recycle()
                faceBmp?.recycle()
                imageProxy.close()
            }
        } ?: imageProxy.close()
        //Trace.endSection()
    }

    private fun onFacesDetected(currTimestamp: Long, faces: List<Face>) {
        val paint = Paint()
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0f
        val mappedRecognitions: MutableList<Recognition> = LinkedList<Recognition>()

        // Note this can be done only once
        val sourceW = rgbFrameBitmap!!.width
        val sourceH = rgbFrameBitmap!!.height
        val targetW = portraitBmp!!.width
        val targetH = portraitBmp!!.height
        val transform = createTransform(
            sourceW,
            sourceH,
            targetW,
            targetH,
            sensorOrientation!!
        )
        val cv = Canvas(portraitBmp!!)

        // draws the original image in portrait mode.
        cv.drawBitmap(rgbFrameBitmap!!, transform, null)
        val cvFace = Canvas(faceBmp!!)
        for (face in faces) {
            //results = detector.recognizeImage(croppedBitmap);
            val boundingBox = RectF(face.boundingBox)

            //final boolean goodConfidence = result.getConfidence() >= minimumConfidence;
            // maps crop coordinates to original
            cropToFrameTransform!!.mapRect(boundingBox)

            // maps original coordinates to portrait coordinates
            val faceBB = RectF(boundingBox)
            transform.mapRect(faceBB)

            // translates portrait to origin and scales to fit input inference size
            //cv.drawRect(faceBB, paint);
            val sx =
                TF_OD_API_INPUT_SIZE.toFloat() / faceBB.width()
            val sy =
                TF_OD_API_INPUT_SIZE.toFloat() / faceBB.height()
            val matrix = Matrix()
            matrix.postTranslate(-faceBB.left, -faceBB.top)
            matrix.postScale(sx, sy)
            cvFace.drawBitmap(portraitBmp!!, matrix, null)

            //canvas.drawRect(faceBB, paint);
            val label = ""
            val confidence = -0f
            val color = Color.YELLOW
            val extra: Any? = null

            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                // camera is frontal so the image is flipped horizontally
                // flips horizontally
                val flip = Matrix();
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    flip.postScale(1f, -1f, previewWidth / 2.0f, previewHeight / 2.0f);
                }
                else {
                    flip.postScale(-1f, 1f, previewWidth / 2.0f, previewHeight / 2.0f);
                }
                //flip.postScale(1, -1, targetW / 2.0f, targetH / 2.0f);
                flip.mapRect(boundingBox);

            }
            val result = Recognition(
                "0", label, confidence, boundingBox
            )
            result.setColor(color)
            result.setLocation(boundingBox)
            result.setExtra(extra)
            mappedRecognitions.add(result)
        }
        updateResults(currTimestamp, mappedRecognitions)
    }

    private fun createTransform(
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        applyRotation: Int
    ): Matrix {
        val matrix = Matrix()
        if (applyRotation != 0) {

            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 1.0f, -srcHeight / 1.0f)

            // Rotate around origin.
            matrix.postRotate(applyRotation.toFloat())
        }

//        // Account for the already applied rotation, if any, and then determine how
//        // much scaling is needed for each axis.
//        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;
//        final int inWidth = transpose ? srcHeight : srcWidth;
//        final int inHeight = transpose ? srcWidth : srcHeight;
        if (applyRotation != 0) {

            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 1.0f, dstHeight / 1.0f)
        }
        return matrix
    }

    private fun showToast(message: String) {
        Toast.makeText(contexts, message, Toast.LENGTH_LONG).show()
    }

    private fun updateResults(currTimestamp: Long, mappedRecognitions: List<Recognition>) {
        //adding = false;
        activity.runOnUiThread {
            computingDetection = false
            tracker?.trackResults(mappedRecognitions, currTimestamp)
            cameraUiContainerBinding?.trackingOverlay?.postInvalidate()
        }
    }

    private fun initLocation() {
        gps?.beginUpdates()
        gps?.setListener(object : SimpleLocation.Listener {
            override fun onPositionChanged(location: Location?) {
                location?.let {
                    if (act.isUseMockDetection()) {
                        if (Utils.isMockLocationOn(it)) {
                            showDialogMock(activity)
                            return
                        }
                    }
                    currLat = it.latitude.toString() + ""
                    currLon = it.longitude.toString() + ""
                    setLocation()
                }
            }
        })

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                location?.let {
                    if (act.isUseMockDetection()) {
                        if (Utils.isMockLocationOn(it)) {
                            showDialogMock(activity)
                            return
                        }
                    }
                    currLat = it.latitude.toString() + ""
                    currLon = it.longitude.toString() + ""
                    setLocation()
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(
                contexts,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                contexts,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient?.requestLocationUpdates(
            locationRequest!!,
            locationCallback!!,
            Looper.getMainLooper())
    }

    private fun setLocation() {
        if (currLat == "" || currLat == "0" || currLon == "" || currLon == "0") {
            return
        }
        Utils.getAddress(contexts, currLat.toDouble(), currLon.toDouble()){address ->
            if (address != null) {
                var text = ""
                text += if (address.thoroughfare == null) "" else address.thoroughfare
                text += "\n"
                text += if (address.subLocality == null) "" else address.subLocality
                text += " , "
                text += if (address.locality == null) "" else address.locality
                text += "\n"
                text += if (address.postalCode == null) "" else address.postalCode
                text += " , "
                text += if (address.subAdminArea == null) "" else address.subAdminArea
                text += "\n"
                text += if (address.adminArea == null) "" else address.adminArea
                text += " , "
                text += if (address.countryName == null) "" else address.countryName
                text += "\n"
                text += "Lat : $currLat"
                text += "\n"
                text += "Lon : $currLon"
                text += "\n"
                text += Utils.getCurrentTimeStr("dd MMM yyyy HH:mm:ss")
                this.addressText = text
                cameraUiContainerBinding?.locationTv?.text = text
            }
        }
    }

    private fun showDialogMock(activity: Activity) {
        dialogsMock?.dismiss()
        val alertDialogMock = AlertDialog.Builder(activity)
        // Setting Dialog Title
        alertDialogMock.setTitle("Mock Location is ON")

        // Setting Dialog Message
        alertDialogMock.setMessage("Application fake GPS is on, turn off it first")
        dialogsMock = alertDialogMock.create()
        dialogsMock?.setButton(
            DialogInterface.BUTTON_POSITIVE, "Close"
        ) { dialog, which -> activity.finish() }
        /*dialogs.setButton(
            DialogInterface.BUTTON_NEGATIVE, "Cancel"
        ) { dialog: DialogInterface?, which: Int -> context.finish() }*/
        dialogsMock?.setCancelable(false)
        dialogsMock?.show()
    }

    private fun startAndValidatePreview(savedUri: Uri, selectedImage: String, bmp: Bitmap){
        //var bitmap: Bitmap = Utils.rotateImageIfRequired(bmp, selectedImage, contexts)
        if (act.isCameraForceLandscape() && screenOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE){
            showToast("Please set your phone to landscape position first")
            setEnabledView(true)
            return
        }
        if (act.isUseTimeStamp() && addressText.isEmpty()){
            showToast("Locaton not found")
            setEnabledView(true)
            if (gps?.hasLocationEnabled() == true) {
                gps?.beginUpdates()
            } else gps?.openSettings(activity)
            return
        }
        var bitmap: Bitmap = bmp
        val bmps = Utils.resize(bitmap, 1280, 1280)
        if (bmps != null) bitmap = bmps
        if (act.isUseFaceDetection()) {
            val image = InputImage.fromBitmap(bitmap, 0)
            faceDetector?.process(image)
                ?.addOnSuccessListener(OnSuccessListener { faces ->
                    //bitmap = Utils.rotateImageIfRequired(bitmap, selectedImage, contexts)
                    if (act.isUseTimeStamp() && addressText.isNotEmpty()){
                        bitmap = Utils.drawMultilineTextToBitmap(contexts, bitmap, addressText, 10);
                    }
                    Utils.saveBitmap(selectedImage, bitmap){it, message ->
                        if (!it) {
                            showToast(message)
                            setEnabledView(true)
                            return@saveBitmap
                        }
                    }
                    if (faces.size == 0) {
                        setEnabledView(true)
                        showToast("No face detected, please point the camera in to a face")
                        return@OnSuccessListener
                    }
                    setGalleryThumbnail(savedUri)
                    if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                        val bundle = Bundle()
                        bundle.putString(GalleryFragment.root_directory, outputDirectory.absolutePath)
                        bundle.putString(GalleryFragment.img_directory, selectedImage)
                        Navigation.findNavController(
                            activity, R.id.fragment_container
                        ).navigate(R.id.gallery_fragment, bundle)
                    }
                    setEnabledView(true)
                })?.addOnFailureListener { e -> // Task failed with an exception
                    if (act.isUseTimeStamp() && addressText.isNotEmpty()){
                        bitmap = Utils.drawMultilineTextToBitmap(contexts, bitmap, addressText, 10);
                    }
                    Utils.saveBitmap(selectedImage, bitmap){it, message ->
                        if (!it) {
                            showToast(message)
                            setEnabledView(true)
                            return@saveBitmap
                        }
                    }
                    setEnabledView(true)
                    showToast("No face detected, please point the camera in to a face")
                    e.printStackTrace()
                }
        } else {
            setGalleryThumbnail(savedUri)
            if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                //bitmap = Utils.rotateImageIfRequired(bitmap, selectedImage, contexts)
                if (act.isUseTimeStamp() && addressText.isNotEmpty()){
                    bitmap = Utils.drawMultilineTextToBitmap(contexts, bitmap, addressText, 10);
                }
                Utils.saveBitmap(selectedImage, bitmap){it, message ->
                    if (!it) {
                        setEnabledView(true)
                        showToast(message)
                        return@saveBitmap
                    }
                }
                val bundle = Bundle()
                bundle.putString(GalleryFragment.root_directory, outputDirectory.absolutePath)
                bundle.putString(GalleryFragment.img_directory, selectedImage)
                Navigation.findNavController(
                    activity, R.id.fragment_container
                ).navigate(R.id.gallery_fragment, bundle)
                setEnabledView(true)
            } else setEnabledView(true)
        }
    }

    override fun onSoundTypeChanged(soundType: Int){
        this.soundType = soundType
        Utils.saveSoundType(contexts, soundType)
    }

    override fun onFlashModeChanged(mode: Int) {
        this.flashType = mode
        Utils.saveFlashType(contexts, mode)
        imageCapture?.flashMode = mode
        try {
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer, imageCapture)
            observeCameraState(camera?.cameraInfo!!)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun playSound(soundType: Int){
        if (soundType == CameraSoundView.SOUND_TYPE_ON){
            val sound = MediaActionSound()
            sound.play(MediaActionSound.SHUTTER_CLICK)
        }
    }
}
