package com.qanal.control.adapter.in.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/transfers  request body.
 */
public record InitiateTransferRequest(

        @NotBlank
        @Size(max = 1024)
        String fileName,

        @NotNull
        @Min(1)
        Long fileSize,

        @NotBlank
        @Size(max = 128)
        String fileChecksum,

        @Size(max = 50)
        String sourceRegion,

        @Size(max = 50)
        String targetRegion,

        Long    estimatedBandwidthBps,
        Integer estimatedRttMs
) {}
