import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth/auth.service';

export interface PermissionGuardOptions {
  requireAll?: boolean;
}

export function permissionGuard(
  permissions: string | string[],
  options: PermissionGuardOptions = {}
): CanActivateFn {
  const required = Array.isArray(permissions) ? permissions : [permissions];

  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }

    const hasAccess = options.requireAll
      ? authService.hasAllPermissions(...required)
      : authService.hasAnyPermission(...required);

    return hasAccess ? true : router.createUrlTree(['/dashboard']);
  };
}
