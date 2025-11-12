import { Component, Input } from '@angular/core';
import { MatDrawer } from '@angular/material/sidenav';

import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-fixed-head',
  standalone: true,
  imports: [MatToolbarModule, MatIconModule, MatButtonModule],
  templateUrl: './fixed-head.html',
  styleUrl: './fixed-head.css',
})
export class FixedHead {
  @Input() drawer!: MatDrawer;
}
