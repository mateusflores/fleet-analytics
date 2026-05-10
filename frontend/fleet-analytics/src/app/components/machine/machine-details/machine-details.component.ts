import { Component, Input, OnInit, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Machine } from '../../../models/machine/machine.model';
import { MachineService } from '../../../services/machine/machine.service';
import { Router } from '@angular/router';
import { MachineTypeLabelPipe } from '../../../shared/pipes/machine-type-label.pipe';
import { MachineStatus } from '../../../models/machine/machine-status.model';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-machine-details',
  imports: [
    MatProgressSpinnerModule,
    MatIconModule,
    MatButtonModule,
    MachineTypeLabelPipe
  ],
  templateUrl: './machine-details.component.html',
  styleUrl: './machine-details.component.css'
})
export class MachineDetailsComponent implements OnInit {
  @Input() id!: number;

  private readonly _machineService = inject(MachineService);
  private readonly _router = inject(Router);
  readonly MachineStatus = MachineStatus;

  machine: Machine | null = null;

  constructor() { }

  ngOnInit(): void {
    this._machineService.getMachineById(this.id).subscribe({
      next: (machine) => {
        if (!machine) {
          console.error('Machine not found with ID:', this.id);
          this._router.navigate(['/machines']);
          return;
        }
        this.machine = machine;
      },
      error: (error) => {
        console.error('Error fetching machine details:', error);
        this._router.navigate(['/machines']);
      }
    });
  }

}
