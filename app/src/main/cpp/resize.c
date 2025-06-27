#include <stdio.h>
#include <stdlib.h>

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#define STB_IMAGE_RESIZE_IMPLEMENTATION
#include "stb_image_resize2.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"

int main(int argc, char **argv)
{
    if (argc != 5)
    {
        fprintf(stderr, "Usage: %s <in.jpg> <out.png> <newW> <newH>\n", argv[0]);
        return 1;
    }

    int w, h, channels;
    unsigned char *in = stbi_load(argv[1], &w, &h, &channels, 0);
    if (!in)
    {
        fprintf(stderr, "Failed to load %s\n", argv[1]);
        return 1;
    }

    int newW = atoi(argv[3]);
    int newH = atoi(argv[4]);

    // Resize with linear filter in sRGB space:
    unsigned char *out =
        stbir_resize_uint8_linear(
            in,                          // input buffer
            w, h, 0,                     // width, height, stride=0 (packed)
            NULL,                        // NULL → auto-allocate output buffer
            newW, newH, 0,               // new dimensions, stride=0
            (stbir_pixel_layout)channels // cast channels to layout enum
        );
    if (!out)
    {
        fprintf(stderr, "Resize failed\n");
        stbi_image_free(in);
        return 1;
    }

    // Write as PNG (you can also use stbi_write_jpg for JPEG)
    if (!stbi_write_png(argv[2], newW, newH, channels, out, newW * channels))
    {
        fprintf(stderr, "Failed to write %s\n", argv[2]);
        free(out);
        stbi_image_free(in);
        return 1;
    }

    printf("Resized %dx%d → %dx%d\n", w, h, newW, newH);
    stbi_image_free(in);
    free(out);
    return 0;
}
