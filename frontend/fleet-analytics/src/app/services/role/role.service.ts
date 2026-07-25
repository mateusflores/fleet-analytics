import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateRoleRequest } from '../../models/role/create-role-request.model';
import { Permission } from '../../models/role/permission.model';
import { Role } from '../../models/role/role.model';
import { PaginatedResponse } from '../../responses/paginated-response.model';

@Injectable({
  providedIn: 'root'
})
export class RoleService {
  private readonly _httpClient = inject(HttpClient);
  private readonly _rolesUrl = `${environment.apiBaseUrl}/roles`;
  private readonly _permissionsUrl = `${environment.apiBaseUrl}/permissions`;

  getRoles(name: string, page = 0, pageSize = 9): Observable<PaginatedResponse<Role>> {
    return this._httpClient.get<PaginatedResponse<Role>>(`${this._rolesUrl}/search?name=${name}&page=${page}&size=${pageSize}`);
  }

  getRolesList(): Observable<PaginatedResponse<Role>> {
    return this._httpClient.get<PaginatedResponse<Role>>(`${this._rolesUrl}`);
  }

  getRoleById(id: string): Observable<Role> {
    return this._httpClient.get<Role>(`${this._rolesUrl}/${id}`);
  }

  createRole(request: CreateRoleRequest): Observable<Role> {
    return this._httpClient.post<Role>(this._rolesUrl, request);
  }

  updateRole(id: string, request: CreateRoleRequest): Observable<Role> {
    return this._httpClient.put<Role>(`${this._rolesUrl}/${id}`, request);
  }

  deleteRole(id: string): Observable<void> {
    return this._httpClient.delete<void>(`${this._rolesUrl}/${id}`);
  }

  getPermissions(): Observable<Permission[]> {
    return this._httpClient.get<Permission[]>(this._permissionsUrl);
  }
}
