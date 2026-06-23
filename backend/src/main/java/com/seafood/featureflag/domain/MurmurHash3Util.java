package com.seafood.featureflag.domain;

/**
 * 纯 Java 实现 MurmurHash3 32-bit，用于 feature flag 百分比灰度分桶。
 * 不依赖 Guava 或任何外部库，零 Spring import。
 */
final class MurmurHash3Util {
    private MurmurHash3Util() {}

    static int hash32(String input) {
        byte[] data = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int h1 = 0;
        int len = data.length;
        int nblocks = len / 4;
        for (int i = 0; i < nblocks; i++) {
            int k1 = (data[i*4] & 0xff) | ((data[i*4+1] & 0xff) << 8)
                   | ((data[i*4+2] & 0xff) << 16) | ((data[i*4+3] & 0xff) << 24);
            k1 *= 0xcc9e2d51; k1 = Integer.rotateLeft(k1, 15); k1 *= 0x1b873593;
            h1 ^= k1; h1 = Integer.rotateLeft(h1, 13); h1 = h1 * 5 + 0xe6546b64;
        }
        int tail = nblocks * 4, k1 = 0;
        switch (len & 3) {
            case 3: k1 ^= (data[tail + 2] & 0xff) << 16;
            case 2: k1 ^= (data[tail + 1] & 0xff) << 8;
            case 1: k1 ^= (data[tail] & 0xff);
                    k1 *= 0xcc9e2d51; k1 = Integer.rotateLeft(k1, 15); k1 *= 0x1b873593; h1 ^= k1;
        }
        h1 ^= len; h1 ^= (h1 >>> 16); h1 *= 0x85ebca6b; h1 ^= (h1 >>> 13);
        h1 *= 0xc2b2ae35; h1 ^= (h1 >>> 16);
        return h1 & 0x7fffffff;
    }
}
