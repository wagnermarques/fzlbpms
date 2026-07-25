import { Component, ViewChild, HostListener, computed, inject } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { FixedHead } from './components/layout/fixed-head/fixed-head';
import { FixedStatusbar } from './components/layout/fixed-statusbar/fixed-statusbar';
import { LoginDialog } from './components/widgets/login-dialog/login-dialog';
import { AuthService } from './services/auth';

import { MatSidenavModule, MatDrawer } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatDialog } from '@angular/material/dialog';

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
  private dialog = inject(MatDialog);
  currentUser = computed(() => this.auth.currentUser()?.name ?? null);

  constructor() {
    this.checkScreenSize();
  }

  login(): void {
    const dialogRef = this.dialog.open(LoginDialog, { width: '360px' });
    const instance = dialogRef.componentInstance;

    instance.submitCredentials.subscribe(({ username, password }) => {
      instance.submitting.set(true);
      instance.errorMessage.set(null);

      this.auth.login(username, password).subscribe({
        next: () => dialogRef.close(),
        error: () => {
          instance.submitting.set(false);
          instance.errorMessage.set('Invalid username or password.');
        },
      });
    });
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
