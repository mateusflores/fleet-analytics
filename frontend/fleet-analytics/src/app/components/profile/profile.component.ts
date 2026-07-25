import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

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

  /** Committed values shown in the header and restored on cancel */
  savedProfile: ProfileFields = {
    fullName: 'John Anderson',
    email: 'john.anderson@company.com',
    role: 'Fleet Manager',
    company: 'Construction Corp'
  };

  isEditing = false;

  readonly profileForm = this._fb.nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    role: [''],
    company: ['']
  });

  readonly passwordForm = this._fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, this.strongPasswordValidator()]],
    confirmPassword: ['', [Validators.required, this.confirmMatchesNewPassword()]]
  });

  ngOnInit(): void {
    this.profileForm.patchValue(this.savedProfile);
    this.profileForm.controls.role.disable({ emitEvent: false });
    this.profileForm.controls.company.disable({ emitEvent: false });
    this.setUserEditableFieldsDisabled(true);

    this.passwordForm.controls.newPassword.valueChanges
      .pipe(takeUntilDestroyed(this._destroyRef))
      .subscribe(() => {
        this.passwordForm.controls.confirmPassword.updateValueAndValidity({ emitEvent: false });
      });
  }

  startEdit(): void {
    this.setUserEditableFieldsDisabled(false);
    this.isEditing = true;
  }

  cancel(): void {
    this.profileForm.patchValue(this.savedProfile);
    this.setUserEditableFieldsDisabled(true);
    this.isEditing = false;
  }

  save(): void {
    if (this.profileForm.invalid || this.profileForm.pending) {
      this.profileForm.markAllAsTouched();
      return;
    }
    const values = this.profileForm.getRawValue();
    this.savedProfile = { ...values };
    this.setUserEditableFieldsDisabled(true);
    this.isEditing = false;
  }

  updatePassword(): void {
    if (this.passwordForm.invalid || this.passwordForm.pending) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    this.passwordForm.reset();
  }

  private setUserEditableFieldsDisabled(disabled: boolean): void {
    const opts = { emitEvent: false } as const;
    if (disabled) {
      this.profileForm.controls.fullName.disable(opts);
      this.profileForm.controls.email.disable(opts);
    } 
    else {
      this.profileForm.controls.fullName.enable(opts);
      this.profileForm.controls.email.enable(opts);
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
