import { MachineType } from '../machine/machine-type.model';

export interface VehicleTypePolicy {
  vehicleType: MachineType;
  economyRpmMax: number;
}

/** Política da frota (`GET`/`PUT /fleet-policy`). Limites que alimentam os insights. */
export interface FleetPolicy {
  speedLimitKmh: number;
  harshAccelMs2: number;
  harshBrakeMs2: number;
  /** segundos */
  idleThresholdS: number;
  fuelPricePerLiter: number;
  vehicleTypes: VehicleTypePolicy[];
  updatedAt: string | null;
  /** id do usuário que alterou */
  updatedBy: string | null;
}

/** Corpo do `PUT`: só os tipos de veículo enviados são alterados. */
export type UpdateFleetPolicyRequest = Omit<FleetPolicy, 'updatedAt' | 'updatedBy' | 'vehicleTypes'> & {
  vehicleTypes?: VehicleTypePolicy[];
};
