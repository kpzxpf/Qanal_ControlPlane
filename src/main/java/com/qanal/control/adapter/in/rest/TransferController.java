package com.qanal.control.adapter.in.rest;

import com.qanal.control.adapter.in.rest.dto.InitiateTransferRequest;
import com.qanal.control.adapter.in.rest.dto.TransferResponse;
import com.qanal.control.adapter.in.security.AuthenticatedOrg;
import com.qanal.control.application.port.in.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final InitiateTransferUseCase  initiateUseCase;
    private final QueryTransferUseCase     queryUseCase;
    private final StreamProgressUseCase    streamProgressUseCase;
    private final PauseTransferUseCase     pauseUseCase;
    private final ResumeTransferUseCase    resumeUseCase;
    private final CancelTransferUseCase    cancelUseCase;

    public TransferController(InitiateTransferUseCase initiateUseCase,
                               QueryTransferUseCase queryUseCase,
                               StreamProgressUseCase streamProgressUseCase,
                               PauseTransferUseCase pauseUseCase,
                               ResumeTransferUseCase resumeUseCase,
                               CancelTransferUseCase cancelUseCase) {
        this.initiateUseCase       = initiateUseCase;
        this.queryUseCase          = queryUseCase;
        this.streamProgressUseCase = streamProgressUseCase;
        this.pauseUseCase          = pauseUseCase;
        this.resumeUseCase         = resumeUseCase;
        this.cancelUseCase         = cancelUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse initiate(
            @Valid @RequestBody InitiateTransferRequest request,
            @AuthenticationPrincipal AuthenticatedOrg principal) {

        return initiateUseCase.initiate(request, principal.organization());
    }

    @GetMapping
    public Page<TransferResponse> list(
            @AuthenticationPrincipal AuthenticatedOrg principal,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return queryUseCase.list(principal.organization().getId(), pageable);
    }

    @GetMapping("/{id}")
    public TransferResponse get(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedOrg principal) {

        return queryUseCase.get(id, principal.organization().getId());
    }

    @GetMapping(value = "/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedOrg principal) {

        return streamProgressUseCase.stream(id, principal.organization().getId());
    }

    @PostMapping("/{id}/pause")
    public TransferResponse pause(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedOrg principal) {

        return pauseUseCase.pause(id, principal.organization().getId());
    }

    @PostMapping("/{id}/resume")
    public TransferResponse resume(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedOrg principal) {

        return resumeUseCase.resume(id, principal.organization().getId());
    }

    @PostMapping("/{id}/cancel")
    public TransferResponse cancel(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedOrg principal) {

        return cancelUseCase.cancel(id, principal.organization().getId());
    }
}
