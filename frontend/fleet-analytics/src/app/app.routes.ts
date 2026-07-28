import { Routes } from '@angular/router';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { MachineListComponent } from './components/machine/machine-list/machine-list.component';
import { AddMachineComponent } from './components/machine/add-machine/add-machine.component';
import { MachineDetailsComponent } from './components/machine/machine-details/machine-details.component';
import { ProfileComponent } from './components/profile/profile.component';
import { LoginComponent } from './components/login/login.component';
import { RoleListComponent } from './components/role/role-list/role-list.component';
import { RoleFormComponent } from './components/role/role-form/role-form.component';
import { UserListComponent } from './components/user/user-list/user-list.component';
import { UserFormComponent } from './components/user/user-form/user-form.component';
import { UserDetailsComponent } from './components/user/user-details/user-details.component';
import { TripListComponent } from './components/trip/trip-list/trip-list.component';
import { InsightListComponent } from './components/insight/insight-list/insight-list.component';
import { authGuard } from './guards/auth.guard';
import { permissionGuard } from './guards/permission.guard';

export const routes: Routes = [
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        component: DashboardComponent
      },
      {
        path: 'dashboard',
        component: DashboardComponent
      },
      {
        path: 'machines',
        component: MachineListComponent,
        canActivate: [permissionGuard('vehicles:view')]
      },
      {
        path: 'machines/details/:id',
        component: MachineDetailsComponent,
        canActivate: [permissionGuard('vehicles:view')]
      },
      {
        path: 'machines/add',
        component: AddMachineComponent,
        canActivate: [permissionGuard('vehicles:create')]
      },
      {
        path: 'trips',
        component: TripListComponent,
        canActivate: [permissionGuard('travel:read')]
      },
      {
        path: 'insights',
        component: InsightListComponent
      },
      {
        path: 'roles',
        component: RoleListComponent,
        canActivate: [permissionGuard('roles:read')]
      },
      {
        path: 'roles/add',
        component: RoleFormComponent,
        canActivate: [permissionGuard('roles:create')]
      },
      {
        path: 'roles/edit/:id',
        component: RoleFormComponent,
        canActivate: [permissionGuard('roles:read')]
      },
      {
        path: 'users',
        component: UserListComponent,
        canActivate: [permissionGuard('users:read')]
      },
      {
        path: 'users/add',
        component: UserFormComponent,
        canActivate: [permissionGuard('users:create')]
      },
      {
        path: 'users/details/:username',
        component: UserDetailsComponent,
        canActivate: [permissionGuard('users:management')]
      },
      {
        path: 'profile',
        component: ProfileComponent
      }
    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
