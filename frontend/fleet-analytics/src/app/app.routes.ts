import { Routes } from '@angular/router';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { MachineListComponent } from './components/machine/machine-list/machine-list.component';
import { AddMachineComponent } from './components/machine/add-machine/add-machine.component';
import { MachineDetailsComponent } from './components/machine/machine-details/machine-details.component';

export const routes: Routes = [
    {
        "path": "",
        "component": DashboardComponent
    },
    {
        "path": "dashboard",
        "component": DashboardComponent
    },
    {
        "path": "machines",
        "component": MachineListComponent
    },
    {
        "path": "machines/details/:id",
        "component": MachineDetailsComponent
    },
    {
        "path": "machines/add",
        "component": AddMachineComponent
    }
];
