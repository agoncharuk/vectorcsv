package com.querifylabs;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;

public class VectorCsvParser {
    public interface ParseListener {
        void nextField(byte[] data, int offset, int length);
        void nextLine();
    }

    // TODO add input stream.
    private final byte[] buffer;
    private final ParseListener listener;

    public VectorCsvParser(byte[] data, ParseListener listener) {
        this.buffer = data;
        this.listener = listener;
    }

    private enum ParserState {
        NORMAL,
        QUOTED
    }

    public void parse() {
        int vectorSize = 256 / 8;
        var commaVector = ByteVector.broadcast(ByteVector.SPECIES_256, ',');
        var quoteVector = ByteVector.broadcast(ByteVector.SPECIES_256, '"');
        var newlineVector = ByteVector.broadcast(ByteVector.SPECIES_256, '\n');

        int vectorPos = 0;
        int pos = 0;
        int prevRegion = 0;

        @SuppressWarnings("PointlessBitwiseExpression")
        int bound = buffer.length & ~(vectorSize - 1);
        while (vectorPos < bound) {
//            System.out.println("        Data[" + new String(buffer, vectorPos, vectorSize).replaceAll("\\n", " ") + "]");
            var vector = ByteVector.fromArray(ByteVector.SPECIES_256, buffer, vectorPos);
            var quotes = vector.compare(VectorOperators.EQ, quoteVector);
            var quotedRegions = findQuotedRegions(quotes) ^ carry(prevRegion);

            var realQuoteMask = VectorMask.fromLong(ByteVector.SPECIES_256, quotedRegions).not();

            var newlines = vector.compare(VectorOperators.EQ, newlineVector);
            var commas = vector.compare(VectorOperators.EQ, commaVector);

            var delimiters = newlines.or(commas).and(realQuoteMask).toLong();
//            {
//                System.out.println("Rqotes: " + realQuoteMask);
//                System.out.println("Quotes: " + VectorMask.fromLong(ByteVector.SPECIES_256, quotedRegions));
//                System.out.println("Will invert: " + (carry(quotedRegions) == -1));
//            }

            int shift = 0;
            while (delimiters != 0) {
                var delimiterOffset = Long.numberOfTrailingZeros(delimiters);
                var offsetInVector = shift + delimiterOffset;

                listener.nextField(buffer, pos, vectorPos + offsetInVector - pos);
                if (newlines.laneIsSet(offsetInVector)) {
                    listener.nextLine();
                }

                pos = vectorPos + offsetInVector + 1;
                shift += delimiterOffset + 1;
                delimiters >>= (delimiterOffset + 1);
            }

            vectorPos += vectorSize;
            prevRegion = quotedRegions;
        }
        // TODO process the remainder.
    }

    private static int carry(int prevRegions) {
        return -(prevRegions >>> 31);
    }
    private static int findQuotedRegions(VectorMask<Byte> quoteMask) {
        int mask = (int)quoteMask.toLong();
        int result = mask;
        while (mask != 0) {
            result ^= -mask ^ mask;
            mask &= mask - 1;
        }
        return result;
    }
}
