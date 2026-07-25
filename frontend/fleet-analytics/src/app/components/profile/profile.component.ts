import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../services/auth/auth.service';
import { SnackbarService } from '../../services/snackbar/snackbar.service';

interface ProfileFields {
  fullName: string;
  email: string;
  role: string;
  company: string;
}

@Component({
  selector: 'app-profile',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css'
})
export class ProfileComponent implements OnInit {
  private readonly _fb = inject(FormBuilder);
  private readonly _destroyRef = inject(DestroyRef);
  private readonly _snackbar = inject(SnackbarService);
  private readonly _authService: AuthService = inject(AuthService);

  readonly passwordForm = this._fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, this.strongPasswordValidator()]],
    confirmPassword: ['', [Validators.required, this.confirmMatchesNewPassword()]]
  });

  ngOnInit(): void {
    this.passwordForm.controls.newPassword.valueChanges
      .pipe(takeUntilDestroyed(this._destroyRef))
      .subscribe(() => {
        this.passwordForm.controls.confirmPassword.updateValueAndValidity({ emitEvent: false });
      });
  }

  updatePassword(): void {
    if (!this.passwordForm.invalid && !this.passwordForm.pending) {
      this.passwordForm.markAllAsTouched();
      console.log("passou aqui")
      const patch = this.passwordForm.getRawValue();
      console.log(patch)
      this._authService.changePassword(patch).subscribe({
        next: () => {
          this._snackbar.success('Máquina atualizada com sucesso.');
        },
        error: (error) => {
          console.error('Error saving machine:', error);
          this._snackbar.error('Erro ao atualizar máquina. Tente novamente.');
        }
      });
    }
  }

  private confirmMatchesNewPassword(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const group = control.parent;
      if (!group) return null;
      const newPassword = group.get('newPassword');
      if (!newPassword || !control.value) return null;
      return newPassword.value === control.value ? null : { mismatch: true };
    };
  }

  private strongPasswordValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = (control.value as string) ?? '';
      if (!value) return null;
  
      const hasMinLength = value.length >= 8;
      const hasUppercase = /[A-Z]/.test(value);
      const hasLowercase = /[a-z]/.test(value);
      const hasSpecial = /[^A-Za-z0-9]/.test(value);
  
      if (hasMinLength && hasUppercase && hasLowercase && hasSpecial) return null;
  
      return {
        passwordStrength: {
          minLength: !hasMinLength,
          uppercase: !hasUppercase,
          lowercase: !hasLowercase,
          special: !hasSpecial
        }
      };
    };
  }
}
