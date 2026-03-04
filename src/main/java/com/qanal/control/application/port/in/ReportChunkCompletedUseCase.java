package com.qanal.control.application.port.in;

public interface ReportChunkCompletedUseCase {

    void report(String transferId, int chunkIndex, String checksum,
                long bytes, double throughputBps, long durationMs);
}
