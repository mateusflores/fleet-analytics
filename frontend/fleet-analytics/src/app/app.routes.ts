import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { authGuard } from './guards/auth.guard';
import { permissionGuard } from './guards/permission.guard';
import { HomeComponent } from './components/home/home.component';

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
        component: HomeComponent
      },
      {
        path: 'machines',
        loadComponent: () => import('./components/machine/machine-list/machine-list.component').then((m) => m.MachineListComponent),
        canActivate: [permissionGuard('vehicles:view')]
      },
      {
        path: 'machines/details/:id',
        loadComponent: () => import('./components/machine/machine-details/machine-details.component').then((m) => m.MachineDetailsComponent),
        canActivate: [permissionGuard('vehicles:view')]
      },
      {
        path: 'machines/add',
        loadComponent: () => import('./components/machine/add-machine/add-machine.component').then((m) => m.AddMachineComponent),
        canActivate: [permissionGuard('vehicles:create')]
      },
      {
        path: 'trips',
        loadComponent: () => import('./components/trip/trip-list/trip-list.component').then((m) => m.TripListComponent),
        canActivate: [permissionGuard('travel:read')]
      },
      {
        path: 'insights',
        loadComponent: () => import('./components/insight/insight-list/insight-list.component').then((m) => m.InsightListComponent),
        canActivate: [permissionGuard('analytics:view')]
      },
      {
        path: 'settings/fleet-policy',
        loadComponent: () => import('./components/fleet-policy/fleet-policy.component').then((m) => m.FleetPolicyComponent),
        canActivate: [permissionGuard('analytics:view')]
      },
      {
        path: 'roles',
        loadComponent: () => import('./components/role/role-list/role-list.component').then((m) => m.RoleListComponent),
        canActivate: [permissionGuard('roles:read')]
      },
      {
        path: 'roles/add',
        loadComponent: () => import('./components/role/role-form/role-form.component').then((m) => m.RoleFormComponent),
        canActivate: [permissionGuard('roles:create')]
      },
      {
        path: 'roles/edit/:id',
        loadComponent: () => import('./components/role/role-form/role-form.component').then((m) => m.RoleFormComponent),
        canActivate: [permissionGuard('roles:read')]
      },
      {
        path: 'users',
        loadComponent: () => import('./components/user/user-list/user-list.component').then((m) => m.UserListComponent),
        canActivate: [permissionGuard('users:read')]
      },
      {
        path: 'users/add',
        loadComponent: () => import('./components/user/user-form/user-form.component').then((m) => m.UserFormComponent),
        canActivate: [permissionGuard('users:create')]
      },
      {
        path: 'users/details/:username',
        loadComponent: () => import('./components/user/user-details/user-details.component').then((m) => m.UserDetailsComponent),
        canActivate: [permissionGuard('users:management')]
      },
      {
        path: 'profile',
        loadComponent: () => import('./components/profile/profile.component').then((m) => m.ProfileComponent)
      }
    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
