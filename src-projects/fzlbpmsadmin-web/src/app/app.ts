import { Component, ViewChild, HostListener, computed, inject } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { FixedHead } from './components/layout/fixed-head/fixed-head';
import { FixedStatusbar } from './components/layout/fixed-statusbar/fixed-statusbar';
import { AuthService } from './services/auth';

import { MatSidenavModule, MatDrawer } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.css',
  imports: [
    RouterOutlet,
    RouterLink,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatDividerModule,
    FixedHead,
    FixedStatusbar,
  ],
})
export class App {
  @ViewChild('drawer') drawer!: MatDrawer;
  isSmallScreen = false;

  private auth = inject(AuthService);
  currentUser = computed(() => this.auth.currentUser()?.name ?? null);

  constructor() {
    this.checkScreenSize();
  }

  login(): void {
    this.auth.login();
  }

  logout(): void {
    this.auth.logout();
  }

  @HostListener('window:resize')
  onResize() {
    this.checkScreenSize();
  }

  checkScreenSize() {
    this.isSmallScreen = window.innerWidth < 768;
  }
}
