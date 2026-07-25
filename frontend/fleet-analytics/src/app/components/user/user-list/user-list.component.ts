import { Component, inject, OnInit, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { User } from '../../../models/user/user.model';
import { ConfirmDialogService } from '../../../services/confirm-dialog/confirm-dialog.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';
import { UserService } from '../../../services/user/user.service';
import { getPtBrPaginatorIntl } from '../../../shared/config/paginator/paginator-intl-pt-br';
import { UserListCardComponent } from '../user-list-card/user-list-card.component';

@Component({
  selector: 'app-user-list',
  imports: [
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
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
export class UserListComponent implements OnInit {
  private readonly _userService = inject(UserService);
  private readonly _router = inject(Router);
  private readonly _snackbar = inject(SnackbarService);
  private readonly _confirmDialog = inject(ConfirmDialogService);

  users: User[] = [];
  isLoading = true;
  pageIndex = 0;
  pageSize = 9;
  totalUsers = 0;
  readonly togglingId = signal<string | null>(null);

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.isLoading = true;
    this._userService.getUsers(this.pageIndex, this.pageSize).subscribe({
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

  goToAddUser(): void {
    this._router.navigate(['/users/add']);
  }

  onEnable(user: User): void {
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
    this.togglingId.set(user.id);
    const request$ = enable
      ? this._userService.enableUser(user.id)
      : this._userService.disableUser(user.id);

    request$.subscribe({
      next: () => {
        this.users = this.users.map((item) =>
          item.id === user.id ? { ...item, enabled: enable } : item
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
