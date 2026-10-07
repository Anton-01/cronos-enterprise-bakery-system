package com.ninsky.cronos.kitchen.recipe.file;

import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Optional;

/**
 * Lossy WebP writer over the system {@code libwebp} (FFM, no extra dependency).
 * Absent library → {@link #instance()} is empty and callers fall back to JPEG.
 */
@Slf4j
final class WebpEncoder {

    private static final List<String> LIBRARIES = List.of("libwebp.so.7", "libwebp.so", "libwebp.7.dylib", "libwebp.dylib", "libwebp.dll");
    private static final Optional<WebpEncoder> INSTANCE = load();

    /** size_t WebPEncodeRGB(const uint8_t* rgb, int w, int h, int stride, float q, uint8_t** out). */
    private final MethodHandle encodeRgb;
    /** void WebPFree(void* ptr). */
    private final MethodHandle free;

    private WebpEncoder(MethodHandle encodeRgb, MethodHandle free) {
        this.encodeRgb = encodeRgb;
        this.free = free;
    }

    static Optional<WebpEncoder> instance() {
        return INSTANCE;
    }

    /** Encodes an RGB image; {@code quality} 0–100. */
    byte[] encode(BufferedImage image, float quality) {
        int width = image.getWidth();
        int height = image.getHeight();
        int stride = width * 3;
        byte[] rgb = new byte[stride * height];
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0, i = y * stride; x < width; x++, i += 3) {
                rgb[i] = (byte) (row[x] >> 16);
                rgb[i + 1] = (byte) (row[x] >> 8);
                rgb[i + 2] = (byte) row[x];
            }
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment input = arena.allocateFrom(ValueLayout.JAVA_BYTE, rgb);
            MemorySegment output = arena.allocate(ValueLayout.ADDRESS);
            long size = (long) encodeRgb.invokeExact(input, width, height, stride, quality, output);
            MemorySegment data = output.get(ValueLayout.ADDRESS, 0);
            try {
                if (size <= 0) {
                    throw new IllegalStateException("WebPEncodeRGB failed");
                }
                return data.reinterpret(size).toArray(ValueLayout.JAVA_BYTE);
            } finally {
                if (!MemorySegment.NULL.equals(data)) {
                    free.invokeExact(data);
                }
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<WebpEncoder> load() {
        Linker linker = Linker.nativeLinker();
        for (String name : LIBRARIES) {
            try {
                SymbolLookup lib = SymbolLookup.libraryLookup(name, Arena.global());
                MethodHandle encode = linker.downcallHandle(lib.findOrThrow("WebPEncodeRGB"), FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT,
                        ValueLayout.ADDRESS));
                MethodHandle free = linker.downcallHandle(lib.findOrThrow("WebPFree"), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
                log.info("WebP thumbnails enabled ({})", name);
                return Optional.of(new WebpEncoder(encode, free));
            } catch (IllegalArgumentException | java.util.NoSuchElementException notHere) {
                // try the next name
            } catch (RuntimeException | LinkageError unavailable) {
                log.warn("libwebp unusable, thumbnails stay JPEG: {}", unavailable.toString());
                return Optional.empty();
            }
        }
        log.warn("libwebp not found, thumbnails stay JPEG");
        return Optional.empty();
    }
}
