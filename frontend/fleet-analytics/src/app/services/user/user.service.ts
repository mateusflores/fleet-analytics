import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateUserRequest } from '../../models/user/create-user-request.model';
import { UpdateUserRequest } from '../../models/user/update-user-request.model';
import { User } from '../../models/user/user.model';
import { PaginatedResponse } from '../../responses/paginated-response.model';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly _httpClient = inject(HttpClient);
  private readonly _usersUrl = `${environment.apiBaseUrl}/users`;

  getUsers(
    field: keyof User = 'username',
    term = '',
    page = 0,
    pageSize = 9
  ): Observable<PaginatedResponse<User>> {
    return this._httpClient.get<PaginatedResponse<User>>(
      `${this._usersUrl}/search?${field}=${term}&page=${page}&size=${pageSize}`
    );
  }

  createUser(request: CreateUserRequest): Observable<User> {
    return this._httpClient.post<User>(this._usersUrl, request);
  }

  getUserByUsername(username: string): Observable<User> {
    return this._httpClient.get<User>(`${this._usersUrl}/${username}`);
  }

  updateUser(userId: string, request: UpdateUserRequest): Observable<User> {
    return this._httpClient.put<User>(`${this._usersUrl}/${userId}`, request);
  }

  enableUser(userId: string): Observable<void> {
    return this._httpClient.patch<void>(`${this._usersUrl}/${userId}/enable`, {});
  }

  disableUser(userId: string): Observable<void> {
    return this._httpClient.patch<void>(`${this._usersUrl}/${userId}/disable`, {});
  }
}
