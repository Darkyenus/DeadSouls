package com.darkyen.minecraft;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.function.IntToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 *
 */
class SpatialDatabaseTest {

    @Test
    void keyTest() {
        assertTrue(SpatialDatabase.key(0, 0) < SpatialDatabase.key(0, 1));
        assertTrue(SpatialDatabase.key(0, 0) < SpatialDatabase.key(1, 1));
        assertTrue(SpatialDatabase.key(0, 0) < SpatialDatabase.key(1, 0));

        assertTrue(SpatialDatabase.key(0, 0) > SpatialDatabase.key(0, -1));
        assertTrue(SpatialDatabase.key(0, 0) > SpatialDatabase.key(-1, -1));
        assertTrue(SpatialDatabase.key(0, 0) > SpatialDatabase.key(-1, 0));

        assertEquals(0L, SpatialDatabase.key(0, Integer.MIN_VALUE));
        assertEquals(0xFFFF_FFFFL, SpatialDatabase.key(0, Integer.MAX_VALUE));

        int[] interestingValues = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int x : interestingValues) {
            for (int y : interestingValues) {
                long key = SpatialDatabase.key(x, y);
                assertEquals(x, SpatialDatabase.keyX(key));
                assertEquals(y, SpatialDatabase.keyY(key));
            }
        }
    }

    @Test
    void simple() {
        final SpatialDatabase<SpatialEntry> db = new SpatialDatabase<>();
        final SpatialEntry entry12a = new SpatialEntry(1, 2, "1.2a");
        final SpatialEntry entry12b = new SpatialEntry(1, 2, "1.2b");
        db.verify();
        db.insert(entry12a);
        db.verify();
        db.insert(entry12b);
        db.verify();
        final SpatialEntry entry45 = new SpatialEntry(4, 5, "4.5");
        db.insert(entry45);
        db.verify();

        assertTrue(db.remove(entry12a));
        db.verify();
        assertFalse(db.remove(entry12a));
        db.verify();
        assertTrue(db.remove(entry45));
        db.verify();
        assertFalse(db.remove(entry45));
        db.verify();
        assertTrue(db.remove(entry12b));
        db.verify();
        assertFalse(db.remove(entry12b));
        db.verify();
    }

    @Test
    void stress() {
        final int mask = (1 << 4)-1;//(1 << 13) - 1;
        final int offset = 0;//(1 << 12);
        final SpatialDatabase<SpatialEntry> db = new SpatialDatabase<>();
        final Random random = new Random();

        final ArrayList<SpatialEntry> backing = new ArrayList<>();

        for (int i = 0; i < 10_000; i++) {
            if (random.nextInt(3) == 0 && !backing.isEmpty()) {
                // Remove something
                final SpatialEntry toRemove = backing.remove(random.nextInt(backing.size()));
                assertTrue(db.remove(toRemove), "Iteration: "+i+" #="+backing.size());
                assertFalse(db.remove(toRemove), "Iteration: "+i+" #="+backing.size());
            } else {
                // Add something
                final SpatialEntry entry = new SpatialEntry((random.nextInt() & mask) - offset, (random.nextInt() & mask) - offset, Integer.toString(i));
                backing.add(entry);
                db.insert(entry);
            }

            /*
            final List<SpatialEntry> dbList = db.toList();
            backing.sort(Comparator.naturalOrder());
            dbList.sort(Comparator.naturalOrder());
            assertEquals(backing, dbList);
            */
            db.verify();

            // Do a query
            final int x1 = (random.nextInt() & mask) - offset;
            final int x2 = (random.nextInt() & mask) - offset;
            final int y1 = (random.nextInt() & mask) - offset;
            final int y2 = (random.nextInt() & mask) - offset;
            final int xMin = Math.min(x1, x2);
            final int xMax = Math.max(x1, x2);
            final int yMin = Math.min(y1, y2);
            final int yMax = Math.max(y1, y2);
            final HashSet<SpatialEntry> correct = new HashSet<>();
            queryArrayList(backing, xMin, xMax, yMin, yMax, correct);
            final ArrayList<SpatialEntry> spatial = new ArrayList<>();
            db.query(xMin, xMax, yMin, yMax, spatial);
            if (correct.size() != spatial.size() || !correct.equals(new HashSet<>(spatial))) {
                db.query(xMin, xMax, yMin, yMax, spatial);
            }
            assertEquals(correct.size(), spatial.size());
            assertEquals(correct, new HashSet<>(spatial));
            db.verify();
        }

        // Remove all
        while (!backing.isEmpty()) {
            final SpatialEntry toRemove = backing.remove(random.nextInt(backing.size()));
            assertTrue(db.remove(toRemove));
            assertFalse(db.remove(toRemove));
        }
    }

    private int benchmarkArrayList(long seed, int iterations, int mask) {
        final Random random = new Random(seed);
        final ArrayList<SpatialEntry> backing = new ArrayList<>();
        final ArrayList<SpatialEntry> query = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < iterations; i++) {
            backing.add(new SpatialEntry(random.nextInt() & mask, random.nextInt() & mask, null));
        }

        for (int i = 0; i < iterations*2; i++) {
            final int xMin = (random.nextInt() & mask);
            final int yMin = (random.nextInt() & mask);
            final int xMax = xMin + random.nextInt(4);
            final int yMax = yMin + random.nextInt(4);
            queryArrayList(backing, xMin, xMax, yMin, yMax, query);
            total += query.size();
            query.clear();
        }
        return total;
    }

    private static <E extends SpatialDatabase.Entry> void queryArrayList(ArrayList<E> entries, int xMin, int xMax, int yMin, int yMax, Collection<E> out) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < entries.size(); i++) {
            final E entry = entries.get(i);
            final int x = entry.x();
            final int y = entry.y();
            if (x >= xMin && x <= xMax && y >= yMin && y <= yMax) {
                out.add(entry);
            }
        }
    }

    private int benchmarkSpatialDatabase(long seed, int iterations, int mask) {
        final Random random = new Random(seed);
        final SpatialDatabase<SpatialEntry> db = new SpatialDatabase<>();
        int total = 0;
        for (int i = 0; i < iterations; i++) {
            db.insert(new SpatialEntry(random.nextInt() & mask, random.nextInt() & mask, null));
        }

        final ArrayList<SpatialEntry> query = new ArrayList<>();
        for (int i = 0; i < iterations*2; i++) {
            final int xMin = (random.nextInt() & mask);
            final int yMin = (random.nextInt() & mask);
            final int xMax = xMin + random.nextInt(4);
            final int yMax = yMin + random.nextInt(4);
            db.query(xMin, xMax, yMin, yMax, query);
            total += query.size();
            query.clear();
        }

        return total;
    }

    private void measure(String label, IntToLongFunction runnable) {
        final int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            runnable.applyAsLong(i);
        }
        final long start = System.nanoTime();
        long result = 0;
        for (int i = 0; i < iterations; i++) {
            result += runnable.applyAsLong(i);
        }
        final long total = System.nanoTime() - start;
        System.out.println(label+": "+(total / iterations)+"ns   "+result);
    }

    private static final int BENCHMARK_MASK = (1 << 14) - 1;

    @Disabled
    @Test
    void benchmark() {
        measure("ArrayList", i -> benchmarkArrayList(i, 200, BENCHMARK_MASK));
        measure("SpatialDatabase", i -> benchmarkSpatialDatabase(i, 200, BENCHMARK_MASK));
    }

    private static class SpatialEntry implements SpatialDatabase.Entry, Comparable<SpatialEntry> {

        final int x, y;
        final String label;

        private SpatialEntry(int x, int y, String label) {
            this.x = x;
            this.y = y;
            this.label = label;
        }

        @Override
        public int x() {
            return x;
        }

        @Override
        public int y() {
            return y;
        }

        @Override
        public int compareTo(@NotNull SpatialDatabaseTest.SpatialEntry o) {
            final int xCmp = Integer.compare(this.x, o.x);
            if (xCmp != 0) return xCmp;
            final int yCmp = Integer.compare(this.y, o.y);
            if (yCmp != 0) return yCmp;
            return this.label.compareTo(o.label);
        }

        @Override
        public String toString() {
            return "{" +
                    "x=" + x +
                    ", y=" + y +
                    ", label='" + label + '\'' +
                    '}';
        }
    }
}
