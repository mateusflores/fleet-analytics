export interface Trip {
  id: string;
  driverId: string;
  driverName: string;
  vehicleId: string;
  vehiclePlate: string;
  startTime: string;
  endTime: string | null;
}
