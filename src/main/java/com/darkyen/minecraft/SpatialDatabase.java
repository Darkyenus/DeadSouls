package com.darkyen.minecraft;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.darkyen.minecraft.Util.overlaps;

/**
 * Implements a modified Quad-tree algorithm, which splits the world into Tiles,
 * which contain a few quad-tree subdivisions.
 */
public final class SpatialDatabase<E extends SpatialDatabase.Entry> {

    private static final int CHUNK_BUCKET_LEVEL = 7;
    private static final int MAX_QUAD_SIZE = 16;

    @SuppressWarnings("unchecked")
    private ChunkBucket<E>[] buckets = new ChunkBucket[16];
    private int bucketCount = 0;

    static long key(int x, int y) {
        // Y is sign-shifted
        return (long) x << 32L | ((y + 0x8000_0000L) & 0xFFFFFFFFL);
    }

    static int keyX(long key) {
        return (int) (key >> 32);
    }

    static int keyY(long key) {
        return (int) ((key & 0xFFFFFFFFL) - 0x8000_0000L);
    }

    private int findBucket(final long key) {
        final ChunkBucket<E>[] buckets = this.buckets;

        int low = 0;
        int high = bucketCount - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = buckets[mid].bucketKey;

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found.
    }

    public void insert(@NotNull E entry) {
        final int x = entry.x();
        final int y = entry.y();
        final long key = key(x >> CHUNK_BUCKET_LEVEL, y >> CHUNK_BUCKET_LEVEL);
        ChunkBucket<E>[] buckets = this.buckets;

        int bucketI = findBucket(key);
        final ChunkBucket<E> bucket;
        if (bucketI < 0) {
            bucketI = -bucketI - 1;

            // Grow if needed
            if (bucketCount == buckets.length) {
                buckets = this.buckets = Arrays.copyOf(buckets, buckets.length * 2);
            }

            // Scoot over
            System.arraycopy(buckets, bucketI, buckets, bucketI + 1, bucketCount - bucketI);

            // Insert
            buckets[bucketI] = bucket = new ChunkBucket<>(key);
            bucketCount++;
        } else {
            // Get
            bucket = buckets[bucketI];
        }

        bucket.insert(x, y, 1 << CHUNK_BUCKET_LEVEL, entry);
    }

    /** @return true if removed, false if not found */
    public boolean remove(@NotNull E entry) {
        final int x = entry.x();
        final int y = entry.y();
        final long key = key(x >> CHUNK_BUCKET_LEVEL, y >> CHUNK_BUCKET_LEVEL);

        int bucketI = findBucket(key);
        if (bucketI < 0) {
            return false;
        }
        return buckets[bucketI].remove(x, y, 1 << CHUNK_BUCKET_LEVEL, entry);
    }

    public void query(int xMin, int xMax, int yMin, int yMax, Collection<E> out) {
        final ChunkBucket<E>[] buckets = this.buckets;
        final int bucketCount = this.bucketCount;

        for (int x = xMin >> CHUNK_BUCKET_LEVEL; x <= xMax >> CHUNK_BUCKET_LEVEL; x++) {
            final long minKey = key(x, yMin >> CHUNK_BUCKET_LEVEL);
            final long maxKey = key(x, yMax >> CHUNK_BUCKET_LEVEL);
            int bucketIndex = findBucket(minKey);
            if (bucketIndex < 0) {
                bucketIndex = -bucketIndex - 1;
            }

            while (bucketIndex < bucketCount) {
                final ChunkBucket<E> bucket = buckets[bucketIndex];
                final long bucketKey = bucket.bucketKey;
                if (!(bucketKey <= maxKey)) break;

                final int bucketX = keyX(bucketKey) << CHUNK_BUCKET_LEVEL;
                final int bucketY = keyY(bucketKey) << CHUNK_BUCKET_LEVEL;
                bucket.query(xMin, xMax, yMin, yMax, bucketX, bucketY, 1 << CHUNK_BUCKET_LEVEL, out);

                bucketIndex++;
            }
        }
    }

