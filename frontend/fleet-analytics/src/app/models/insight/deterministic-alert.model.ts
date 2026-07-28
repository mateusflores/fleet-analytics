export interface DeterministicAlert {
  vehicleId: string;
  tripId: string;
  timestamp: string;
  eventType: string;
  severity: string;
  message: string;
  isNew: boolean;
}
