import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateUserRequest } from '../../models/user/create-user-request.model';
import { User } from '../../models/user/user.model';
import { PaginatedUsersResponse } from '../../responses/paginated-users.response';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly _httpClient = inject(HttpClient);
  private readonly _usersUrl = `${environment.apiBaseUrl}/users`;

  getUsers(page = 0, pageSize = 9): Observable<PaginatedUsersResponse> {
    return this._httpClient.get<PaginatedUsersResponse>(
      `${this._usersUrl}?page=${page}&size=${pageSize}`
    );
  }

  createUser(request: CreateUserRequest): Observable<User> {
    return this._httpClient.post<User>(this._usersUrl, request);
  }

  enableUser(userId: string): Observable<void> {
    return this._httpClient.patch<void>(`${this._usersUrl}/${userId}/enable`, {});
  }

  disableUser(userId: string): Observable<void> {
    return this._httpClient.patch<void>(`${this._usersUrl}/${userId}/disable`, {});
  }
}