    public void verify() {
        final int bucketCount = this.bucketCount;
        final ChunkBucket<E>[] buckets = this.buckets;
        for (int i = 0; i < bucketCount; i++) {
            final ChunkBucket<E> bucket = buckets[i];
            final long bucketKey = bucket.bucketKey;
            final int bucketMinX = keyX(bucketKey) << CHUNK_BUCKET_LEVEL;
            final int bucketMaxX = bucketMinX + (1 << CHUNK_BUCKET_LEVEL) - 1;
            final int bucketMinY = keyY(bucketKey) << CHUNK_BUCKET_LEVEL;
            final int bucketMaxY = bucketMinY + (1 << CHUNK_BUCKET_LEVEL) - 1;

            bucket.verify(bucketMinX, bucketMaxX, bucketMinY, bucketMaxY);
        }
    }

    public List<E> toList() {
        final ArrayList<E> result = new ArrayList<>();
        final int bucketCount = this.bucketCount;
        final ChunkBucket<E>[] buckets = this.buckets;
        for (int i = 0; i < bucketCount; i++) {
            buckets[i].toList(result);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private static class Quad<E extends SpatialDatabase.Entry> {
        Quad<E>[] quads;
        Object[] entries = new Object[MAX_QUAD_SIZE];
        int entriesCount = 0;

        private static int quadIndex(int x, int y, int levelSize) {
            final int quadBit = levelSize >>> 1;
            return (((x & quadBit) == quadBit) ? 0b10 : 0b00) | (((y & quadBit) == quadBit) ? 0b1 : 0b0);
        }

        final void split(int levelSize) {
            //noinspection unchecked
            final Quad<E>[] quads = this.quads = new Quad[4];
            final Object[] entries = this.entries;
            final int entriesCount = this.entriesCount;
            this.entries = null;
            this.entriesCount = -1;
            for (int i = 0; i < entriesCount; i++) {
                final E e = (E)entries[i];
                final int x = e.x();
                final int y = e.y();

                final int quadIndex = quadIndex(x, y, levelSize);
                Quad<E> quad = quads[quadIndex];
                if (quad == null) {
                    quad = quads[quadIndex] = new Quad<>();
                }
                quad.insert(x, y, levelSize >>> 1, e);
            }
        }

        final void insert(int x, int y, int levelSize, @NotNull E entry) {
            Object[] entries = this.entries;
            if (entries != null) {
                // This is a leaf

                final int entriesCount = this.entriesCount;
                if (levelSize > 1 && entriesCount >= MAX_QUAD_SIZE) {
                    // This leaf is full and can explode further, do it
                    split(levelSize);
                } else {
                    if (entriesCount == entries.length) {
                        entries = this.entries = Arrays.copyOf(entries, entries.length * 2);
                    }
                    // Add it to the leaf
                    entries[this.entriesCount++] = entry;
                    return;
                }
            }

            // This is not a leaf. To which quad does it belong?
            final int quadIndex = quadIndex(x, y, levelSize);
            Quad<E> quad = quads[quadIndex];
            if (quad == null) {
                quad = quads[quadIndex] = new Quad<>();
            }
            quad.insert(x, y, levelSize >>> 1, entry);
        }

        final boolean remove(int x, int y, int levelSize, @NotNull E entry) {
            final Object[] entries = this.entries;
            if (entries != null) {
                // This is a leaf
                final int entriesCount = this.entriesCount;
                if (entriesCount == 0) {
                    return false;
                }
                int i = 0;
                for (; i < entriesCount-1; i++) {
                    if (entries[i].equals(entry)) {
                        entries[i] = entries[--this.entriesCount];
                        entries[this.entriesCount] = null;
                        return true;
                    }
                }
                if (entries[i].equals(entry)) {
                    entries[--this.entriesCount] = null;
                    return true;
                }
                // TODO(jp): Join?
                return false;
            }

            final int quadIndex = quadIndex(x, y, levelSize);
            Quad<E> quad = quads[quadIndex];
            if (quad == null) {
                return false;
            }
            return quad.remove(x, y, levelSize >> 1, entry);
        }

        final void query(int xMin, int xMax, int yMin, int yMax, int quadX, int quadY, int quadSize, Collection<E> out) {
            final Object[] entries = this.entries;
            if (entries != null) {
                // This is a leaf
                final int entriesCount = this.entriesCount;
                for (int i = 0; i < entriesCount; i++) {
                    final E entry = (E)entries[i];
                    final int x = entry.x();
                    final int y = entry.y();
                    if (x >= xMin && x <= xMax && y >= yMin && y <= yMax) {
                        out.add(entry);
                    }
                }
                return;
            }

            final int subQuadSize = quadSize >> 1;
            final Quad<E>[] quads = this.quads;
            for (int i = 0; i < quads.length; i++) {
                final Quad<E> quad = quads[i];
                if (quad == null)
                    continue;

                final int subQuadMinX = quadX + ((i >> 1) & 1) * subQuadSize;
                final int subQuadMaxX = subQuadMinX + subQuadSize;
                final int subQuadMinY = quadY + (i & 1) * subQuadSize;
                final int subQuadMaxY = subQuadMinY + subQuadSize;
                if (!overlaps(subQuadMinX, subQuadMaxX, xMin, xMax) || !overlaps(subQuadMinY, subQuadMaxY, yMin, yMax))
                    continue;

                quad.query(xMin, xMax, yMin, yMax, subQuadMinX, subQuadMinY, subQuadSize, out);
            }
        }

        final void toList(ArrayList<E> out) {
            final Object[] entries = this.entries;
            if (entries != null) {
                // This is a leaf
                final int entriesCount = this.entriesCount;
                out.ensureCapacity(entriesCount);
                for (int i = 0; i < entriesCount; i++) {
                    out.add((E)entries[i]);
                }
            } else {
                for (Quad<E> quad : quads) {
                    if (quad == null) {
                        continue;
                    }
                    quad.toList(out);
                }
            }
        }

        void verify(int minX, int maxX, int minY, int maxY) {
            if (entries != null) {
                // This is a leaf
                final int entriesCount = this.entriesCount;
                for (int i = 0; i < entriesCount; i++) {
                    final E entry = (E) entries[i];
                    assert entry.x() >= minX && entry.x() <= maxX;
                    assert entry.y() >= minY && entry.y() <= maxY;
                }
            } else {
                final int midX = (minX + maxX + 1) >> 1;
                final int midY = (minY + maxY + 1) >> 1;

                int notNull = 0;
                for (int x = 0; x < 2; x++) {
                    for (int y = 0; y < 2; y++) {
                        final Quad<E> quad = quads[(x << 1) | y];
                        if (quad == null) {
                            continue;
                        }

                        notNull++;

                        int newMinX, newMaxX;
                        int newMinY, newMaxY;
                        if (x == 0) {
                            newMinX = minX;
                            newMaxX = midX-1;
                        } else {
                            newMinX = midX;
                            newMaxX = maxX;
                        }
                        if (y == 0) {
                            newMinY = minY;
                            newMaxY = midY-1;
                        } else {
                            newMinY = midY;
                            newMaxY = maxY;
                        }
                        quad.verify(newMinX, newMaxX, newMinY, newMaxY);
                    }
                }

                if (notNull == 0 || notNull > quads.length) {
                    throw new AssertionError("Bad node");
                }
            }
        }
    }

    private static final class ChunkBucket<E extends SpatialDatabase.Entry> extends Quad<E> {
        final long bucketKey;

        private ChunkBucket(long bucketKey) {
            this.bucketKey = bucketKey;
        }
    }

    public interface Entry {
        int x();
        int y();
    }
}
