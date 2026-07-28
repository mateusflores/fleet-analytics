import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { filter, map, startWith } from 'rxjs';
import { AuthService } from './services/auth/auth.service';

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
  private readonly _authService = inject(AuthService);
  private readonly _router = inject(Router);

  readonly isLoginRoute = toSignal(
    this._router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects.startsWith('/login')),
      startWith(this._router.url.startsWith('/login'))
    ),
    { initialValue: this._router.url.startsWith('/login') }
  );

  readonly navItems: NavItem[] = [
    {
      label: 'Home',
      icon: 'home',
      route: '/'
    },
    {
      label: 'Máquinas',
      icon: 'precision_manufacturing',
      route: '/machines'
    },
    {
      label: 'Viagens',
      icon: 'route',
      route: '/trips'
    },
    {
      label: 'Perfis',
      icon: 'badge',
      route: '/roles'
    },
    {
      label: 'Usuários',
      icon: 'group',
      route: '/users'
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
    this._authService.logout();
  }
}
