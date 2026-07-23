import { Component, Input } from '@angular/core';
import { MatDrawer } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-fixed-head',
  standalone: true,
  imports: [MatToolbarModule, MatIconModule, MatButtonModule, MatTooltipModule],
  templateUrl: './fixed-head.html',
  styleUrl: './fixed-head.css',
})
export class FixedHead {
  @Input() drawer!: MatDrawer;

  constructor(readonly auth: AuthService) {}
}
