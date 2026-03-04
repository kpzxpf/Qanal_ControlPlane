package com.qanal.control.application.port.out;

public interface ChunkCompletionCounter {

    /**
     * Initializes counters for a new transfer.
     *
     * @param transferId  the transfer identifier
     * @param totalChunks total number of chunks expected
     */
    void initialize(String transferId, int totalChunks);

    /**
     * Atomically increments the done counter and checks if all chunks are complete.
     *
     * @return {@code true} if the incremented done count equals totalChunks
     */
    boolean incrementAndCheck(String transferId);

    /**
     * Removes the counters for a completed or failed transfer.
     */
    void delete(String transferId);
}
