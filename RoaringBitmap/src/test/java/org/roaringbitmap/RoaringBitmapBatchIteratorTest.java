package org.roaringbitmap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.roaringbitmap.RoaringBitmapWriter.writer;
import static org.roaringbitmap.SeededTestData.TestDataSet.testCase;

@Execution(ExecutionMode.CONCURRENT)
public class RoaringBitmapBatchIteratorTest {

    private static RoaringBitmap[] BITMAPS;
    private static final int[] SIZES = {
        128, 256, 1024, 8192, 5, 127, 1023
    };

    @BeforeAll
    public static void beforeAll() {
        BITMAPS = new RoaringBitmap[] {
            testCase().withArrayAt(0).withArrayAt(2).withArrayAt(4).withArrayAt((1 << 15) | (1 << 14)).build(),
            testCase().withRunAt(0).withRunAt(2).withRunAt(4).withRunAt((1 << 15) | (1 << 14)).build(),
            testCase().withBitmapAt(0).withRunAt(2).withBitmapAt(4).withBitmapAt((1 << 15) | (1 << 14)).build(),
            testCase().withArrayAt(0).withBitmapAt(2).withRunAt(4).withBitmapAt((1 << 15) | (1 << 14)).build(),
            testCase().withRunAt(0).withArrayAt(2).withBitmapAt(4).withRunAt((1 << 15) | (1 << 14)).build(),
            testCase().withBitmapAt(0).withRunAt(2).withArrayAt(4).withBitmapAt((1 << 15) | (1 << 14)).build(),
            testCase().withArrayAt(0).withBitmapAt(2).withRunAt(4).withArrayAt((1 << 15) | (1 << 14)).build(),
            testCase().withBitmapAt(0).withArrayAt(2).withBitmapAt(4).withRunAt((1 << 15) | (1 << 14)).build(),
            testCase().withRunAt((1 << 15) | (1 << 11)).withBitmapAt((1 << 15) | (1 << 12)).withArrayAt((1 << 15) | (1 << 13)).withBitmapAt((1 << 15) | (1 << 14)).build(),
            RoaringBitmap.bitmapOf(IntStream.range(1 << 10, 1 << 26).filter(i -> (i & 1) == 0).toArray()),
            RoaringBitmap.bitmapOf(IntStream.range(1 << 10, 1 << 25).filter(i -> ((i >>> 8) & 1) == 0).toArray()),
            RoaringBitmap.bitmapOf(IntStream.range(0,127).toArray()),
            RoaringBitmap.bitmapOf(IntStream.range(0,1024).toArray()),
            RoaringBitmap.bitmapOf(IntStream.concat(IntStream.range(0,256), IntStream.range(1 << 16, (1 << 16) | 256)).toArray()),
            new RoaringBitmap()
        };
    }

    @AfterAll
    public static void clear() {
        BITMAPS = null;
    }


    public static Stream<Arguments> params() {
        return Stream.of(BITMAPS)
            .flatMap(bitmap -> IntStream.of(SIZES).mapToObj(i -> Arguments.of(bitmap, i)));
    }

    @ParameterizedTest(name="offset={1}")
    @MethodSource("params")
    public void testBatchIteratorAsIntIterator(RoaringBitmap bitmap, int size) {
        IntIterator it = bitmap.getBatchIterator().asIntIterator(new int[size]);
        RoaringBitmapWriter<RoaringBitmap> w = writer().constantMemory()
                .initialCapacity(bitmap.highLowContainer.size).get();
        while (it.hasNext()) {
            w.add(it.next());
        }
        RoaringBitmap copy = w.get();
        assertEquals(bitmap, copy);
    }

    @ParameterizedTest(name="offset={1}")
    @MethodSource("params")
    public void test(RoaringBitmap bitmap, int batchSize) {
        int[] buffer = new int[batchSize];
        RoaringBitmap result = new RoaringBitmap();
        RoaringBatchIterator it = bitmap.getBatchIterator();
        int cardinality = 0;
        while (it.hasNext()) {
            int batch = it.nextBatch(buffer);
            for (int i = 0; i < batch; ++i) {
                result.add(buffer[i]);
            }
            cardinality += batch;
        }
        assertEquals(bitmap, result);
        assertEquals(bitmap.getCardinality(), cardinality);
    }

    @ParameterizedTest(name="offset={1}")
    @MethodSource("params")
    public void testBatchIteratorAdvancedIfNeeded(RoaringBitmap bitmap, int batchSize) {
        final int cardinality = bitmap.getCardinality();
        if (cardinality < 2) {
            return;
        }
        int midpoint = bitmap.select(cardinality / 2);
        int[] buffer = new int[batchSize];
        RoaringBitmap result = new RoaringBitmap();
        RoaringBatchIterator it = bitmap.getBatchIterator();
        it.advanceIfNeeded(midpoint);
        int consumed = 0;
        while (it.hasNext()) {
            int batch = it.nextBatch(buffer);
            for (int i = 0; i < batch; ++i) {
                result.add(buffer[i]);
            }
            consumed += batch;
        }
        RoaringBitmap expected = bitmap.clone();
        expected.remove(0, midpoint & 0xFFFFFFFFL);
        assertEquals(expected, result);
        assertEquals(expected.getCardinality(), consumed);
    }

    @ParameterizedTest(name="offset={1}")
    @MethodSource("params")
    public void testBatchIteratorAdvancedIfNeededToAbsentValue(RoaringBitmap bitmap, int batchSize) {
        long firstAbsent = bitmap.nextAbsentValue(0);
        int[] buffer = new int[batchSize];
        RoaringBitmap result = new RoaringBitmap();
        BatchIterator it = bitmap.getBatchIterator();
        it.advanceIfNeeded((int) firstAbsent);
        int consumed = 0;
        while (it.hasNext()) {
            int batch = it.nextBatch(buffer);
            for (int i = 0; i < batch; ++i) {
                result.add(buffer[i]);
            }
            consumed += batch;
        }
        RoaringBitmap expected = bitmap.clone();
        expected.remove(0, firstAbsent & 0xFFFFFFFFL);
        assertEquals(expected, result);
        assertEquals(expected.getCardinality(), consumed);
    }

    @ParameterizedTest(name="offset={1}")
    @MethodSource("params")
    public void testBatchIteratorAdvancedIfNeededBeyondLastValue(RoaringBitmap bitmap, int batchSize) {
        long advanceTo = bitmap.isEmpty() ? 0 : bitmap.last() + 1;
        int[] buffer = new int[batchSize];
        RoaringBitmap result = new RoaringBitmap();
        BatchIterator it = bitmap.getBatchIterator();
        it.advanceIfNeeded((int) advanceTo);
        int consumed = 0;
        while (it.hasNext()) {
            int batch = it.nextBatch(buffer);
            for (int i = 0; i < batch; ++i) {
                result.add(buffer[i]);
            }
            consumed += batch;
        }
        assertEquals(0, consumed);
        assertTrue(result.isEmpty());
    }

}
