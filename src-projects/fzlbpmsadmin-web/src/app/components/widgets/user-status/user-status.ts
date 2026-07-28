import { Component, Input, Output, EventEmitter } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-user-status',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './user-status.html',
  styleUrl: './user-status.css',
})
export class UserStatus {
  @Input() userName: string | null = null;
  @Output() login = new EventEmitter<void>();
  @Output() logout = new EventEmitter<void>();
}
