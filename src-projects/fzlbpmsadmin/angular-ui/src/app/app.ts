import { Component, ViewChild, HostListener, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { FixedHead } from './components/layout/fixed-head/fixed-head';
import { FixedStatusbar } from "./components/layout/fixed-statusbar/fixed-statusbar";

import { MatSidenavModule, MatDrawer } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';

/**
 * Interface for the log payload received from the Rust backend.
 */
interface LogPayload {
  level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG' | 'TRACE';
  message: string;
  timestamp: string;
}

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
    FixedStatusbar
  ],
})
export class App implements OnInit {
  @ViewChild('drawer') drawer!: MatDrawer;
  isSmallScreen = false;

  constructor() {
    this.checkScreenSize();
  }

  ngOnInit() {
    this.setupLogListener();
  }

  /**
   * Sets up a listener to receive log events from the Tauri backend
   * and prints them to the browser's developer console.
   */
  async setupLogListener() {
    await listen<LogPayload>('log-message', (event) => {
      const logPayload = event.payload;
      const logMessage = `%c[RUST]%c [${logPayload.timestamp}] [${logPayload.level}] ${logPayload.message}`;
      
      // CSS for styling the log messages in the console
      const rustTagStyle = 'background-color: #5D4037; color: white; padding: 2px 4px; border-radius: 3px;';
      const resetStyle = ''; // Resets styles for the rest of the message

      switch (logPayload.level) {
        case 'INFO':
          console.info(logMessage, rustTagStyle, resetStyle);
          break;
        case 'WARN':
          console.warn(logMessage, rustTagStyle, resetStyle);
          break;
        case 'ERROR':
          console.error(logMessage, rustTagStyle, resetStyle);
          break;
        case 'DEBUG':
          console.debug(logMessage, rustTagStyle, resetStyle);
          break;
        default:
          console.log(logMessage, rustTagStyle, resetStyle);
          break;
      }
    });
  }

  @HostListener('window:resize', ['$event'])
  onResize() {
    this.checkScreenSize();
  }
  
  toggleDrawer() {
    this.drawer.toggle();
  }

  checkScreenSize() {
    this.isSmallScreen = window.innerWidth < 768;
  }
}
