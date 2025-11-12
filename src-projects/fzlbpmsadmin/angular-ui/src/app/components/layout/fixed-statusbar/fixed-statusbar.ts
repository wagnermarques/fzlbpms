import { Component, signal } from '@angular/core';

import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-fixed-statusbar',
  standalone: true,
  imports: [CommonModule, MatToolbarModule, MatIconModule],
  templateUrl: './fixed-statusbar.html',
  styleUrl: './fixed-statusbar.css',
})
export class FixedStatusbar {
  currentTime = signal(new Date());
  
  constructor() {
    setInterval(() => {
      this.currentTime.set(new Date());
    }, 1000);
  }
}
