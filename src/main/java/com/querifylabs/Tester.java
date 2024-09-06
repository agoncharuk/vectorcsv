package com.querifylabs;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class Tester {
    public static void main(String[] args) throws Exception {
        var file = new File("data/quoted.csv");
        byte[] data = new FileInputStream(file).readNBytes((int)Files.size(file.toPath()));

        var parser = new VectorCsvParser(data, new VectorCsvParser.ParseListener() {
            @Override
            public void nextField(byte[] data, int offset, int length) {
                var str = new String(data, offset, length, StandardCharsets.UTF_8);
                assert str.length() == length;
            }

            @Override
            public void nextLine() {
//                System.out.println("New line");
            }
        });

        System.out.println("Warmup...");
        {
            for (int i = 0; i < 100_000; ++i) {
                parser.parse();
            }
        }
        System.out.println("Done warmup");
        long repeat = 1_000_000;
        long start = System.currentTimeMillis();
        for (int i = 0; i < repeat; ++i) {
            parser.parse();
        }
        long end = System.currentTimeMillis();

        var parsedBytesPerSec = data.length * repeat * 1000L / (end - start);
        System.out.println("Parse speed " + parsedBytesPerSec + " b/s");
    }
}
