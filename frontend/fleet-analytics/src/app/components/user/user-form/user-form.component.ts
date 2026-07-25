import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { CreateUserRequest } from '../../../models/user/create-user-request.model';
import { Role } from '../../../models/role/role.model';
import { RoleService } from '../../../services/role/role.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';
import { UserService } from '../../../services/user/user.service';

@Component({
  selector: 'app-user-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule
  ],
  templateUrl: './user-form.component.html',
  styleUrl: './user-form.component.css'
})
export class UserFormComponent implements OnInit {
  private readonly _formBuilder = inject(FormBuilder);
  private readonly _userService = inject(UserService);
  private readonly _roleService = inject(RoleService);
  private readonly _router = inject(Router);
  private readonly _snackbar = inject(SnackbarService);

  readonly form = this._formBuilder.nonNullable.group({
    username: ['', Validators.required],
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    phone: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
    roleId: ['', Validators.required]
  });

  roles: Role[] = [];
  isLoading = true;
  isSaving = false;

  ngOnInit(): void {
    this._roleService.getRolesList().subscribe({
      next: (response) => {
        this.roles = response.content;
        this.isLoading = false;
      },
      error: (error) => {
        console.error(error);
        this.isLoading = false;
      }
    });
  }

  onCancel(): void {
    this._router.navigate(['/users']);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const request: CreateUserRequest = this.form.getRawValue();
    this.isSaving = true;

    this._userService.createUser(request).subscribe({
      next: () => {
        this._snackbar.success('Usuário criado com sucesso.');
        this._router.navigate(['/users']);
      },
      error: (error) => {
        console.error(error);
        this._snackbar.error('Erro ao criar usuário.');
        this.isSaving = false;
      }
    });
  }

  fieldError(controlName: string): string | null {
    const control = this.form.get(controlName);
    if (!control?.invalid || !control.touched) {
      return null;
    }
    const errs = control.errors;
    if (!errs) {
      return null;
    }
    if (errs['required']) {
      return 'Campo obrigatório.';
    }
    if (errs['email']) {
      return 'Informe um e-mail válido.';
    }
    const minLengthErr = errs['minlength'] as { requiredLength: number } | undefined;
    if (minLengthErr) {
      return `A senha deve ter no mínimo ${minLengthErr.requiredLength} caracteres.`;
    }
    return null;
  }
}
