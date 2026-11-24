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
  /** Exibe o item só para quem tem a permissão (as rotas já usam o mesmo `permissionGuard`). */
  permission?: string;
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

  private readonly _navItems: NavItem[] = [
    {
      label: 'Home',
      icon: 'home',
      route: '/'
    },
    {
      label: 'Máquinas',
      icon: 'precision_manufacturing',
      route: '/machines',
      permission: 'vehicles:view'
    },
    {
      label: 'Viagens',
      icon: 'route',
      route: '/trips',
      permission: 'travel:read'
    },
    {
      label: 'Perfis',
      icon: 'badge',
      route: '/roles',
      permission: 'roles:read'
    },
    {
      label: 'Usuários',
      icon: 'group',
      route: '/users',
      permission: 'users:read'
    },
    {
      label: 'Insights',
      icon: 'lightbulb_outline',
      route: '/insights',
      permission: 'analytics:view'
    },
    {
      label: 'Políticas da frota',
      icon: 'tune',
      route: '/settings/fleet-policy',
      permission: 'analytics:view'
    },
    {
      label: 'Perfil',
      icon: 'person_outline',
      route: '/profile'
    }
  ];

  /** Recalculado a cada navegação (o login troca o token e navega), não a cada detecção de mudanças. */
  readonly navItems = toSignal(
    this._router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      startWith(null),
      map(() => this.visibleNavItems())
    ),
    { initialValue: [] as NavItem[] }
  );

  private visibleNavItems(): NavItem[] {
    const authorities = this._authService.getAuthorities();
    return this._navItems.filter((item) => !item.permission || authorities.includes(item.permission));
  }

  onLogout(event: Event): void {
    event.preventDefault();
    this._authService.logout();
  }
}
