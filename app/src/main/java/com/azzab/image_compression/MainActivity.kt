package com.azzab.image_compression

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var previewView: PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppContent() }
        requestCameraPermission()
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> startCamera()

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // TODO: Show rationale UI
            }

            else -> {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) startCamera()
                }.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).apply {
            addListener({
                val provider = get()
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                imageCapture = ImageCapture.Builder().build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    this@MainActivity,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            }, ContextCompat.getMainExecutor(this@MainActivity))
        }
    }

    @Composable
    fun AppContent() {
        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "camera") {
                    composable("camera") {
                        CameraResizeScreen { original, resized ->
                            navController.currentBackStackEntry?.savedStateHandle?.apply {
                                println("Image cxxxxx : ${original.size}")
                                set("original", original)
                                set("resized", resized)
                            }


                            navController.navigate("result")
                        }
                    }
                    composable("result") {
                        val handle = navController.previousBackStackEntry?.savedStateHandle
                        val originalBytes = handle?.get<ByteArray>("original") ?: byteArrayOf()
                        val resizedBytes = handle?.get<ByteArray>("resized") ?: byteArrayOf()

                        println("Image vvvvvvvvvvvvvvvvvvv : ${originalBytes.size}")
                        ResultScreen(originalBytes, resizedBytes)
                    }
                }
            }
        }
    }

    @Composable
    fun CameraResizeScreen(
        onImageProcessed: (ByteArray, ByteArray) -> Unit
    ) {
        val context = LocalContext.current
        var isProcessing by remember { mutableStateOf(false) }
        var originalBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        var resizedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        val scope = rememberCoroutineScope()

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { PreviewView(it).also { pv -> previewView = pv } },
                modifier = Modifier.matchParentSize()
            )

            if (isProcessing) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                originalBitmap?.let { bmp ->
                    Text("Original Image", style = MaterialTheme.typography.titleMedium)
                    Image(
                        bmp.asImageBitmap(), "Original", Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                resizedBitmap?.let { bmp ->
                    Text("Resized Image", style = MaterialTheme.typography.titleMedium)
                    Image(
                        bmp.asImageBitmap(), "Resized", Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        imageCapture?.let { capture ->
                            val tempFile = File(cacheDir, "temp.jpg")
                            val options = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                            capture.takePicture(
                                options, ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onError(exc: ImageCaptureException) {
                                        // handle error
                                    }

                                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                        scope.launch(Dispatchers.Main) {
                                            isProcessing = true
                                            val bytes =
                                                withContext(Dispatchers.IO) { tempFile.readBytes() }
                                            val outBytes = withContext(Dispatchers.IO) {
                                                ImageResizer.resizeGeneric(
                                                    bytes, 413, 531,
                                                    getExifOrientation(tempFile),
                                                    3, 1, 1, 4
                                                ) ?: return@withContext byteArrayOf()
                                            }
                                            originalBitmap =
                                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            resizedBitmap = BitmapFactory.decodeByteArray(
                                                outBytes,
                                                0,
                                                outBytes.size
                                            )
                                            isProcessing = false
                                            onImageProcessed(bytes, outBytes)
                                            tempFile.delete()
                                        }
                                    }
                                }
                            )
                        }
                    },
                    Modifier.fillMaxWidth()
                ) {
                    Text("Capture & Resize")
                }
            }
        }
    }


    @Composable
    fun ResultScreen(originalBytes: ByteArray, resizedBytes: ByteArray) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val originalBmp by remember(originalBytes) {
            mutableStateOf(
                BitmapFactory.decodeByteArray(
                    originalBytes,
                    0,
                    originalBytes.size
                )
            )
        }
        val resizedBmp by remember(resizedBytes) {
            mutableStateOf(
                BitmapFactory.decodeByteArray(
                    resizedBytes,
                    0,
                    resizedBytes.size
                )
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (originalBmp != null && resizedBmp != null) {
                Text("Original Image", style = MaterialTheme.typography.titleMedium)
                Image(
                    originalBmp.asImageBitmap(),
                    contentDescription = "Original",
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("Resized Image", style = MaterialTheme.typography.titleMedium)
                Image(
                    resizedBmp.asImageBitmap(),
                    contentDescription = "Resized",
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        saveBitmapToGallery(

                            originalBytes.toBitmap()!!,
                            "IMG_original_${System.currentTimeMillis()}.jpg"
                        )
                        saveBitmapToGallery(

                            resizedBytes.toBitmap()!!,
                            "IMG_resized_${System.currentTimeMillis()}.jpg"
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Images saved to gallery", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }, Modifier.fillMaxWidth()) {
                    Text("Save to Gallery")
                }
            } else {
                Text("Failed to load images", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    fun ByteArray.toBitmap(): Bitmap? =
        BitmapFactory.decodeByteArray(this, 0, this.size)

    suspend fun saveBitmapToGallery(bitmap: Bitmap, displayName: String) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "${displayName}_${System.currentTimeMillis()}.jpg"
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = contentResolver
        val uri = resolver.insert(collection, values)
        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, values, null, null)
            }
        }
    }


//    @Composable
//    fun ResultScreen(originalBytes: ByteArray, resizedBytes: ByteArray) {
//        // Decode bitmaps once and handle nulls
//        val originalBmp by remember(originalBytes) {
//            mutableStateOf(BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size))
//        }
//        val resizedBmp by remember(resizedBytes) {
//            mutableStateOf(BitmapFactory.decodeByteArray(resizedBytes, 0, resizedBytes.size))
//        }
//
//        Column(
//            Modifier
//                .fillMaxSize()
//                .verticalScroll(rememberScrollState())
//                .padding(16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            if (originalBmp != null && resizedBmp != null) {
//                Text("Original Image", style = MaterialTheme.typography.titleMedium)
//                Image(
//                    bitmap = originalBmp.asImageBitmap(),
//                    contentDescription = "Original",
//                    modifier = Modifier.fillMaxWidth().height(300.dp)
//                )
//                Spacer(Modifier.height(16.dp))
//                Text("Resized Image", style = MaterialTheme.typography.titleMedium)
//                Image(
//                    bitmap = resizedBmp.asImageBitmap(),
//                    contentDescription = "Resized",
//                    modifier = Modifier.fillMaxWidth().height(300.dp)
//                )
//            } else {
//                Text("Failed to load images", style = MaterialTheme.typography.bodyMedium)
//            }
//        }
//    }

//    @Composable
//    fun ResultScreen(originalBytes: ByteArray, resizedBytes: ByteArray) {
//        Column(
//            Modifier
//                .fillMaxSize()
//                .verticalScroll(rememberScrollState())
//                .padding(16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Text("Original Image", style = MaterialTheme.typography.titleMedium)
//            Image(
//                bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size).asImageBitmap(),
//                contentDescription = "Original",
//                modifier = Modifier.fillMaxWidth().height(300.dp)
//            )
//            Spacer(Modifier.height(16.dp))
//            Text("Resized Image", style = MaterialTheme.typography.titleMedium)
//            Image(
//                bitmap = BitmapFactory.decodeByteArray(resizedBytes, 0, resizedBytes.size).asImageBitmap(),
//                contentDescription = "Resized",
//                modifier = Modifier.fillMaxWidth().height(300.dp)
//            )
//        }
//    }

    private fun getExifOrientation(file: File): Int {
        val exif = ExifInterface(file.absolutePath)
        return when (exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 6
            ExifInterface.ORIENTATION_ROTATE_180 -> 3
            ExifInterface.ORIENTATION_ROTATE_270 -> 8
            else -> 1
        }
    }
}


//package com.azzab.image_compression
//
//
//import android.Manifest
//import android.content.ContentValues
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.media.ExifInterface
//import android.os.Build
//import android.os.Bundle
//import android.provider.MediaStore
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.camera.core.CameraSelector
//import androidx.camera.core.ImageCapture
//import androidx.camera.core.ImageCaptureException
//import androidx.camera.core.Preview
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.rememberScrollState
//import androidx.compose.foundation.verticalScroll
//import androidx.compose.material3.Button
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.core.content.ContextCompat
//import androidx.navigation.NavController
//import androidx.navigation.NavHostController
//import androidx.navigation.NavType
//import androidx.navigation.compose.NavHost
//import androidx.navigation.compose.composable
//import androidx.navigation.compose.rememberNavController
//import androidx.navigation.navArgument
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import java.io.File
//import java.util.Base64
//import java.util.concurrent.Executors
//
//
//
//class MainActivity : ComponentActivity() {
//    private var previewView: PreviewView? = null
//    private val cameraExecutor = Executors.newSingleThreadExecutor()
//    private var imageCapture: ImageCapture? = null
//
//    private val requestPermission =
//        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
//            if (granted) startCamera()
//        }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            MaterialTheme {
//                Surface(modifier = Modifier.fillMaxSize()) {
//                    AppNavHost()
//                }
//            }
//        }
//        if (ContextCompat.checkSelfPermission(
//                this, Manifest.permission.CAMERA
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            requestPermission.launch(Manifest.permission.CAMERA)
//        } else startCamera()
//    }
//
//    @Composable
//    fun AppNavHost() {
//        val navController = rememberNavController()
//        NavHost(navController, startDestination = "camera") {
//            composable("camera") {
//                CameraResizeScreen(navController)
////                CameraScreen(onCaptured = { originalBytes, resizedBytes ->
////                    val oriBase64 = Base64.getEncoder().encodeToString(originalBytes)
////                    val resBase64 = Base64.getEncoder().encodeToString(resizedBytes)
////                    navController.navigate("result/$oriBase64/$resBase64")
//////                })
//            }
//            composable("result") { backStackEntry ->
//                // Retrieve bytes from SavedStateHandle
//                val saved = backStackEntry.savedStateHandle
//                val oriBytes = saved.get<ByteArray>("ori")!!
//                val resBytes = saved.get<ByteArray>("res")!!
//                ResultScreen(oriBytes, resBytes)
//            }
//        }
//    }
//
//    @Composable
//    fun CameraResizeScreen(navController: NavHostController) {
//        var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
//        var resizedBitmap by remember { mutableStateOf<Bitmap?>(null) }
//        val scope = rememberCoroutineScope()
//        val context = LocalContext.current
//        val scrollState = rememberScrollState()
//
//        Box(
//            modifier = Modifier
//                .fillMaxSize(), contentAlignment = Alignment.BottomCenter
//        ) {
//            AndroidView(
//                factory = { ctx ->
//                    PreviewView(ctx).also { pv -> previewView = pv }
//                }, modifier = Modifier.fillMaxSize()
//            )
//
//            Button(
//                modifier = Modifier.padding(bottom = 60.dp),
//                onClick = {
//                    imageCapture?.let { capture ->
//                        val photoFile = File(cacheDir, "temp.jpg")
//                        val outputOptions =
//                            ImageCapture.OutputFileOptions.Builder(photoFile).build()
//
//                        capture.takePicture(
//                            outputOptions,
//                            ContextCompat.getMainExecutor(context),
//                            object : ImageCapture.OnImageSavedCallback {
//                                override fun onError(exc: ImageCaptureException) = Unit
//                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
//                                    scope.launch(Dispatchers.IO) {
//
//                                        val exif = ExifInterface(photoFile.absolutePath)
//                                        val ori = exif.getAttributeInt(
//                                            ExifInterface.TAG_ORIENTATION,
//                                            ExifInterface.ORIENTATION_NORMAL
//                                        )
//                                        // Map to STB-friendly codes:
//                                        val orientationCode = when (ori) {
//                                            ExifInterface.ORIENTATION_ROTATE_90 -> 6
//                                            ExifInterface.ORIENTATION_ROTATE_180 -> 3
//                                            ExifInterface.ORIENTATION_ROTATE_270 -> 8
//                                            else -> 1
//                                        }
//
//
//                                        // Load and save original
//                                        originalBitmap =
//                                            BitmapFactory.decodeFile(photoFile.absolutePath)
////                                    originalBitmap?.let { bmp ->
////                                        saveBitmapToGallery(bmp, "OriginalImage")
////                                    }
//
//                                        // Resize via JNI
//                                        val inputBytes = photoFile.readBytes()
//
//                                        //  val targetHieght =  originalBitmap!!.calcTargetHieght(413)
//
//                                        val targetHieght = originalBitmap!!.getHeightForWidth(413)
//
//                                        //  val outBytes = ImageResizer.resize(inputBytes, 600, 800,orientationCode)
//                                        val outBytes = ImageResizer.resizeGeneric(
//                                            inputBytes, 413, 531, orientationCode, 3, 1, 1, 4
//                                        )
//                                        outBytes?.let {
////                                        resizedBitmap =
////                                            BitmapFactory.decodeByteArray(it, 0, it.size)
////                                        resizedBitmap?.let { bmp ->
////                                            saveBitmapToGallery(bmp, "ResizedImage")
////                                        }
//
////                                        val oriBase64 =
////                                            Base64.getEncoder().encodeToString(inputBytes)
////                                        val resBase64 = Base64.getEncoder().encodeToString(outBytes)
//                                            scope.launch(Dispatchers.Main) {
//                                                navController.currentBackStackEntry?.savedStateHandle?.set(
//                                                    "ori",
//                                                    outBytes
//                                                )
//                                                navController.currentBackStackEntry?.savedStateHandle?.set(
//                                                    "res",
//                                                    inputBytes
//                                                )
//                                                navController.navigate("result")
////                                           navController.navigate("result/sdfds/sdfsd")
//                                            }
//
//
//                                        }
//
//
//
//                                        photoFile.delete()
//                                    }
//                                }
//                            })
//                    }
//                }) {
//                Text("Capture & Resize")
//            }
//        }
//
//
////            originalBitmap?.let { bmp ->
////                Text("Original Image", modifier = Modifier.padding(4.dp))
////                Image(
////                    bitmap = bmp.asImageBitmap(),
////                    contentDescription = null,
////                    modifier = Modifier
////                        .height(200.dp)
////                        .fillMaxWidth()
////                )
////                Spacer(modifier = Modifier.height(8.dp))
////            }
////
////            resizedBitmap?.let { bmp ->
////                Text("Resized Image", modifier = Modifier.padding(4.dp))
////                Image(
////                    bitmap = bmp.asImageBitmap(),
////                    contentDescription = null,
////                    modifier = Modifier
////                        .height(200.dp)
////                        .fillMaxWidth()
////                )
////            }
//    }
//
//    fun Bitmap.calcTargetSize(minWidth: Int, minHeight: Int): Pair<Int, Int> {
//        val scale = maxOf(1f, minOf(width.toFloat() / minWidth, height.toFloat() / minHeight))
//        return (width / scale).toInt() to (height / scale).toInt()
//    }
//
//    fun Bitmap.calcTargetHieght(minWidth: Int): Int {
//        val ratio = height / width
//        return minWidth * ratio
//    }
//
//    fun Bitmap.getHeightForWidth(targetWidth: Int): Int =
//        (height.toFloat() * targetWidth / width).toInt()
//
//    private fun startCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//        cameraProviderFuture.addListener({
//            val cameraProvider = cameraProviderFuture.get()
//            val preview = Preview.Builder().build().also {
//                it.setSurfaceProvider(previewView?.surfaceProvider)
//            }
//            imageCapture = ImageCapture.Builder().build()
//            cameraProvider.unbindAll()
//            cameraProvider.bindToLifecycle(
//                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
//            )
//        }, ContextCompat.getMainExecutor(this))
//    }
//
//    private fun saveBitmapToGallery(bitmap: Bitmap, displayName: String) {
//        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
//        } else {
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//        }
//        val values = ContentValues().apply {
//            put(
//                MediaStore.Images.Media.DISPLAY_NAME,
//                "${displayName}_${System.currentTimeMillis()}.jpg"
//            )
//            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                put(MediaStore.Images.Media.IS_PENDING, 1)
//            }
//        }
//        val resolver = contentResolver
//        val uri = resolver.insert(collection, values)
//        uri?.let {
//            resolver.openOutputStream(it)?.use { out ->
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
//            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                values.clear()
//                values.put(MediaStore.Images.Media.IS_PENDING, 0)
//                resolver.update(it, values, null, null)
//            }
//        }
//    }
//}
//
//@Composable
//fun ResultScreen(originalBytes: ByteArray, resizedBytes: ByteArray) {
//    val scrollState = rememberScrollState()
//    Column(
//        Modifier
//            .fillMaxSize()
//            .verticalScroll(scrollState)
//            .padding(8.dp),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Text("Original Image")
//        Image(
//            bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
//                .asImageBitmap(),
//            contentDescription = null,
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(300.dp)
//        )
//        Spacer(Modifier.height(16.dp))
//        Text("Resized Image")
//        Image(
//            bitmap = BitmapFactory.decodeByteArray(resizedBytes, 0, resizedBytes.size)
//                .asImageBitmap(),
//            contentDescription = null,
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(300.dp)
//        )
//    }
//}
//
