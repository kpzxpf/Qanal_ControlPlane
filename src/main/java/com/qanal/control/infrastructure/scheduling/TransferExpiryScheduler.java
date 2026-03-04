package com.qanal.control.infrastructure.scheduling;

import com.qanal.control.application.port.in.ExpireStaleTransfersUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin infrastructure component that drives the expiry use case on a schedule.
 * The use case itself has no @Scheduled annotation — keeping domain logic independent.
 */
@Component
public class TransferExpiryScheduler {

    private final ExpireStaleTransfersUseCase useCase;

    public TransferExpiryScheduler(ExpireStaleTransfersUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireStale() {
        useCase.expireStale();
    }
}
