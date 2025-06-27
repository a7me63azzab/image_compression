package com.azzab.image_compression

object ImageResizer {
    init { System.loadLibrary("imageresizer") }
    @JvmStatic external fun resize(
        inputPngOrJpeg: ByteArray,
        newWidth: Int,
        newHeight: Int,
        orientation: Int
    ): ByteArray?

    @JvmStatic external fun resizeGeneric(
        inputData: ByteArray,
        newWidth: Int,
        newHeight: Int,
        orientation: Int,
        pixelLayout: Int,
        dataType: Int,
        edgeMode: Int,
        filterMode: Int
    ): ByteArray?

    @JvmStatic external fun resizeLanczos(
        inputData: ByteArray,
        newWidth: Int,
        newHeight: Int,
        orientation: Int,
        quality: Int,
    ): ByteArray?
}