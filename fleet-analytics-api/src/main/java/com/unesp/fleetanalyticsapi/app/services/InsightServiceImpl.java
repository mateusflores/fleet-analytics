package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.InsightSummary;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import com.unesp.fleetanalyticsapi.model.entities.telemetry.Insight;
import com.unesp.fleetanalyticsapi.model.enums.InsightScope;
import com.unesp.fleetanalyticsapi.model.enums.InsightStatus;
import com.unesp.fleetanalyticsapi.model.repositories.InsightRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import com.unesp.fleetanalyticsapi.model.repositories.VehicleRepository;
import com.unesp.fleetanalyticsapi.model.repositories.specifications.InsightSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class InsightServiceImpl implements InsightService {

    /** Status listados quando o filtro não é informado: os que pedem atenção. */
    static final Set<InsightStatus> OPEN = EnumSet.of(InsightStatus.ACTIVE, InsightStatus.ACKNOWLEDGED);

    private final InsightRepository insightRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<InsightSummary> search(Collection<InsightStatus> statuses, InsightScope scope, UUID subjectId,
                                       String category, Pageable pageable) {
        Collection<InsightStatus> filter = statuses == null || statuses.isEmpty() ? OPEN : statuses;
        Page<Insight> page = insightRepository.findAll(
                InsightSpecification.filter(filter, scope, subjectId, category), pageable);
        Map<UUID, String> names = subjectNames(page.getContent());
        return page.map(i -> toSummary(i, names.get(i.getSubjectId())));
    }

    @Override
    @Transactional(readOnly = true)
    public InsightSummary findById(UUID id) {
        Insight insight = load(id);
        return toSummary(insight, subjectNames(List.of(insight)).get(insight.getSubjectId()));
    }

    @Override
    @Transactional
    public InsightSummary updateStatus(UUID id, InsightStatus status, UUID userId) {
        if (status != InsightStatus.ACKNOWLEDGED && status != InsightStatus.DISMISSED) {
            throw new IllegalArgumentException("status must be ACKNOWLEDGED or DISMISSED");
        }
        Insight insight = load(id);
        if (!OPEN.contains(insight.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Insight is " + insight.getStatus() + " and can no longer be changed");
        }
        ZonedDateTime now = ZonedDateTime.now();
        insight.setStatus(status);
        insight.setAcknowledgedBy(userId);
        insight.setAcknowledgedAt(now);
        // o motor usa updated_at do descarte para silenciar a mesma chave por 14 dias
        insight.setUpdatedAt(now);
        insightRepository.save(insight);
        return toSummary(insight, subjectNames(List.of(insight)).get(insight.getSubjectId()));
    }

    private Insight load(UUID id) {
        return insightRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Insight not found: " + id));
    }

    /** Resolve os nomes dos assuntos da página com uma consulta por tipo de escopo. */
    private Map<UUID, String> subjectNames(List<Insight> insights) {
        Map<UUID, String> names = new HashMap<>();
        Set<UUID> vehicleIds = idsOf(insights, InsightScope.VEHICLE);
        Set<UUID> driverIds = idsOf(insights, InsightScope.DRIVER);
        if (!vehicleIds.isEmpty()) {
            vehicleRepository.findAllById(vehicleIds).forEach(v -> names.put(v.getId(), vehicleName(v)));
        }
        if (!driverIds.isEmpty()) {
            userRepository.findAllById(driverIds).forEach(u -> names.put(u.getId(), driverName(u)));
        }
        return names;
    }

    private static Set<UUID> idsOf(List<Insight> insights, InsightScope scope) {
        return insights.stream()
                .filter(i -> i.getScope() == scope && i.getSubjectId() != null)
                .map(Insight::getSubjectId)
                .collect(Collectors.toSet());
    }

    static String vehicleName(Vehicle v) {
        return firstNonBlank(v.getNickname(), v.getLicensePlate(), v.getSerialNumber());
    }

    static String driverName(User u) {
        String full = Stream.of(u.getFirstName(), u.getLastName())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        return firstNonBlank(full, u.getUsername());
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static InsightSummary toSummary(Insight i, String subjectName) {
        return new InsightSummary(i.getId(), i.getScope(), i.getSubjectId(), subjectName, i.getCategory(),
                i.getSeverity(), i.getTitle(), i.getDescription(), i.getPeriodStart(), i.getPeriodEnd(),
                i.getEvidence(), i.getStatus(), i.getAcknowledgedBy(), i.getAcknowledgedAt(), i.getCreatedAt(),
                i.getUpdatedAt());
    }
}
