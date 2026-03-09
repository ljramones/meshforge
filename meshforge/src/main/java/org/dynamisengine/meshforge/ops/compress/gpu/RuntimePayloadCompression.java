package org.dynamisengine.meshforge.ops.compress.gpu;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Small utility seam for runtime payload compression feasibility work.
 */
public final class RuntimePayloadCompression {
    private static final int BUFFER_CHUNK_BYTES = 8 * 1024;

    private RuntimePayloadCompression() {
    }

    /**
     * Compresses payload bytes using the requested mode.
     */
    public static byte[] compress(ByteBuffer payloadBytes, RuntimePayloadCompressionMode mode) {
        Objects.requireNonNull(payloadBytes, "payloadBytes");
        Objects.requireNonNull(mode, "mode");

        byte[] source = toByteArray(payloadBytes);
        return compress(source, mode);
    }

    /**
     * Compresses payload bytes using the requested mode.
     */
    public static byte[] compress(byte[] payloadBytes, RuntimePayloadCompressionMode mode) {
        Objects.requireNonNull(payloadBytes, "payloadBytes");
        Objects.requireNonNull(mode, "mode");

        return switch (mode) {
            case NONE -> payloadBytes.clone();
            case DEFLATE -> deflate(payloadBytes);
        };
    }

    /**
     * Decompresses payload bytes using the requested mode.
     */
    public static byte[] decompress(byte[] payloadBytes, int expectedBytes, RuntimePayloadCompressionMode mode) {
        Objects.requireNonNull(payloadBytes, "payloadBytes");
        Objects.requireNonNull(mode, "mode");
        if (expectedBytes < 0) {
            throw new IllegalArgumentException("expectedBytes must be >= 0");
        }

        byte[] out = switch (mode) {
            case NONE -> payloadBytes.clone();
            case DEFLATE -> inflate(payloadBytes);
        };
        if (out.length != expectedBytes) {
            throw new IllegalArgumentException(
                "decompressed size mismatch: expected=" + expectedBytes + " actual=" + out.length
            );
        }
        return out;
    }

    private static byte[] deflate(byte[] source) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(source);
        deflater.finish();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(source.length)) {
            byte[] buffer = new byte[BUFFER_CHUNK_BYTES];
            while (!deflater.finished()) {
                int written = deflater.deflate(buffer);
                if (written == 0 && deflater.needsInput()) {
                    break;
                }
                out.write(buffer, 0, written);
            }
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("deflate failed", ex);
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] source) {
        Inflater inflater = new Inflater();
        inflater.setInput(source);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(source.length * 2)) {
            byte[] buffer = new byte[BUFFER_CHUNK_BYTES];
            while (!inflater.finished()) {
                int written = inflater.inflate(buffer);
                if (written == 0) {
                    if (inflater.needsInput()) {
                        break;
                    }
                    if (inflater.needsDictionary()) {
                        throw new IllegalArgumentException("inflate requires dictionary");
                    }
                } else {
                    out.write(buffer, 0, written);
                }
            }
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalArgumentException("inflate failed", ex);
        } finally {
            inflater.end();
        }
    }

    private static byte[] toByteArray(ByteBuffer source) {
        ByteBuffer view = source.asReadOnlyBuffer();
        byte[] out = new byte[view.remaining()];
        view.get(out);
        return out;
    }
}

