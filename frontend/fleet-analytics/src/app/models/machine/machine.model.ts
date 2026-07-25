import { MachineStatus } from "./machine-status.model";
import { MachineType } from "./machine-type.model";

export interface Machine {
  id: string;
  serialNumber: string;
  licensePlate: string;
  brand: string;
  model: string;
  year: number;
  color: string;
  mileageKm: number;
  vehicleType: MachineType;
  maxLoadKg: number;
  fuelType: string;
  nickname: string;
  renavam: string;
  active: boolean;
}
