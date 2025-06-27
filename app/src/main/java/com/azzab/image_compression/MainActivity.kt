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

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AndroidView(
                factory = { PreviewView(it).also { pv -> previewView = pv } },
                modifier = Modifier.fillMaxSize(),

                )

            if (isProcessing) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
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

//                                        originalBitmap =
//                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)


                                        val outBytes = withContext(Dispatchers.IO) {

                                            val imageWidth =
                                                calculateTargetWidthFromFile(tempFile, 512)

                                            println("Size after 00000000-> $imageWidth")


                                            ImageResizer.resizeGeneric(
                                                bytes, imageWidth, 512,
                                                getExifOrientation(tempFile),
                                                3, 1, 1, 5
                                            ) ?: return@withContext byteArrayOf()
                                        }

//                                        val outBytes = withContext(Dispatchers.IO) {
//                                            ImageResizer.resize(
//                                                bytes, 413, 531,
//                                                getExifOrientation(tempFile),
//
//                                                ) ?: return@withContext byteArrayOf()
//                                        }


//                                        resizedBitmap = BitmapFactory.decodeByteArray(
//                                            outBytes,
//                                            0,
//                                            outBytes.size
//                                        )
                                        isProcessing = false
                                        onImageProcessed(bytes, outBytes)
                                        tempFile.delete()
                                    }
                                }
                            }
                        )
                    }
                },
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 70.dp)
            ) {
                Text("Capture & Resize")
            }

        }
    }

    private fun calculateTargetHeight(bitmap: Bitmap, targetWidth: Int): Int {
        val imageWidth = bitmap.width
        val imageHeight = bitmap.height
        val aspectRatio = imageHeight / imageWidth
        return targetWidth * aspectRatio
    }

    private fun calculateTargetHeightFromFile(file: File, targetWidth: Int): Int {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return if (opts.outWidth > 0) {
            (targetWidth * (opts.outHeight.toFloat() / opts.outWidth)).toInt()
        } else 0
    }

    private fun calculateTargetWidthFromFile(file: File, targetHeight: Int): Int {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return if (opts.outWidth > 0) {
            (targetHeight * (opts.outHeight.toFloat() / opts.outWidth)).toInt()
        } else 0
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

    fun saveBitmapToGallery(bitmap: Bitmap, displayName: String) {
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
