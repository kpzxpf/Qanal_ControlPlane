package com.qanal.control.adapter.in.grpc;

import com.qanal.control.adapter.in.rest.dto.TransferProgressResponse;
import com.qanal.control.application.port.in.*;
import com.qanal.control.application.port.out.ProgressBus;
import com.qanal.control.application.port.out.TransferStore;
import com.qanal.control.domain.model.TransferStatus;
import com.qanal.control.proto.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC adapter — receives reports from the Data Plane.
 *
 * <p>All methods delegate to use cases; this class handles gRPC protocol only.
 * No direct repository access (B6 fix — removed TransferRepository injection).
 */
@Service
public class TransferGrpcAdapter extends TransferReportServiceGrpc.TransferReportServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(TransferGrpcAdapter.class);

    private final ReportChunkCompletedUseCase reportChunkUseCase;
    private final FinalizeTransferUseCase     finalizeUseCase;
    private final RegisterAgentUseCase        registerAgentUseCase;
    private final HeartbeatUseCase            heartbeatUseCase;
    private final ProgressBus                 progressBus;
    private final TransferStore               transferStore;

    public TransferGrpcAdapter(ReportChunkCompletedUseCase reportChunkUseCase,
                                FinalizeTransferUseCase finalizeUseCase,
                                RegisterAgentUseCase registerAgentUseCase,
                                HeartbeatUseCase heartbeatUseCase,
                                ProgressBus progressBus,
                                TransferStore transferStore) {
        this.reportChunkUseCase  = reportChunkUseCase;
        this.finalizeUseCase     = finalizeUseCase;
        this.registerAgentUseCase = registerAgentUseCase;
        this.heartbeatUseCase    = heartbeatUseCase;
        this.progressBus         = progressBus;
        this.transferStore       = transferStore;
    }

    @Override
    public void reportChunkCompleted(ChunkCompletedRequest request,
                                      StreamObserver<ChunkCompletedResponse> responseObserver) {
        try {
            reportChunkUseCase.report(
                    request.getTransferId(),
                    request.getChunkIndex(),
                    request.getChecksum(),
                    request.getBytesTransferred(),
                    request.getThroughputBps(),
                    request.getDurationMs()
            );
            responseObserver.onNext(ChunkCompletedResponse.newBuilder().setAccepted(true).build());
        } catch (Exception e) {
            log.error("Error processing chunk completion: transfer={}, chunk={}",
                    request.getTransferId(), request.getChunkIndex(), e);
            responseObserver.onNext(ChunkCompletedResponse.newBuilder()
                    .setAccepted(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void reportTransferFinalized(TransferFinalizedRequest request,
                                         StreamObserver<TransferFinalizedResponse> responseObserver) {
        try {
            boolean verified = finalizeUseCase.finalize(
                    request.getTransferId(), request.getFinalChecksum());
            responseObserver.onNext(TransferFinalizedResponse.newBuilder()
                    .setVerified(verified)
                    .setStatus(verified ? "COMPLETED" : "FAILED")
                    .build());
        } catch (Exception e) {
            log.error("Error finalizing transfer {}", request.getTransferId(), e);
            responseObserver.onNext(TransferFinalizedResponse.newBuilder()
                    .setVerified(false).setStatus("FAILED").build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<ProgressUpdate> streamProgress(StreamObserver<ProgressAck> responseObserver) {
        AtomicReference<TransferStatus> cachedStatus  = new AtomicReference<>(TransferStatus.IN_PROGRESS);
        AtomicReference<String>         lastTransferId = new AtomicReference<>("");

        return new StreamObserver<>() {
            @Override
            public void onNext(ProgressUpdate update) {
                String transferId = update.getTransferId();

                // Read current status (by primary key — cheap)
                TransferStatus currentStatus = transferStore.findById(transferId)
                        .map(com.qanal.control.domain.model.Transfer::getStatus)
                        .orElse(TransferStatus.IN_PROGRESS);

                lastTransferId.set(transferId);
                cachedStatus.set(currentStatus);

                progressBus.publish(new TransferProgressResponse(
                        transferId,
                        currentStatus,
                        update.getBytesTransferred(),
                        0L,
                        0,
                        update.getCurrentThroughputBps(),
                        update.getActiveStreams(),
                        update.getPacketLossRate(),
                        update.getTimestampMs()
                ));

                responseObserver.onNext(ProgressAck.newBuilder()
                        .setShouldPause(currentStatus == TransferStatus.PAUSED)
                        .setShouldCancel(currentStatus == TransferStatus.CANCELLED)
                        .build());
            }

            @Override public void onError(Throwable t) {
                log.warn("Progress stream error: {}", t.getMessage());
            }

            @Override public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void reportError(TransferErrorRequest request,
                             StreamObserver<TransferErrorResponse> responseObserver) {
        log.error("DataPlane error — transfer={}, chunk={}, code={}, retryable={}, msg={}",
                request.getTransferId(), request.getChunkIndex(),
                request.getErrorCode(), request.getIsRetryable(), request.getErrorMessage());

        String action = request.getIsRetryable() ? "RETRY" : "ABORT";
        int    delay  = request.getIsRetryable() ? 1000 : 0;

        responseObserver.onNext(TransferErrorResponse.newBuilder()
                .setAction(action).setRetryDelayMs(delay).build());
        responseObserver.onCompleted();
    }

    @Override
    public void registerAgent(AgentRegistration request,
                               StreamObserver<AgentRegistrationResponse> responseObserver) {
        try {
            var node = registerAgentUseCase.register(
                    request.getHost(),
                    request.getQuicPort(),
                    request.getRegion(),
                    request.getAvailableBandwidthBps(),
                    null
            );
            log.info("Agent registered: id={}, region={}, host={}:{}",
                    node.getId(), node.getRegion(), node.getHost(), node.getQuicPort());
            responseObserver.onNext(AgentRegistrationResponse.newBuilder()
                    .setAccepted(true).setAssignedNodeId(node.getId()).build());
        } catch (Exception e) {
            log.error("Agent registration failed", e);
            responseObserver.onNext(AgentRegistrationResponse.newBuilder()
                    .setAccepted(false).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void heartbeat(HeartbeatRequest request,
                           StreamObserver<HeartbeatResponse> responseObserver) {
        heartbeatUseCase.heartbeat(request.getAgentId(), request.getBytesInFlight());
        responseObserver.onNext(HeartbeatResponse.newBuilder().setAlive(true).build());
        responseObserver.onCompleted();
    }
}
