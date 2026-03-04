package com.qanal.control.infrastructure.common;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UUID version 7 generator.
 *
 * <p>Structure (RFC 9562):
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    unix_ts_ms (48 bits)                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  ver=0111 |      rand_a (12 bits)                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |var=10|           rand_b (62 bits)                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * <p>Thread-safe; produces lexicographically sortable UUIDs.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong   STATE  = new AtomicLong(0L);

    private UuidV7() {}

    public static String generate() {
        long ts;
        long seq;

        while (true) {
            long now     = System.currentTimeMillis();
            long prev    = STATE.get();
            long prevTs  = prev >>> 12;
            long prevSeq = prev & 0xFFFL;

            if (now > prevTs) {
                if (STATE.compareAndSet(prev, now << 12)) {
                    ts  = now;
                    seq = 0;
                    break;
                }
            } else {
                long nextSeq = prevSeq + 1;
                if (nextSeq > 0xFFFL) {
                    Thread.onSpinWait();
                    continue;
                }
                long next = (prevTs << 12) | nextSeq;
                if (STATE.compareAndSet(prev, next)) {
                    ts  = prevTs;
                    seq = nextSeq;
                    break;
                }
            }
        }

        long msb = (ts << 16) | (0x7L << 12) | seq;
        long lsb = (2L << 62) | (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL);

        return new UUID(msb, lsb).toString();
    }
}
