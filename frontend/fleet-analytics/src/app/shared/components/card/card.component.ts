import { Component, Input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from "@angular/material/icon";
import { TinyColor } from '@ctrl/tinycolor';

@Component({
  selector: 'app-card',
  imports: [
    MatCardModule,
    MatButtonModule,
    MatIconModule
],
  templateUrl: './card.component.html',
  styleUrl: './card.component.css'
})
export class CardComponent {

  @Input({ required: true }) icon!: string;
  @Input({ required: true }) iconColor!: string;
  @Input() title: string = '';
  @Input({ required: true }) value!: string;
  @Input() lightenFactor: number = 40;

  getIconBackgroundColor(): string {
    let color = new TinyColor(this.iconColor);
    return color.lighten(this.lightenFactor).toString();
  }
}
