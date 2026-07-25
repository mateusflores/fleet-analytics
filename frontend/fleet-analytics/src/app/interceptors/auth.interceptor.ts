import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const isAuthEndpoint = req.url.includes('/auth/login') || req.url.includes('/auth/refresh');

  const authReq = (() => {
    if (isAuthEndpoint) {
      return req;
    }

    const accessToken = authService.getAccessToken();
    if (!accessToken) {
      return req;
    }

    return req.clone({
      setHeaders: {
        Authorization: `Bearer ${accessToken}`
      }
    });
  })();

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if ((error.status !== 401 && error.status !== 403) || isAuthEndpoint) {
        return throwError(() => error);
      }

      return authService.refreshTokens().pipe(
        switchMap(() => {
          const newAccessToken = authService.getAccessToken();

          if (!newAccessToken) {
            authService.logout();
            return throwError(() => error);
          }

          const retryReq = req.clone({
            setHeaders: {
              Authorization: `Bearer ${newAccessToken}`
            }
          });

          return next(retryReq).pipe(
            catchError((retryError: HttpErrorResponse) => {
              if (retryError.status === 401 || retryError.status === 403) {
                authService.logout();
              }
              return throwError(() => retryError);
            })
          );
        })
      );
    })
  );
};
