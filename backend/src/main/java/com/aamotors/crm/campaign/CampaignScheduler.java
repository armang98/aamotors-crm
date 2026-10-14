package com.aamotors.crm.campaign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Starts SCHEDULED campaigns when their time comes and paces dispatch of RUNNING
 * campaigns in batches, so a large segment never floods the broker or providers.
 * Paused and cancelled campaigns are left alone.
 */
@Component
public class CampaignScheduler {

    private static final Logger log = LoggerFactory.getLogger(CampaignScheduler.class);

    private final CampaignRepository campaigns;
    private final CampaignService service;
    private final CampaignProperties props;
    private final Clock clock;

    public CampaignScheduler(CampaignRepository campaigns, CampaignService service, CampaignProperties props, Clock clock) {
        this.campaigns = campaigns;
        this.service = service;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.campaigns.tick:30s}", initialDelayString = "15s")
    public void tick() {
        Instant now = Instant.now(clock);
        for (Campaign c : campaigns.findByStatusAndScheduledAtLessThanEqual(CampaignStatus.SCHEDULED, now)) {
            try {
                service.start(c.getId());
                log.info("Campaign {} ({}) started by scheduler", c.getId(), c.getName());
            } catch (Exception e) {
                log.warn("Could not start campaign {}: {}", c.getId(), e.getMessage());
            }
        }
        for (Campaign c : campaigns.findByStatus(CampaignStatus.RUNNING)) {
            try {
                int n = service.dispatchBatch(c.getId(), props.batchSize());
                if (n > 0) log.info("Campaign {} dispatched {} recipients", c.getId(), n);
            } catch (Exception e) {
                log.warn("Dispatch failed for campaign {}: {}", c.getId(), e.getMessage());
            }
        }
    }
}
