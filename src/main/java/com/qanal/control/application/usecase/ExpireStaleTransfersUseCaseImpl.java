package com.qanal.control.application.usecase;

import com.qanal.control.application.port.in.ExpireStaleTransfersUseCase;
import com.qanal.control.application.port.out.TransferStore;
import com.qanal.control.domain.model.Transfer;
import com.qanal.control.domain.model.TransferStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * No @Scheduled here — scheduling is wired in infrastructure layer via a thin @Component.
 */
@Service
public class ExpireStaleTransfersUseCaseImpl implements ExpireStaleTransfersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpireStaleTransfersUseCaseImpl.class);

    private final TransferStore transferStore;

    public ExpireStaleTransfersUseCaseImpl(TransferStore transferStore) {
        this.transferStore = transferStore;
    }

    @Override
    @Transactional
    public void expireStale() {
        var activeStatuses = List.of(
                TransferStatus.INITIATED, TransferStatus.WAITING_SENDER,
                TransferStatus.IN_PROGRESS, TransferStatus.PAUSED, TransferStatus.COMPLETING
        );
        var expired = transferStore.findExpired(activeStatuses, OffsetDateTime.now());
        if (!expired.isEmpty()) {
            var ids = expired.stream().map(Transfer::getId).toList();
            int count = transferStore.bulkExpire(ids);
            log.info("Expired {} stale transfer(s)", count);
        }
    }
}
