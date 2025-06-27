package com.azzab.image_compression


import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private var previewView: PreviewView? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraResizeScreen()
                }
            }
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(Manifest.permission.CAMERA)
        } else startCamera()
    }

    @Composable
    fun CameraResizeScreen() {
        var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var resizedBitmap by remember { mutableStateOf<Bitmap?>(null) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier
                .height(300.dp)
                .fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv -> previewView = pv }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                imageCapture?.let { capture ->
                    val photoFile = File(cacheDir, "temp.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exc: ImageCaptureException) = Unit
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                scope.launch(Dispatchers.IO) {
                                    // Load and save original
                                    originalBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                    originalBitmap?.let { bmp ->
                                        saveBitmapToGallery(bmp, "OriginalImage")
                                    }

                                    // Resize via JNI
                                    val inputBytes = photoFile.readBytes()
                                    val outBytes = ImageResizer.resize(inputBytes, 800, 600)
                                    outBytes?.let {
                                        resizedBitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                                        resizedBitmap?.let { bmp ->
                                            saveBitmapToGallery(bmp, "ResizedImage")
                                        }
                                    }

                                    photoFile.delete()
                                }
                            }
                        }
                    )
                }
            }) {
                Text("Capture & Resize")
            }

            Spacer(modifier = Modifier.height(16.dp))

            originalBitmap?.let { bmp ->
                Text("Original Image", modifier = Modifier.padding(4.dp))
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            resizedBitmap?.let { bmp ->
                Text("Resized Image", modifier = Modifier.padding(4.dp))
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                )
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView?.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, displayName: String) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${displayName}_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = contentResolver
        val uri = resolver.insert(collection, values)
        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, values, null, null)
            }
        }
    }
}


//import android.Manifest
//import android.content.pm.PackageManager
//import android.graphics.BitmapFactory
//import android.os.Bundle
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
//import androidx.compose.material.Button
//import androidx.compose.material.MaterialTheme
//import androidx.compose.material.Surface
//import androidx.compose.material.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.core.content.ContextCompat
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import java.io.File
//import java.util.concurrent.Executors
//
//class MainActivity : ComponentActivity() {
//    // Hold a reference to the PreviewView for CameraX
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
//                    CameraResizeScreen()
//                }
//            }
//        }
//        // Ask for camera permission
//        if (ContextCompat.checkSelfPermission(
//                this, Manifest.permission.CAMERA
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            requestPermission.launch(Manifest.permission.CAMERA)
//        } else startCamera()
//    }
//
//    @Composable
//    fun CameraResizeScreen() {
//        var resizedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
//        val scope = rememberCoroutineScope()
//        val context = LocalContext.current
//
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(8.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            // Camera preview
//            Box(modifier = Modifier
//                .weight(1f)
//                .fillMaxWidth()) {
//                AndroidView(
//                    factory = { ctx ->
//                        PreviewView(ctx).also { pv ->
//                            previewView = pv
//                        }
//                    },
//                    modifier = Modifier.fillMaxSize()
//                )
//            }
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            // Capture & Resize button
//            Button(onClick = {
//                imageCapture?.let { capture ->
//                    val photoFile = File(cacheDir, "temp.jpg")
//                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
//
//                    capture.takePicture(
//                        outputOptions,
//                        ContextCompat.getMainExecutor(context),
//                        object : ImageCapture.OnImageSavedCallback {
//                            override fun onError(exc: ImageCaptureException) = Unit
//                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
//                                scope.launch(Dispatchers.IO) {
//                                    val inputBytes = photoFile.readBytes()
//                                    val outBytes = ImageResizer.resize(inputBytes, 800, 600)
//                                    outBytes?.let {
//                                        resizedBitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
//                                    }
//                                    photoFile.delete()
//                                }
//                            }
//                        }
//                    )
//                }
//            }) {
//                Text("Capture & Resize")
//            }
//
//            Spacer(modifier = Modifier.height(20.dp))
//
//            // Display resized image
//            resizedBitmap?.let { bmp ->
//                Image(
//                    bitmap = bmp.asImageBitmap(),
//                    contentDescription = null,
//                    modifier = Modifier
//                        .height(200.dp)
//                        .fillMaxWidth()
//                )
//            }
//        }
//    }
//
//    private fun startCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//        cameraProviderFuture.addListener({
//            val cameraProvider = cameraProviderFuture.get()
//            val preview = Preview.Builder().build().also {
//                // Use the activity-level previewView reference
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
//    override fun onDestroy() {
//        super.onDestroy()
//        cameraExecutor.shutdown()
//    }
//}