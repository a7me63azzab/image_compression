#define STB_IMAGE_IMPLEMENTATION

#include "stb_image.h"

#define STB_IMAGE_RESIZE_IMPLEMENTATION

#include "stb_image_resize.h"

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
    (void) scale;
    (void) user_data;
    if (x < 0) x = -x;
    if (x < a) {
        float pix = M_PI * x;
        return (sinf(pix) / pix) * (sinf(pix / a) / (pix / a));
    }
    return 0;
}

// Support radius for Lanczos-3
static float lanczos_support(float scale, void *user_data) {
    (void) scale;
    (void) user_data;
    return 3.0f;
}


// Buffer accumulator for JPEG output
typedef struct {
    unsigned char *buf;
    int size;
} mem_buf;

static void write_jpg_callback(void *context, void *data, int size) {
    mem_buf *m = (mem_buf *) context;
    unsigned char *newBuf = (unsigned char *) realloc(m->buf, m->size + size);
    if (!newBuf) return;
    m->buf = newBuf;
    memcpy(m->buf + m->size, data, size);
    m->size += size;
}


JNIEXPORT jbyteArray JNICALL
Java_com_azzab_image_1compression_ImageResizer_resize(
        JNIEnv *env,
        jclass clazz,
        jbyteArray inputData,
        jint newW,
        jint newH,
        jint orientation
) {
    // 1) Load input bytes
    jsize inLen = (*env)->GetArrayLength(env, inputData);
    jbyte *inBytes = (*env)->GetByteArrayElements(env, inputData, NULL);
    if (!inBytes) return NULL;

    // 2) Decode image
    int w, h, channels;
    unsigned char *img = stbi_load_from_memory(
            (unsigned char *)inBytes, inLen, &w, &h, &channels, 0
    );
    (*env)->ReleaseByteArrayElements(env, inputData, inBytes, JNI_ABORT);
    if (!img) return NULL;

    // 3) Rotate if needed
    int rw, rh;
    unsigned char *rotated = rotate_image(img, w, h, channels,
                                          orientation, &rw, &rh);
    stbi_image_free(img);
    if (!rotated) return NULL;

    // 4) Resize
    size_t resizedSize = (size_t)newW * newH * channels;
    unsigned char *resized = (unsigned char *)malloc(resizedSize);
    if (!resized) {
        free(rotated);
        return NULL;
    }

    int ok = stbir_resize_uint8_generic(
            rotated,        // input_pixels
            rw, rh, 0,      // input_w, input_h, input_stride_in_bytes
            resized,        // output_pixels
            newW, newH, 0,  // output_w, output_h, output_stride_in_bytes
            channels,                       // num_channels
            STBIR_ALPHA_CHANNEL_NONE,       // alpha_channel (-1 = none)
            0,                              // flags
            STBIR_EDGE_CLAMP,               // edge_wrap_mode
            STBIR_FILTER_CATMULLROM,        // filter
            STBIR_COLORSPACE_SRGB,          // colorspace
            NULL                            // alloc_context
    );
    free(rotated);
    if (!ok) {
        free(resized);
        return NULL;
    }

    // 5) Encode PNG
    int outLen;
    unsigned char *png = stbi_write_png_to_mem(
            resized,
            newW * channels,  // stride_in_bytes
            newW, newH, channels,
            &outLen
    );
    free(resized);
    if (!png) return NULL;

    // 6) Return byte[]
    jbyteArray result = (*env)->NewByteArray(env, outLen);
    if (result) {
        (*env)->SetByteArrayRegion(env, result, 0, outLen, (jbyte *)png);
    }
    free(png);
    return result;
}


