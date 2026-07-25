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
import { authGuard } from './guards/auth.guard';

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
        component: MachineListComponent
      },
      {
        path: 'machines/details/:id',
        component: MachineDetailsComponent
      },
      {
        path: 'machines/add',
        component: AddMachineComponent
      },
      {
        path: 'roles',
        component: RoleListComponent
      },
      {
        path: 'roles/add',
        component: RoleFormComponent
      },
      {
        path: 'roles/edit/:id',
        component: RoleFormComponent
      },
      {
        path: 'users',
        component: UserListComponent
      },
      {
        path: 'users/add',
        component: UserFormComponent
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
