package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.InsightSummary;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import com.unesp.fleetanalyticsapi.model.entities.telemetry.Insight;
import com.unesp.fleetanalyticsapi.model.enums.InsightScope;
import com.unesp.fleetanalyticsapi.model.enums.InsightSeverity;
import com.unesp.fleetanalyticsapi.model.enums.InsightStatus;
import com.unesp.fleetanalyticsapi.model.repositories.InsightRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import com.unesp.fleetanalyticsapi.model.repositories.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightServiceImplTest {

    @Mock
    private InsightRepository insightRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private InsightServiceImpl service;

    private static Insight insight(InsightScope scope, UUID subjectId, InsightStatus status) {
        ZonedDateTime t = ZonedDateTime.now().minusDays(1);
        return Insight.builder().id(UUID.randomUUID()).scope(scope).subjectId(subjectId).category("X")
                .severity(InsightSeverity.WARNING).title("t").description("d").evidence("{\"ratio\": 1.3}")
                .status(status).dedupKey("X:" + subjectId).createdAt(t).updatedAt(t).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchResolvesSubjectNamesPerScope() {
        UUID vehicleId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        var page = new PageImpl<>(List.of(
                insight(InsightScope.VEHICLE, vehicleId, InsightStatus.ACTIVE),
                insight(InsightScope.DRIVER, driverId, InsightStatus.ACTIVE),
                insight(InsightScope.FLEET, null, InsightStatus.ACTIVE)));
        when(insightRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(vehicleRepository.findAllById(Set.of(vehicleId))).thenReturn(List.of(
                Vehicle.builder().id(vehicleId).nickname("").licensePlate("ABC1D23").build()));
        when(userRepository.findAllById(Set.of(driverId))).thenReturn(List.of(
                User.builder().id(driverId).username("joao").firstName("João").lastName("Silva").build()));

        List<InsightSummary> result = service.search(null, null, null, null, PageRequest.of(0, 20)).getContent();

        assertThat(result).extracting(InsightSummary::subjectName).containsExactly("ABC1D23", "João Silva", null);
        assertThat(result.getFirst().evidence()).isEqualTo("{\"ratio\": 1.3}");
    }

    @Test
    void driverNameFallsBackToUsername() {
        assertThat(InsightServiceImpl.driverName(User.builder().username("maria").build())).isEqualTo("maria");
    }

    @Test
    void acknowledgeRecordsUserAndTime() {
        Insight i = insight(InsightScope.FLEET, null, InsightStatus.ACTIVE);
        ZonedDateTime before = i.getUpdatedAt();
        UUID user = UUID.randomUUID();
        when(insightRepository.findById(i.getId())).thenReturn(Optional.of(i));

        InsightSummary s = service.updateStatus(i.getId(), InsightStatus.ACKNOWLEDGED, user);

        assertThat(s.status()).isEqualTo(InsightStatus.ACKNOWLEDGED);
        assertThat(s.acknowledgedBy()).isEqualTo(user);
        assertThat(s.updatedAt()).isAfter(before);
        verify(insightRepository).save(i);
    }

    @Test
    void dismissAcknowledgedInsight() {
        Insight i = insight(InsightScope.FLEET, null, InsightStatus.ACKNOWLEDGED);
        when(insightRepository.findById(i.getId())).thenReturn(Optional.of(i));

        assertThat(service.updateStatus(i.getId(), InsightStatus.DISMISSED, UUID.randomUUID()).status())
                .isEqualTo(InsightStatus.DISMISSED);
    }

    @Test
    void rejectsStatusesReservedToTheEngine() {
        assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(), InsightStatus.RESOLVED, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(), InsightStatus.ACTIVE, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(insightRepository, never()).save(any());
    }

    @Test
    void closedInsightCannotChange() {
        Insight i = insight(InsightScope.FLEET, null, InsightStatus.RESOLVED);
        when(insightRepository.findById(eq(i.getId()))).thenReturn(Optional.of(i));

        assertThatThrownBy(() -> service.updateStatus(i.getId(), InsightStatus.DISMISSED, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
        verify(insightRepository, never()).save(any());
    }

    @Test
    void unknownInsightIsNotFound() {
        UUID id = UUID.randomUUID();
        when(insightRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(EntityNotFoundException.class);
    }
}
