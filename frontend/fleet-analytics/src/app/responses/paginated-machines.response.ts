import { Machine } from "../models/machine/machine.model";

export interface PaginatedMachinesResponse {
    machines: Machine[];
    total: number;
}