import { Component, Input, output } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { User } from '../../../models/user/user.model';

@Component({
  selector: 'app-user-list-card',
  imports: [MatCardModule, MatIconModule, MatButtonModule],
  templateUrl: './user-list-card.component.html',
  styleUrl: './user-list-card.component.css'
})
export class UserListCardComponent {
  @Input({ required: true }) user!: User;
  @Input() isToggling = false;

  readonly enable = output<User>();
  readonly disable = output<User>();

  onEnable(event: Event): void {
    event.stopPropagation();
    this.enable.emit(this.user);
  }

  onDisable(event: Event): void {
    event.stopPropagation();
    this.disable.emit(this.user);
  }
}
