import { Component, ChangeDetectionStrategy, OnInit, OnDestroy, signal, WritableSignal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { UnlistenFn } from '@tauri-apps/api/event';

@Component({
  selector: 'app-moodle-install-view',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSnackBarModule,
  ],
  templateUrl: './moodle-install-view.html',
  styleUrls: ['./moodle-install-view.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MoodleInstallView implements OnInit, OnDestroy {
  installForm: FormGroup;
  installationLog: WritableSignal<string> = signal('');
  installing: WritableSignal<boolean> = signal(false);
  private unlisten?: UnlistenFn;

  constructor(private fb: FormBuilder, private snackBar: MatSnackBar) {
    this.installForm = this.fb.group({
      dbType: ['pgsql', Validators.required],
      dbHost: ['fzl-postgresql', Validators.required],
      dbName: ['moodle', Validators.required],
      dbUser: ['postgres', Validators.required],
      dbPass: ['1234', Validators.required],
      dbPrefix: ['mdl_', Validators.required],
      wwwroot: ['http://localhost/moodle', Validators.required],
      adminUser: ['admin', Validators.required],
      adminPass: ['SuaSenhaAdminForte', Validators.required],
      adminEmail: ['wagnerdocri@gmail.com', [Validators.required, Validators.email]],
      fullname: ['EtecZL PPPs', Validators.required],
      shortname: ['EtecZL', Validators.required],
      moodlePath: ['', Validators.required],
      moodledataPath: ['', Validators.required],
    });
  }

  async ngOnInit(): Promise<void> {
    invoke<string>('get_fzlbpms_home').then(homePath => {
      this.installForm.patchValue({
        moodlePath: `${homePath}/src-projects/var_www/html/moodle`,
        moodledataPath: `${homePath}/src-projects/moodledata`,
      });
    }).catch(err => {
        console.error("Failed to get FZLBPMS_HOME", err);
        this.snackBar.open(`Error: Failed to determine project home directory. ${err}`, 'Close', { duration: 5000 });
    });

    this.unlisten = await listen<string>('moodle-installation-progress', (event) => {
      this.installationLog.update(log => log + event.payload + '\n');
    });
  }

  ngOnDestroy(): void {
    if (this.unlisten) {
      this.unlisten();
    }
  }

  async onSubmit() {
    if (this.installForm.valid) {
      this.installing.set(true);
      this.installationLog.set('');
      try {
        const result = await invoke('install_moodle', { config: this.installForm.value });
        this.snackBar.open('Moodle installation completed successfully!', 'Close', { duration: 3000 });
        console.log('Installation result:', result);
      } catch (error) {
        console.error('Installation failed:', error);
        this.snackBar.open(`Installation failed: ${error}`, 'Close', { duration: 5000 });
      } finally {
        this.installing.set(false);
      }
    }
  }
}
