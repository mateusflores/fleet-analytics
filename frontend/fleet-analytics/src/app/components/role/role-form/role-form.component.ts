import { Component, Input, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Permission } from '../../../models/role/permission.model';
import { CreateRoleRequest } from '../../../models/role/create-role-request.model';
import { RoleService } from '../../../services/role/role.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';
import { UserService } from '../../../services/user/user.service';
import { PermissionLabelPipe } from '../../../shared/pipes/permission-label.pipe';
import { AuthService } from '../../../services/auth/auth.service';

@Component({
  selector: 'app-role-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    PermissionLabelPipe
  ],
  templateUrl: './role-form.component.html',
  styleUrl: './role-form.component.css'
})
export class RoleFormComponent implements OnInit {
  @Input() id?: string;

  private readonly _formBuilder = inject(FormBuilder);
  private readonly _roleService = inject(RoleService);
  private readonly _userService = inject(UserService);
  private readonly _router = inject(Router);
  private readonly _snackbar = inject(SnackbarService);
  readonly authService: AuthService = inject(AuthService);

  private currentRoleName = '';

  readonly form = this._formBuilder.nonNullable.group({
    name: ['', Validators.required]
  });

  permissions: Permission[] = [];
  selectedPermissionIds = new Set<string>();
  isLoading = true;
  isSaving = false;

  get isEditMode(): boolean {
    return !!this.id;
  }

  get pageTitle(): string {
    return this.isEditMode ? 'Editar Perfil' : 'Adicionar Perfil';
  }

  get userCanEdit(): boolean {
    return this.isEditMode ? this.authService.hasPermission('roles:update') : this.authService.hasPermission('roles:create');
  }

  ngOnInit(): void {
    this._roleService.getPermissions().subscribe({
      next: (permissions) => {
        this.permissions = permissions;
        if (this.id) {
          this.loadRole(this.id);
          return;
        }
        this.isLoading = false;
      },
      error: (error) => {
        console.error(error);
        this.isLoading = false;
      }
    });
  }

  private loadRole(id: string): void {
    this._roleService.getRoleById(id).subscribe({
      next: (role) => {
        this.currentRoleName = role.name;
        this.form.patchValue({ name: role.name });
        this.selectedPermissionIds = new Set(role.permissions.map((permission) => permission.id));
        this.isLoading = false;
      },
      error: (error) => {
        console.error(error);
        this._router.navigate(['/roles']);
      }
    });
  }

  isPermissionSelected(permissionId: string): boolean {
    return this.selectedPermissionIds.has(permissionId);
  }

  onPermissionToggle(permissionId: string, checked: boolean): void {
    if (checked) {
      this.selectedPermissionIds.add(permissionId);
    } else {
      this.selectedPermissionIds.delete(permissionId);
    }
  }

  onCancel(): void {
    this._router.navigate(['/roles']);
  }

  private refreshTokenIfCurrentUserRoleMatches(): void {
    const username = this.authService.decodeAccessToken()?.sub;
    if (!username || !this.currentRoleName) {
      return;
    }

    this._userService.getUserByUsername(username).subscribe({
      next: (user) => {
        if (user.roleName === this.currentRoleName) {
          this.authService.refreshTokens().subscribe({
            next: () => {},
            error: (error) => {
              console.error('Erro ao atualizar token após alteração da role do usuário logado', error);
            }
          });
        }
      },
      error: (error) => {
        console.error('Erro ao buscar usuário logado para refresh de token', error);
      }
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const request: CreateRoleRequest = {
      name: this.form.getRawValue().name.trim(),
      permissionIds: [...this.selectedPermissionIds]
    };

    this.isSaving = true;
    const isEdit = this.isEditMode && !!this.id;

    if (!this.userCanEdit) {
      this._snackbar.error(`Você não tem permissão para ${isEdit ? 'atualizar': 'criar'} perfis.`);
      this.isSaving = false;
      return;
    }

    const request$ = isEdit
      ? this._roleService.updateRole(this.id!, request)
      : this._roleService.createRole(request);

    request$.subscribe({
      next: () => {
        this._snackbar.success(
          isEdit ? 'Perfil atualizado com sucesso.' : 'Perfil criado com sucesso.'
        );
        if (isEdit) {
          this.refreshTokenIfCurrentUserRoleMatches();
        }

        this._router.navigate(['/roles']);
      },
      error: (error) => {
        console.error(error);
        this._snackbar.error(
          isEdit ? 'Erro ao atualizar perfil.' : 'Erro ao criar perfil.'
        );
        this.isSaving = false;
      }
    });
  }

  fieldError(controlName: string): string | null {
    const control = this.form.get(controlName);
    if (!control?.invalid || !control.touched) {
      return null;
    }
    if (control.errors?.['required']) {
      return 'Campo obrigatório.';
    }
    return null;
  }
}
