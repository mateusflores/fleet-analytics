import { Component, effect, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { Role } from '../../../models/role/role.model';
import { ConfirmDialogService } from '../../../services/confirm-dialog/confirm-dialog.service';
import { RoleService } from '../../../services/role/role.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';
import { RoleListCardComponent } from '../role-list-card/role-list-card.component';
import { getPtBrPaginatorIntl } from '../../../shared/config/paginator/paginator-intl-pt-br';
import { AuthService } from '../../../services/auth/auth.service';

@Component({
  selector: 'app-role-list',
  imports: [
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    RoleListCardComponent
  ],
  providers: [
    { provide: MatPaginatorIntl, useFactory: getPtBrPaginatorIntl }
  ],
  templateUrl: './role-list.component.html',
  styleUrl: './role-list.component.css'
})
export class RoleListComponent {
  private readonly _roleService = inject(RoleService);
  private readonly _router = inject(Router);
  private readonly _snackbar = inject(SnackbarService);
  private readonly _confirmDialog = inject(ConfirmDialogService);
  readonly authService: AuthService = inject(AuthService);

  readonly searchTerm = signal('');
  roles: Role[] = [];
  isLoading = true;
  pageIndex = 0;
  pageSize = 9;
  totalRoles = 0;
  readonly deletingId = signal<string | null>(null);

  constructor() {
    effect((onCleanup) => {
      const term = this.searchTerm();
      const timeout = setTimeout(() => {
        this.pageIndex = 0;
        this.loadRoles(term, this.pageIndex);
      }, 300);

      onCleanup(() => clearTimeout(timeout));
    });
  }

  private loadRoles(searchTerm: string, page: number): void {
    this.isLoading = true;
    this._roleService.getRoles(searchTerm, page, this.pageSize).subscribe({
      next: (response) => {
        this.roles = response.content;
        this.totalRoles = response.totalElements;
        this.isLoading = false;
      },
      error: (error) => {
        console.error(error);
        this.isLoading = false;
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadRoles(this.searchTerm(), this.pageIndex);
  }

  onSearch(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.searchTerm.set(input?.value ?? '');
  }

  goToAddRole(): void {
    if (!this.authService.hasPermission('roles:create')) {
      return;
    }

    this._router.navigate(['/roles/add']);
  }

  onEdit(role: Role): void {
    if (!this.authService.hasPermission('roles:update')) {
      return;
    }

    this._router.navigate(['/roles/edit', role.id]);
  }

  onDelete(role: Role): void {
    if (!this.authService.hasPermission('roles:delete')) {
      return;
    }

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
        this._snackbar.success('Perfil excluído com sucesso.');
        this.deletingId.set(null);
        this.loadRoles(this.searchTerm(), this.pageIndex);
      },
      error: (error) => {
        console.error(error);
        this._snackbar.error('Erro ao excluir perfil. Tente novamente.');
        this.deletingId.set(null);
      }
    });
  }
}
