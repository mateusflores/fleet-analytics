package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.InsightSummary;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateInsightStatusRequest;
import com.unesp.fleetanalyticsapi.app.services.InsightService;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.enums.InsightScope;
import com.unesp.fleetanalyticsapi.model.enums.InsightStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Insights históricos gerados pelo ml-service (ver ml-service/docs/plano-insights-historicos.md). */
@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    /** Sem {@code status}, lista os abertos (ACTIVE e ACKNOWLEDGED). Aceita vários: {@code status=ACTIVE,RESOLVED}. */
    @GetMapping
    @PreAuthorize("hasAuthority('analytics:view')")
    public ResponseEntity<Page<InsightSummary>> search(
            @RequestParam(required = false) List<InsightStatus> status,
            @RequestParam(required = false) InsightScope scope,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(insightService.search(status, scope, subjectId, category, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('analytics:view')")
    public ResponseEntity<InsightSummary> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(insightService.findById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('analytics:view')")
    public ResponseEntity<InsightSummary> updateStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateInsightStatusRequest request,
                                                       Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return ResponseEntity.ok(insightService.updateStatus(id, request.status(), currentUser.getId()));
    }
}
