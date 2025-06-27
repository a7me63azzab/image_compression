package com.azzab.image_compression

object ImageResizer {
    init { System.loadLibrary("imageresizer") }
    @JvmStatic external fun resize(
        inputPngOrJpeg: ByteArray,
        newWidth: Int,
        newHeight: Int
    ): ByteArray?
}