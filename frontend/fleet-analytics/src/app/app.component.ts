import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatIconModule,
    MatListModule,
    MatButtonModule
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  readonly navItems: NavItem[] = [
    {
      label: 'Dashboard',
      icon: 'grid_view',
      route: '/'
    },
    {
      label: 'Máquinas',
      icon: 'precision_manufacturing',
      route: '/machines'
    },
    {
      label: 'Insights',
      icon: 'lightbulb_outline',
      route: '/insights'
    },
    {
      label: 'Perfil',
      icon: 'person_outline',
      route: '/profile'
    }
  ];

  onLogout(event: Event): void {
    event.preventDefault();
    // Logout logic
  }
}
