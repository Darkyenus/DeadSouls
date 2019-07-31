package com.darkyen.minecraft;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class DataChannelTest {

    @Test
    void primitives() throws IOException {
        final ByteBufferChannel byteChn = new ByteBufferChannel();

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            out.writeBoolean(true);
            out.writeBoolean(false);
            out.writeByte(1);
            out.writeByte(90);
            out.writeByte(250);
            out.writeChar('a');
            out.writeChar('@');
            out.writeChar('ž');
            out.writeShort(0);
            out.writeShort(1000);
            out.writeShort(-1000);
            out.writeInt(1000);
            out.writeInt(-1000);
            out.writeInt(Integer.MAX_VALUE);
            out.writeLong(Long.MIN_VALUE);
            out.writeLong(123456789012345L);
            out.writeFloat(1234f);
            out.writeDouble(1234.0);
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            assertTrue(in.readBoolean());
            assertFalse(in.readBoolean());
            assertEquals(1, in.readByte());
            assertEquals(90, in.readByte());
            assertEquals(250, in.readUnsignedByte());
            assertEquals('a', in.readChar());
            assertEquals('@', in.readChar());
            assertEquals('ž', in.readChar());
            assertEquals(0, in.readShort());
            assertEquals(1000, in.readShort());
            assertEquals(-1000, in.readShort());
            assertEquals(1000, in.readInt());
            assertEquals(-1000, in.readInt());
            assertEquals(Integer.MAX_VALUE, in.readInt());
            assertEquals(Long.MIN_VALUE, in.readLong());
            assertEquals(123456789012345L, in.readLong());
            assertEquals(1234f, in.readFloat());
            assertEquals(1234.0, in.readDouble());
        }
    }

    @Test
    void string() throws IOException {
        final ByteBufferChannel byteChn = new ByteBufferChannel();

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            out.writeChars("123");
            out.writeBytes("123");
            out.writeUTF("123");
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            assertEquals('1', in.readChar());
            assertEquals('2', in.readChar());
            assertEquals('3', in.readChar());
            assertEquals('1', in.readByte());
            assertEquals('2', in.readByte());
            assertEquals('3', in.readByte());
            assertEquals("123", in.readUTF());
        }
    }

    @Test
    void byteStressTest() throws IOException {
        final int repeats = 1<<20;
        final ByteBufferChannel byteChn = new ByteBufferChannel(repeats);

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            for (int i = 0; i < repeats; i++) {
                out.writeByte(i);
            }
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            for (int i = 0; i < repeats; i++) {
                assertEquals((byte)i, in.readByte());
            }
        }
    }

    @Test
    void longStressTest() throws IOException {
        final int repeats = 1<<20;
        final ByteBufferChannel byteChn = new ByteBufferChannel(repeats * 8);

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            for (int i = 0; i < repeats; i++) {
                out.writeLong(i);
            }
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            for (int i = 0; i < repeats; i++) {
                assertEquals(i, in.readLong());
            }
        }
    }

    @Test
    void bytesStressTest() throws IOException {
        final int repeats = 1<<20;
        final byte[] bytes = new byte[13];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte)i;
        }
        final ByteBufferChannel byteChn = new ByteBufferChannel(repeats * bytes.length);

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            for (int i = 0; i < repeats; i++) {
                out.write(bytes);
            }
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            for (int i = 0; i < repeats; i++) {
                final byte[] newBytes = new byte[bytes.length];
                in.readFully(newBytes);
                assertArrayEquals(bytes, newBytes);
            }
        }
    }

    @Test
    void utfStressTest() throws IOException {
        final int repeats = 1<<8;
        final String utf = "1234567890ABC";
        final ByteBufferChannel byteChn = new ByteBufferChannel(repeats * (utf.length() + 2));

        try (DataOutputChannel out = new DataOutputChannel(byteChn, 64)){
            for (int i = 0; i < repeats; i++) {
                out.writeUTF(utf);
            }
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn, 64)) {
            for (int i = 0; i < repeats; i++) {
                assertEquals(utf, in.readUTF());
            }
        }
    }

    @Test
    void stressTest() throws IOException {
        final int repeats = 1 << 20;
        final ByteBufferChannel byteChn = new ByteBufferChannel(repeats * 57);

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            for (int i = 0; i < repeats; i++) {
                out.writeBoolean(true);
                out.writeBoolean(false);
                out.writeByte(1);
                out.writeByte(90);
                out.writeByte(250);
                out.writeChar('a');
                out.writeChar('@');
                out.writeChar('ž');
                out.writeShort(0);
                out.writeShort(1000);
                out.writeShort(-1000);
                out.writeInt(1000);
                out.writeInt(-1000);
                out.writeInt(Integer.MAX_VALUE);
                out.writeLong(Long.MIN_VALUE);
                out.writeLong(123456789012345L);
                out.writeFloat(1234f);
                out.writeDouble(1234.0);
            }
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            for (int i = 0; i < repeats; i++) {
                assertTrue(in.readBoolean());
                assertFalse(in.readBoolean());
                assertEquals(1, in.readByte());
                assertEquals(90, in.readByte());
                assertEquals(250, in.readUnsignedByte());
                assertEquals('a', in.readChar());
                assertEquals('@', in.readChar());
                assertEquals('ž', in.readChar());
                assertEquals(0, in.readShort());
                assertEquals(1000, in.readShort());
                assertEquals(-1000, in.readShort());
                assertEquals(1000, in.readInt());
                assertEquals(-1000, in.readInt());
                assertEquals(Integer.MAX_VALUE, in.readInt());
                assertEquals(Long.MIN_VALUE, in.readLong());
                assertEquals(123456789012345L, in.readLong());
                assertEquals(1234f, in.readFloat());
                assertEquals(1234.0, in.readDouble());
            }
        }
    }

}
