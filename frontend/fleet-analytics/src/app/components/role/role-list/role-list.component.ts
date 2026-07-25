import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { Role } from '../../../models/role/role.model';
import { ConfirmDialogService } from '../../../services/confirm-dialog/confirm-dialog.service';
import { RoleService } from '../../../services/role/role.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';
import { RoleListCardComponent } from '../role-list-card/role-list-card.component';
import { PERMISSION_TRANSLATIONS } from '../../../shared/pipes/permission-translations';

@Component({
  selector: 'app-role-list',
  imports: [
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    RoleListCardComponent
  ],
  templateUrl: './role-list.component.html',
  styleUrl: './role-list.component.css'
})
export class RoleListComponent implements OnInit {
  private readonly _roleService = inject(RoleService);
  private readonly _router = inject(Router);
  private readonly _snackbar = inject(SnackbarService);
  private readonly _confirmDialog = inject(ConfirmDialogService);

  readonly searchTerm = signal('');
  readonly roles = signal<Role[]>([]);
  readonly isLoading = signal(true);
  readonly deletingId = signal<string | null>(null);

  readonly filteredRoles = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const roles = this.roles();
    if (!term) {
      return roles;
    }
    return roles.filter((role) =>
      role.name.toLowerCase().includes(term)
      || role.permissions.some((permission) => {
        const translation = PERMISSION_TRANSLATIONS[permission.name];
        return permission.name.toLowerCase().includes(term)
          || (permission.description ?? '').toLowerCase().includes(term)
          || (translation?.name.toLowerCase().includes(term) ?? false)
          || (translation?.description.toLowerCase().includes(term) ?? false);
      })
    );
  });

  ngOnInit(): void {
    this.loadRoles();
  }

  loadRoles(): void {
    this.isLoading.set(true);
    this._roleService.getRoles().subscribe({
      next: (roles) => {
        this.roles.set(roles);
        this.isLoading.set(false);
      },
      error: (error) => {
        console.error(error);
        this.isLoading.set(false);
      }
    });
  }

  onSearch(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.searchTerm.set(input?.value ?? '');
  }

  goToAddRole(): void {
    this._router.navigate(['/roles/add']);
  }

  onEdit(role: Role): void {
    this._router.navigate(['/roles/edit', role.id]);
  }

  onDelete(role: Role): void {
    this._confirmDialog
      .confirm({
        title: 'Excluir perfil',
        message: `Excluir o perfil "${role.name}"? Essa ação não pode ser desfeita.`,
        confirmLabel: 'Excluir',
        cancelLabel: 'Cancelar'
      })
      .subscribe(() => this.deleteRole(role));
  }

  private deleteRole(role: Role): void {
    this.deletingId.set(role.id);
    this._roleService.deleteRole(role.id).subscribe({
      next: () => {
        this.roles.update((roles) => roles.filter((item) => item.id !== role.id));
        this._snackbar.success('Perfil excluído com sucesso.');
        this.deletingId.set(null);
      },
      error: (error) => {
        console.error(error);
        this._snackbar.error('Erro ao excluir perfil. Tente novamente.');
        this.deletingId.set(null);
      }
    });
  }
}
