package org.dynamisengine.meshforge.mgi;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class MgiIo {
    private MgiIo() {
    }

    static void writeIntLe(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    static void writeLongLe(OutputStream out, long value) throws IOException {
        writeIntLe(out, (int) (value & 0xFFFFFFFFL));
        writeIntLe(out, (int) ((value >>> 32) & 0xFFFFFFFFL));
    }

    static int readIntLe(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            throw new EOFException("Unexpected EOF while reading int LE");
        }
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    static long readLongLe(InputStream in) throws IOException {
        long lo = Integer.toUnsignedLong(readIntLe(in));
        long hi = Integer.toUnsignedLong(readIntLe(in));
        return lo | (hi << 32);
    }
}
