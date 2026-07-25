import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  filter,
  Observable,
  take,
  tap,
  throwError
} from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthTokens } from '../../models/auth/auth-tokens.model';
import { LoginRequest } from '../../models/auth/login-request.model';
import { RefreshTokenRequest } from '../../models/auth/refresh-token-request.model';

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';
const TOKEN_TYPE_KEY = 'tokenType';
const EXPIRES_IN_KEY = 'expiresIn';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly _httpClient = inject(HttpClient);
  private readonly _router = inject(Router);

  private _isRefreshing = false;
  private readonly _refreshSubject = new BehaviorSubject<AuthTokens | null>(null);

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  }

  login(request: LoginRequest): Observable<AuthTokens> {
    return this._httpClient
      .post<AuthTokens>(`${environment.apiBaseUrl}/auth/login`, request)
      .pipe(tap((tokens) => this.saveTokens(tokens)));
  }

  saveTokens(tokens: AuthTokens): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    localStorage.setItem(TOKEN_TYPE_KEY, tokens.tokenType);
    localStorage.setItem(EXPIRES_IN_KEY, String(tokens.expiresIn));
  }

  clearTokens(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(TOKEN_TYPE_KEY);
    localStorage.removeItem(EXPIRES_IN_KEY);
  }

  refreshTokens(): Observable<AuthTokens> {
    const refreshToken = this.getRefreshToken();

    if (!refreshToken) {
      this.logout();
      return throwError(() => new Error('Refresh token not found.'));
    }

    if (this._isRefreshing) {
      return this._refreshSubject.pipe(
        filter((tokens): tokens is AuthTokens => tokens !== null),
        take(1)
      );
    }

    this._isRefreshing = true;
    this._refreshSubject.next(null);

    const body: RefreshTokenRequest = { refreshToken };

    return this._httpClient
      .post<AuthTokens>(`${environment.apiBaseUrl}/auth/refresh`, body)
      .pipe(
        tap((tokens) => {
          this.saveTokens(tokens);
          this._isRefreshing = false;
          this._refreshSubject.next(tokens);
        }),
        catchError((error) => {
          this._isRefreshing = false;
          this._refreshSubject.next(null);
          this.logout();
          return throwError(() => error);
        })
      );
  }

  logout(): void {
    this.clearTokens();
    void this._router.navigate(['/login']);
  }
}
