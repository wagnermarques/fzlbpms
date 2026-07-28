import { Component, Input, Output, EventEmitter } from '@angular/core';
import { MatDrawer } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { UserStatus } from '../../widgets/user-status/user-status';

@Component({
  selector: 'app-fixed-head',
  standalone: true,
  imports: [MatToolbarModule, MatIconModule, MatButtonModule, UserStatus],
  templateUrl: './fixed-head.html',
  styleUrl: './fixed-head.css',
})
export class FixedHead {
  @Input() drawer!: MatDrawer;
  @Input() userName: string | null = null;
  @Output() login = new EventEmitter<void>();
  @Output() logout = new EventEmitter<void>();
}
