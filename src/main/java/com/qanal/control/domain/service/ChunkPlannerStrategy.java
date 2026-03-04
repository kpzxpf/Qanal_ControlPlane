package com.qanal.control.domain.service;

import com.qanal.control.domain.model.Transfer;
import com.qanal.control.domain.model.TransferChunk;

import java.util.List;

/**
 * Strategy for splitting a file transfer into chunks.
 */
public interface ChunkPlannerStrategy {

    List<TransferChunk> plan(Transfer transfer, long bandwidthBps, int rttMs);
}
