package com.darkyen.minecraft;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 *
 */
public class ByteBufferChannel implements SeekableByteChannel {

    private final ByteBuffer buffer;


    public ByteBufferChannel(int size) {
        this.buffer = ByteBuffer.allocate(size);
        buffer.limit(0);
    }

    public ByteBufferChannel() {
        this(8096);
    }

    @Override
    public int read(ByteBuffer dst) {
        final int available = buffer.remaining();
        if (available == 0) {
            return -1;
        }
        final int toRead = dst.remaining();
        if (available > toRead) {
            final int oldLimit = buffer.limit();
            buffer.limit(buffer.position() + toRead);
            dst.put(buffer);
            buffer.limit(oldLimit);
            return toRead;
        } else {
            // available < toRead
            dst.put(buffer);
            return available;
        }
    }

    @Override
    public int write(ByteBuffer src) {
        final int toWrite = src.remaining();
        if (buffer.remaining() < toWrite) {
            // Expand
            buffer.limit(buffer.position() + toWrite);
        }
        buffer.put(src);
        return toWrite;
    }

    @Override
    public long position() {
        return buffer.position();
    }

    @Override
    public ByteBufferChannel position(long newPosition) {
        buffer.position((int) newPosition);
        return this;
    }

    @Override
    public long size() {
        return buffer.limit();
    }

    @Override
    public ByteBufferChannel truncate(long size) {
        buffer.limit((int)size);
        return this;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void close() {}
}
