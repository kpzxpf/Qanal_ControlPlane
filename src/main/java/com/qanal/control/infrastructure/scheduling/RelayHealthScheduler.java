package com.qanal.control.infrastructure.scheduling;

import com.qanal.control.adapter.out.persistence.RelayNodeJpaRepository;
import com.qanal.control.domain.model.RelayStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Marks relay nodes UNHEALTHY when no heartbeat for > 30 s.
 */
@Component
public class RelayHealthScheduler {

    private static final Logger log = LoggerFactory.getLogger(RelayHealthScheduler.class);
    private static final long   HEARTBEAT_TIMEOUT_SECONDS = 30;

    private final RelayNodeJpaRepository repo;

    public RelayHealthScheduler(RelayNodeJpaRepository repo) {
        this.repo = repo;
    }

    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void checkNodes() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);
        int marked = repo.markUnhealthyOlderThan(cutoff);
        if (marked > 0) {
            log.warn("Marked {} relay node(s) as UNHEALTHY (no heartbeat since {})", marked, cutoff);
        }
    }
}
