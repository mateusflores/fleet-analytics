import { MachineStatus } from "./machine-status.model";
import { MachineType } from "./machine-type.model";

export interface Machine {
  id: number;
  name: string;
  code: string;
  type: MachineType;
  year: number;
  mileage: number;
  status: MachineStatus;
}
