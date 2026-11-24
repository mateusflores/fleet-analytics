package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.FleetPolicySummary;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateFleetPolicyRequest;

import java.util.UUID;

public interface FleetPolicyService {

    FleetPolicySummary get();

    FleetPolicySummary update(UpdateFleetPolicyRequest request, UUID updatedBy);
}
