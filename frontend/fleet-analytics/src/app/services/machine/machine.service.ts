import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Machine } from '../../models/machine/machine.model';
import { delay, Observable, of } from 'rxjs';
import { MachineType } from '../../models/machine/machine-type.model';
import { MachineStatus } from '../../models/machine/machine-status.model';
import { PaginatedMachinesResponse } from '../../responses/paginated-machines.response';

@Injectable({
  providedIn: 'root'
})
export class MachineService {
  
  private readonly _httpClient: HttpClient = inject(HttpClient);

  machinesMock: Machine[] = [
    {
      id: 1,
      name: 'Hauler Alpha',
      code: 'TRK-2024-001',
      type: MachineType.Truck,
      year: 2022,
      mileage: 45230,
      status: MachineStatus.Online
    },
    {
      id: 2,
      name: 'Big Dig',
      code: 'EXC-2024-002',
      type: MachineType.Excavator,
      year: 2023,
      mileage: 12450,
      status: MachineStatus.Online
    },
    {
      id: 3,
      name: 'Green Giant',
      code: 'TRC-2024-003',
      type: MachineType.Tractor,
      year: 2021,
      mileage: 67890,
      status: MachineStatus.Offline
    },
    {
      id: 4,
      name: 'Load Master',
      code: 'LDR-2024-004',
      type: MachineType.Loader,
      year: 2024,
      mileage: 3210,
      status: MachineStatus.Online
    },
  ]

  constructor() {
    for (let i = 0; i < 100; i++) {
      this.machinesMock.push({
        id: i + 5,
        name: `Machine ${i + 1}`,
        code: `M${i + 1}-2024-00${i + 1}`,
        type: MachineType.Tractor,
        year: 2024,
        mileage: 3210,
        status: Math.random() > 0.5 ? MachineStatus.Online : MachineStatus.Offline
      })
    }
  }
  

  public getMachines(search: string, page = 1, pageSize = 9): Observable<PaginatedMachinesResponse> {
    //return this._httpClient.get<Machine[]>(`http://localhost:3000/machines?search=${search}`);
    const safePage = Math.max(1, page);
    const safePageSize = Math.max(1, pageSize);
    const filteredMachines = this.machinesMock.filter((machine) =>
      machine.name.toLowerCase().includes(search.toLowerCase())
    );
    const startIndex = (safePage - 1) * safePageSize;
    const endIndex = startIndex + safePageSize;
    const paginatedMachines = filteredMachines.slice(startIndex, endIndex);

    return of({
      machines: paginatedMachines,
      total: filteredMachines.length
    }).pipe(delay(700));
  }

  public addMachine(machine: Machine): Observable<Machine> {
    const newMachine: Machine = {
      ...machine,
      id: this.machinesMock.length + 1,
      status: machine.status ?? Math.random() > 0.5 ? MachineStatus.Online : MachineStatus.Offline
    };
    this.machinesMock.unshift(newMachine);
    return of(newMachine).pipe(delay(500));
  }

  public getMachineById(id: number): Observable<Machine | undefined> {
    const found = this.machinesMock.find(m => m.id == id);
    return of(found).pipe(delay(200));
  }

  public updateMachine(id: number, patch: Partial<Machine>): Observable<Machine | undefined> {
    const idx = this.machinesMock.findIndex(m => m.id === id);
    if (idx === -1) {
      return of(undefined).pipe(delay(200));
    }
    const updated = { ...this.machinesMock[idx], ...patch };
    this.machinesMock[idx] = updated;
    return of(updated).pipe(delay(300));
  }
}
