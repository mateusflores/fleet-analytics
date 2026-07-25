import { Component, effect, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { User } from '../../../models/user/user.model';
import { ConfirmDialogService } from '../../../services/confirm-dialog/confirm-dialog.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';
import { UserService } from '../../../services/user/user.service';
import { getPtBrPaginatorIntl } from '../../../shared/config/paginator/paginator-intl-pt-br';
import { UserListCardComponent } from '../user-list-card/user-list-card.component';
import { AuthService } from '../../../services/auth/auth.service';

@Component({
  selector: 'app-user-list',
  imports: [
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    UserListCardComponent
  ],
  providers: [
    { provide: MatPaginatorIntl, useFactory: getPtBrPaginatorIntl }
  ],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.css'
})
export class UserListComponent {
  private readonly _userService = inject(UserService);
  private readonly _router = inject(Router);
  private readonly _snackbar = inject(SnackbarService);
  private readonly _confirmDialog = inject(ConfirmDialogService);
  readonly authService = inject(AuthService);

  users: User[] = [];
  isLoading = true;
  pageIndex = 0;
  pageSize = 9;
  totalUsers = 0;
  readonly togglingId = signal<string | null>(null);
  readonly searchTerm = signal('');
  readonly searchField = signal<keyof User>('username');
  readonly searchFields: ReadonlyArray<{ value: keyof User; label: string }> = [
    { value: 'username', label: 'Usuário' },
    { value: 'firstName', label: 'Nome' },
    { value: 'lastName', label: 'Sobrenome' },
    { value: 'phone', label: 'Telefone' },
    { value: 'email', label: 'E-mail' },
    { value: 'roleName', label: 'Perfil' }
  ];

  constructor() {
    effect((onCleanup) => {
      const field = this.searchField();
      const term = this.searchTerm();
      const timeout = setTimeout(() => {
        this.pageIndex = 0;
        this.loadUsers(field, term);
      }, 300);

      onCleanup(() => clearTimeout(timeout));
    });
  }

  loadUsers(field = this.searchField(), term = this.searchTerm()): void {
    this.isLoading = true;
    this._userService.getUsers(field, term, this.pageIndex, this.pageSize).subscribe({
      next: (response) => {
        this.users = response.content;
        this.totalUsers = response.totalElements;
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
    this.loadUsers();
  }

  onSearch(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.searchTerm.set(input?.value ?? '');
  }

  onSearchFieldChange(field: keyof User): void {
    this.searchField.set(field);
  }

  goToAddUser(): void {
    this._router.navigate(['/users/add']);
  }

  goToUserDetails(user: User): void {
    if (!this.authService.hasPermission('users:read')) {
      return;
    }
    void this._router.navigate(['/users/details', user.username]);
  }

  onEnable(user: User): void {
    if (!this.authService.hasPermission('users:management')) {
      return;
    }
    this._confirmDialog
      .confirm({
        title: 'Habilitar usuário',
        message: `Habilitar o usuário "${user.username}"?`,
        confirmLabel: 'Habilitar',
        cancelLabel: 'Cancelar'
      })
      .subscribe(() => this.toggleUserStatus(user, true));
  }

  onDisable(user: User): void {
    if (!this.authService.hasPermission('users:management')) {
      return;
    }
    this._confirmDialog
      .confirm({
        title: 'Desabilitar usuário',
        message: `Desabilitar o usuário "${user.username}"? Ele não poderá acessar o sistema.`,
        confirmLabel: 'Desabilitar',
        cancelLabel: 'Cancelar'
      })
      .subscribe(() => this.toggleUserStatus(user, false));
  }

  private toggleUserStatus(user: User, enable: boolean): void {
    this.togglingId.set(user.userId);
    const request$ = enable
      ? this._userService.enableUser(user.userId)
      : this._userService.disableUser(user.userId);

    request$.subscribe({
      next: () => {
        this.users = this.users.map((item) =>
          item.userId === user.userId ? { ...item, enabled: enable } : item
        );
        this._snackbar.success(
          enable ? 'Usuário habilitado com sucesso.' : 'Usuário desabilitado com sucesso.'
        );
        this.togglingId.set(null);
      },
      error: (error) => {
        console.error(error);
        this._snackbar.error(
          enable ? 'Erro ao habilitar usuário.' : 'Erro ao desabilitar usuário.'
        );
        this.togglingId.set(null);
      }
    });
  }
}
