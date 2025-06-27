#define STB_IMAGE_IMPLEMENTATION

#include "stb_image.h"

#define STB_IMAGE_RESIZE_IMPLEMENTATION

#include "stb_image_resize2.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION

#include "stb_image_write.h"

#include <jni.h>
#include <math.h>
#include <stdlib.h>


// Rotate buffer by EXIF orientation (1=normal,3=180,6=90CW,8=270CW)
static unsigned char *rotate_image(
        const unsigned char *in,
        int w, int h,
        int channels,
        int orientation,
        int *outW,
        int *outH
) {
    int i, x, y, c;
    if (orientation == 6) {
        *outW = h;
        *outH = w;
    } else if (orientation == 8) {
        *outW = h;
        *outH = w;
    } else {
        *outW = w;
        *outH = h;
    }
    unsigned char *out = malloc((*outW) * (*outH) * channels);
    if (!out) return NULL;

    for (y = 0; y < h; ++y) {
        for (x = 0; x < w; ++x) {
            for (c = 0; c < channels; ++c) {
                unsigned char v = in[(y * w + x) * channels + c];
                int tx, ty;
                switch (orientation) {
                    case 3: // 180
                        tx = w - x - 1;
                        ty = h - y - 1;
                        break;
                    case 6: // 90 CW
                        tx = h - y - 1;
                        ty = x;
                        break;
                    case 8: // 270 CW
                        tx = y;
                        ty = w - x - 1;
                        break;
                    default: // no rotate
                        tx = x;
                        ty = y;
                }
                out[(ty * (*outW) + tx) * channels + c] = v;
            }
        }
    }
    return out;
}

// Lanczos-3 kernel (a=3)
static float lanczos_kernel(float x, float scale, void *user_data) {
    const float a = 3.0f;
    (void)scale; (void)user_data;
    if (x < 0) x = -x;
    if (x < a) {
        float pix = M_PI * x;
        return (sinf(pix) / pix) * (sinf(pix / a) / (pix / a));
    }
    return 0;
}

// Support radius for Lanczos-3
static float lanczos_support(float scale, void *user_data) {
    (void)scale; (void)user_data;
    return 3.0f;
}




// JNI entry: resize with Lanczos-3 filter via extended API
JNIEXPORT jbyteArray JNICALL
Java_com_azzab_image_1compression_ImageResizer_resizeLanczos(
        JNIEnv* env,
        jclass clazz,
        jbyteArray inputData,
        jint newW,
        jint newH,
        jint orientation
) {
    // 1. Retrieve input bytes
    jsize inLen = (*env)->GetArrayLength(env, inputData);
    jbyte* inBytes = (*env)->GetByteArrayElements(env, inputData, NULL);

    // 2. Decode image
    int w, h, channels;
    unsigned char* img = stbi_load_from_memory(
            (unsigned char*)inBytes, inLen,
            &w, &h, &channels, 0
    );
    (*env)->ReleaseByteArrayElements(env, inputData, inBytes, 0);
    if (!img) return NULL;

    // 3. Rotate if needed
    int rw, rh;
    unsigned char* rotated = rotate_image(img, w, h, channels, orientation, &rw, &rh);
    stbi_image_free(img);
    if (!rotated) return NULL;

    // 4. Allocate buffer for output pixels
    size_t bufSize = (size_t)newW * newH * channels;
    unsigned char* outBuf = (unsigned char*)malloc(bufSize);
    if (!outBuf) { free(rotated); return NULL; }

    // 5. Initialize resize context
    STBIR_RESIZE ctx;
    stbir_resize_init(
            &ctx,
            rotated, rw, rh, 0,
            outBuf, newW, newH, 0,
            (stbir_pixel_layout)channels,
            STBIR_TYPE_UINT8
    );
    // 6. Override filters with Lanczos callbacks
    stbir_set_filter_callbacks(
            &ctx,
            lanczos_kernel, lanczos_support,
            lanczos_kernel, lanczos_support
    );

    // 7. Execute resize
    stbir_resize_extended(&ctx);
    free(rotated);

    // 8. Encode result to PNG in memory
    int outLen;
    unsigned char* png = stbi_write_png_to_mem(
            outBuf,
            newW * channels,
            newW, newH,
            channels,
            &outLen
    );
    free(outBuf);
    if (!png) return NULL;

    // 9. Create and return Java byte[]
    jbyteArray result = (*env)->NewByteArray(env, outLen);
    (*env)->SetByteArrayRegion(env, result, 0, outLen, (jbyte*)png);
    free(png);
    return result;
}


