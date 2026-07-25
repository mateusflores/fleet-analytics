import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../services/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit {
  private readonly _formBuilder = inject(FormBuilder);
  private readonly _authService = inject(AuthService);
  private readonly _router = inject(Router);

  readonly form = this._formBuilder.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  hidePassword = true;
  isSubmitting = false;
  errorMessage: string | null = null;

  ngOnInit(): void {
    if (this._authService.isAuthenticated()) {
      void this._router.navigate(['/']);
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = null;

    this._authService.login(this.form.getRawValue()).subscribe({
      next: () => {
        void this._router.navigate(['/']);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting = false;
        if (error.status === 401 || error.status === 403) {
          this.errorMessage = 'Usuário ou senha inválidos.';
          return;
        }
        this.errorMessage = 'Não foi possível entrar. Tente novamente.';
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
