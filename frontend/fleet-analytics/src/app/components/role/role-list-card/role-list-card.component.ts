import { Component, Input, output } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { Role } from '../../../models/role/role.model';
import { PermissionLabelPipe } from '../../../shared/pipes/permission-label.pipe';

@Component({
  selector: 'app-role-list-card',
  imports: [MatCardModule, MatIconModule, MatButtonModule, PermissionLabelPipe],
  templateUrl: './role-list-card.component.html',
  styleUrl: './role-list-card.component.css'
})
export class RoleListCardComponent {
  @Input({ required: true }) role!: Role;

  readonly edit = output<Role>();
  readonly remove = output<Role>();

  onEdit(event: Event): void {
    event.stopPropagation();
    this.edit.emit(this.role);
  }

  onDelete(event: Event): void {
    event.stopPropagation();
    this.remove.emit(this.role);
  }
}
