#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#define STB_IMAGE_RESIZE_IMPLEMENTATION
#include "stb_image_resize2.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"

#include <jni.h>
#include <stdlib.h>







JNIEXPORT jbyteArray JNICALL
Java_com_azzab_image_1compression_ImageResizer_resize(JNIEnv *env, jclass clazz,
                                         jbyteArray inputData,
                                         jint newW, jint newH) {
    // 1. Get raw input bytes
    jbyte* inBytes = (*env)->GetByteArrayElements(env, inputData, NULL);
    jsize inLen = (*env)->GetArrayLength(env, inputData);

    // 2. Decode from memory
    int w, h, channels;
    unsigned char* in = stbi_load_from_memory(
            (unsigned char*)inBytes, inLen, &w, &h, &channels, 0);
    (*env)->ReleaseByteArrayElements(env, inputData, inBytes, 0);
    if (!in) return NULL;

    // 3. Resize
    unsigned char* out = stbir_resize_uint8_linear(
            in, w, h, 0, NULL, newW, newH, 0, (stbir_pixel_layout)channels);
    stbi_image_free(in);
    if (!out) return NULL;

    // 4. Encode to PNG in-memory
    int outLen;
    unsigned char* png = stbi_write_png_to_mem(
            out, newW * channels, newW, newH, channels, &outLen);
    free(out);
    if (!png) return NULL;

    // 5. Copy into a Java byte[]
    jbyteArray result = (*env)->NewByteArray(env, outLen);
    (*env)->SetByteArrayRegion(env, result, 0, outLen, (jbyte*)png);
    free(png);
    return result;
}


//JNIEXPORT jbyteArray JNICALL
//Java_com_azzab_image_1compression_ImageResizer_resize(JNIEnv *env, jclass clazz,
//                                                      jbyteArray input_png_or_jpeg, jint new_width,
//                                                      jint new_height) {
//    // TODO: implement resize()
//}
//JNIEXPORT jbyteArray JNICALL
//Java_com_azzab_image_1compression_ImageResizer_resize(JNIEnv *env, jclass clazz,
//                                                      jbyteArray input_png_or_jpeg, jint new_width,
//                                                      jint new_height) {
//    // TODO: implement resize()
//}