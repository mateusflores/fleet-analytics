package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.InsightSummary;
import com.unesp.fleetanalyticsapi.model.enums.InsightScope;
import com.unesp.fleetanalyticsapi.model.enums.InsightStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.UUID;

public interface InsightService {

    Page<InsightSummary> search(Collection<InsightStatus> statuses, InsightScope scope, UUID subjectId,
                                String category, Pageable pageable);

    InsightSummary findById(UUID id);

    InsightSummary updateStatus(UUID id, InsightStatus status, UUID userId);
}
