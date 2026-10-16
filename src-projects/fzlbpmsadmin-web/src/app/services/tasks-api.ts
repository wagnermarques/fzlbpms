import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface PendingTask {
  id: string;
  name: string;
  description: string | null;
  createTime: string | null;
  dueDate: string | null;
  processInstanceId: string;
  formKey: string | null;
  /** True when formKey names a published Orbeon form (app/form). */
  hasOrbeonForm: boolean;
  /** Where to send the user: the Orbeon form, or Flowable's own task UI. */
  openUrl: string;
}

@Injectable({ providedIn: 'root' })
export class TasksApi {
  private http = inject(HttpClient);

  /** Tasks assigned to the signed-in user. The username comes from the token
   * server-side (kc-authorize-user), never from the browser, so this can only
   * ever return the caller's own tasks. */
  listMyTasks(): Observable<PendingTask[]> {
    return this.http.get<PendingTask[]>(`${environment.apiUrl}/tasks`);
  }
}