JNIEXPORT jbyteArray JNICALL
Java_com_azzab_image_1compression_ImageResizer_resize(JNIEnv *env, jclass clazz,
                                                      jbyteArray inputData,
                                                      jint newW, jint newH, jint orientation) {
    // 1) Load input bytes
    jsize inLen = (*env)->GetArrayLength(env, inputData);
    jbyte *inBytes = (*env)->GetByteArrayElements(env, inputData, NULL);

    // 2) Decode image
    int w, h, channels;
    unsigned char *img = stbi_load_from_memory(
            (unsigned char *) inBytes, inLen, &w, &h, &channels, 0);
    (*env)->ReleaseByteArrayElements(env, inputData, inBytes, 0);
    if (!img) return NULL;

    // 3) Rotate if needed
    int rw, rh;
    unsigned char *rotated = rotate_image(img, w, h, channels, orientation, &rw, &rh);
    stbi_image_free(img);
    if (!rotated) return NULL;

    // 4) Resize
    unsigned char *resized = stbir_resize_uint8_linear(
            rotated, rw, rh, 0,
            NULL, newW, newH, 0,
            (stbir_pixel_layout) channels);
    free(rotated);
    if (!resized) return NULL;

    // 5) Encode PNG
    int outLen;
    unsigned char *png = stbi_write_png_to_mem(
            resized, newW * channels, newW, newH, channels, &outLen);
    free(resized);
    if (!png) return NULL;

    // 6) Return byte[]
    jbyteArray result = (*env)->NewByteArray(env, outLen);
    (*env)->SetByteArrayRegion(env, result, 0, outLen, (jbyte *) png);
    free(png);
    return result;
}


// JNI: com.example.imageresizer.ImageResizer.resizeGeneric(
//      ByteArray inputData, int newW, int newH, int orientation,
//      int pixel_layout, int datatype, int edge_mode, int filter_mode)
JNIEXPORT jbyteArray JNICALL
Java_com_azzab_image_1compression_ImageResizer_resizeGeneric(
        JNIEnv* env,
        jclass clazz,
        jbyteArray inputData,
        jint newW,
        jint newH,
        jint orientation,
        jint pixel_layout,
        jint datatype,
        jint edge_mode,
        jint filter_mode
) {
    // 1) Get input bytes
    jsize inLen = (*env)->GetArrayLength(env, inputData);
    jbyte* inBytes = (*env)->GetByteArrayElements(env, inputData, NULL);

    // 2) Decode image
    int w, h, channels;
    unsigned char* img = stbi_load_from_memory((unsigned char*)inBytes, inLen, &w, &h, &channels, 0);
    (*env)->ReleaseByteArrayElements(env, inputData, inBytes, 0);
    if (!img) return NULL;

    // 3) Rotate if needed
    int rw, rh;
    unsigned char* rotated = rotate_image(img, w, h, channels, orientation, &rw, &rh);
    stbi_image_free(img);
    if (!rotated) return NULL;

    // 4) Allocate output buffer
    size_t outStride = 0;
    unsigned char* outBuf = malloc(newW * newH * channels);
    if (!outBuf) { free(rotated); return NULL; }

    // 5) Call medium-complexity API
    stbir_resize(
            rotated,              // input_pixels
            rw, rh, 0,            // input_w, input_h, input_stride
            outBuf,               // output_pixels
            newW, newH, outStride, // output_w, output_h, output_stride
            (stbir_pixel_layout)pixel_layout,
            (stbir_datatype)datatype,
            (stbir_edge)edge_mode,
            (stbir_filter)filter_mode
    );
    free(rotated);

    // 6) Encode to PNG
    int outLen;
    unsigned char* png = stbi_write_png_to_mem(outBuf, newW * channels, newW, newH, channels, &outLen);
    free(outBuf);
    if (!png) return NULL;

    // 7) Return byte[]
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