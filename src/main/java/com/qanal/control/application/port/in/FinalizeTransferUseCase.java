package com.qanal.control.application.port.in;

public interface FinalizeTransferUseCase {

    /**
     * Result of transfer finalization.
     *
     * @param verified        true if checksum matches
     * @param egressHost      non-empty if the ingress DataPlane must relay to this egress host
     * @param egressPort      QUIC port on the egress DataPlane (upload/relay port)
     * @param egressDownloadPort download port on the egress DataPlane for recipients
     */
    record FinalizeResult(boolean verified, String egressHost, int egressPort, int egressDownloadPort) {}

    FinalizeResult finalize(String transferId, String finalChecksum);
}
