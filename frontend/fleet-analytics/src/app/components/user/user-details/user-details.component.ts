import { Component, Input, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { User } from '../../../models/user/user.model';
import { UserService } from '../../../services/user/user.service';
import { AuthService } from '../../../services/auth/auth.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';

@Component({
  selector: 'app-user-details',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './user-details.component.html',
  styleUrl: './user-details.component.css'
})
export class UserDetailsComponent implements OnInit {
  @Input() username!: string;

  private readonly _userService = inject(UserService);
  private readonly _router = inject(Router);
  private readonly _formBuilder = inject(FormBuilder);
  private readonly _snackbar = inject(SnackbarService);
  readonly authService = inject(AuthService);

  user: User | null = null;
  isEditing = false;
  isLoading = true;
  isSaving = false;

  readonly editForm = this._formBuilder.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    phone: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]]
  });

  ngOnInit(): void {
    this._userService.getUserByUsername(this.username).subscribe({
      next: (user) => {
        this.user = user;
        this.isLoading = false;
        this.startEdit();
      },
      error: (error) => {
        console.error('Erro ao buscar detalhes do usuário:', error);
        this.isLoading = false;
        void this._router.navigate(['/users']);
      }
    });
  }

  startEdit(): void {
    if (!this.user) {
      return;
    }

    this.editForm.patchValue({
      firstName: this.user.firstName,
      lastName: this.user.lastName,
      phone: this.user.phone,
      email: this.user.email
    });
  }

  saveEdit(): void {
    if (!this.user || !this.authService.hasPermission('users:management')) {
      return;
    }
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    this._userService.updateUser(this.user.userId, this.editForm.getRawValue()).subscribe({
      next: (updatedUser) => {
        this.user = updatedUser;
        this.isSaving = false;
        this._snackbar.success('Usuário atualizado com sucesso.');
        void this._router.navigate(['/users']);
      },
      error: (error) => {
        console.error('Erro ao atualizar usuário:', error);
        this.isSaving = false;
        this._snackbar.error('Erro ao atualizar usuário. Tente novamente.');
      }
    });
  }

  fieldError(controlName: string): string | null {
    const control = this.editForm.get(controlName);
    if (!control?.invalid || !control.touched) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Campo obrigatório.';
    }
    if (control.hasError('email')) {
      return 'Informe um e-mail válido.';
    }
    return null;
  }
}
