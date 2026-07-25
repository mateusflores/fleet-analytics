import { Component, Input, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { Machine } from '../../../models/machine/machine.model';
import { MachineStatus } from '../../../models/machine/machine-status.model';
import { MachineTypeLabelPipe } from '../../../shared/pipes/machine-type-label.pipe';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth/auth.service';

@Component({
  selector: 'app-machine-list-card',
  imports: [MatCardModule, MatIconModule, MachineTypeLabelPipe],
  templateUrl: './machine-list-card.component.html',
  styleUrl: './machine-list-card.component.css'
})
export class MachineListCardComponent {
  readonly MachineStatus = MachineStatus;
  readonly authService: AuthService = inject(AuthService);

  @Input({ required: true }) machine!: Machine;
  private readonly _router = inject(Router);

  onOpenDetails(): void {
    if (this.authService.hasAnyPermission('vehicles:view', 'vehicles:update')) {
      this._router.navigate(['/machines/details', this.machine.id]);
    }
  }
}
