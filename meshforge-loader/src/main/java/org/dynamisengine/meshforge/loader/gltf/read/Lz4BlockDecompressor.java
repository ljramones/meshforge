package org.dynamisengine.meshforge.loader.gltf.read;

import java.util.Objects;

/**
 * Pure-Java LZ4 block decoder used by meshopt-compressed glTF payloads.
 */
public final class Lz4BlockDecompressor {
    private Lz4BlockDecompressor() {
    }

    /**
     * Executes decompress.
     * @param input parameter value
     * @param outputSize parameter value
     * @return resulting value
     */
    public static byte[] decompress(byte[] input, int outputSize) {
        Objects.requireNonNull(input, "input");
        if (outputSize < 0) {
            throw new IllegalArgumentException("outputSize must be >= 0");
        }
        byte[] output = new byte[outputSize];
        decompressInto(input, output);
        return output;
    }

    /**
     * Decompresses an LZ4 block into the provided output array.
     *
     * @param input compressed bytes
     * @param output destination array sized to expected decompressed length
     */
    public static void decompressInto(byte[] input, byte[] output) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        int inPos = 0;
        int outPos = 0;
        int outputSize = output.length;
        while (inPos < input.length) {
            int token = u8(input[inPos++]);

            int literalLength = token >>> 4;
            if (literalLength == 15) {
                int add;
                do {
                    ensureInputRemaining(inPos, input.length, 1);
                    add = u8(input[inPos++]);
                    literalLength += add;
                } while (add == 255);
            }

            ensureInputRemaining(inPos, input.length, literalLength);
            ensureOutputRemaining(outPos, outputSize, literalLength);
            System.arraycopy(input, inPos, output, outPos, literalLength);
            inPos += literalLength;
            outPos += literalLength;

            if (inPos >= input.length) {
                break;
            }

            ensureInputRemaining(inPos, input.length, 2);
            int offset = u8(input[inPos]) | (u8(input[inPos + 1]) << 8);
            inPos += 2;
            if (offset <= 0 || offset > outPos) {
                throw new IllegalArgumentException("Invalid LZ4 match offset: " + offset);
            }

            int matchLength = token & 0x0F;
            if (matchLength == 15) {
                int add;
                do {
                    ensureInputRemaining(inPos, input.length, 1);
                    add = u8(input[inPos++]);
                    matchLength += add;
                } while (add == 255);
            }
            matchLength += 4;

            ensureOutputRemaining(outPos, outputSize, matchLength);
            int matchPos = outPos - offset;
            for (int i = 0; i < matchLength; i++) {
                output[outPos++] = output[matchPos + i];
            }
        }
        if (outPos != outputSize) {
            throw new IllegalArgumentException(
                "LZ4 output size mismatch: expected " + outputSize + " bytes, decoded " + outPos + " bytes");
        }
    }

    private static int u8(byte value) {
        return value & 0xFF;
    }

    private static void ensureInputRemaining(int pos, int len, int count) {
        if (count < 0 || pos + count > len) {
            throw new IllegalArgumentException("Truncated LZ4 input");
        }
    }

    private static void ensureOutputRemaining(int pos, int len, int count) {
        if (count < 0 || pos + count > len) {
            throw new IllegalArgumentException("LZ4 output exceeds declared size");
        }
    }
}
