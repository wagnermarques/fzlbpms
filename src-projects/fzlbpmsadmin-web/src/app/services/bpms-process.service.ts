import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export interface ProcessDefinition {
  id: string;
  url: string;
  key: string;
  version: number;
  name: string;
  description: string | null;
  tenantId: string;
  deploymentId: string;
  resourceName: string;
  diagramResourceName: string | null;
  category: string;
  suspended: boolean;
}

export interface ProcessInstanceVariable {
  name: string;
  type?: string;
  value: any;
  scope?: string;
}

export interface ProcessInstance {
  id: string;
  url: string;
  businessKey?: string | null;
  suspended?: boolean;
  ended?: boolean;
  processDefinitionId: string;
  processDefinitionUrl?: string;
  processDefinitionKey?: string;
  processDefinitionName?: string;
  processDefinitionVersion?: number;
  activityId?: string | null;
  startUserId?: string | null;
  startTime?: string;
  endTime?: string | null;
  durationInMillis?: number | null;
  name?: string | null;
  variables?: ProcessInstanceVariable[];
  currentActivities?: string[];
  tasks?: FlowableTask[];
  hasError?: boolean;
  hasConflict?: boolean;
  errorMessage?: string;
}

export interface FlowableTask {
  id: string;
  url?: string;
  owner?: string | null;
  assignee?: string | null;
  delegationState?: string | null;
  name: string;
  description?: string | null;
  createTime: string;
  dueDate?: string | null;
  priority?: number;
  suspended?: boolean;
  taskDefinitionKey?: string;
  scopeDefinitionId?: string;
  scopeId?: string;
  scopeType?: string;
  tenantId?: string;
  category?: string | null;
  formKey?: string | null;
  parentTaskId?: string | null;
  parentTaskUrl?: string | null;
  executionId?: string;
  executionUrl?: string;
  processInstanceId: string;
  processInstanceUrl?: string;
  processDefinitionId?: string;
  processDefinitionUrl?: string;
}

export interface ExecutedActivity {
  activityId: string;
  activityType?: string;
  activityName?: string;
  finished?: boolean;
  startTime?: string;
  endTime?: string | null;
  durationInMillis?: number | null;
}

@Injectable({ providedIn: 'root' })
export class BpmsProcessService {
  private http = inject(HttpClient);
  private baseUrl = '/fzlbpms/bpms';

  getDefinitions(): Observable<ProcessDefinition[]> {
    return this.http.get<any>(`${this.baseUrl}/definitions`).pipe(
      map(res => (res && res.data ? res.data : Array.isArray(res) ? res : []))
    );
  }

  getDiagramXml(processDefinitionKey: string): Observable<string> {
    return this.http.get(`${this.baseUrl}/process-diagram/${encodeURIComponent(processDefinitionKey)}`, {
      responseType: 'text',
    });
  }

  getRunningInstances(): Observable<ProcessInstance[]> {
    return this.http.get<any>(`${this.baseUrl}/instances`).pipe(
      map(res => (res && res.data ? res.data : Array.isArray(res) ? res : []))
    );
  }

  getHistoricInstances(): Observable<ProcessInstance[]> {
    return this.http.get<any>(`${this.baseUrl}/historic-instances`).pipe(
      map(res => (res && res.data ? res.data : Array.isArray(res) ? res : []))
    );
  }

  getCurrentActivities(instanceId: string): Observable<string[]> {
    return this.http.get<any>(`${this.baseUrl}/instances/${encodeURIComponent(instanceId)}/current-activities`).pipe(
      map(res => {
        const list = res && res.data ? res.data : Array.isArray(res) ? res : [];
        return Array.from(
          new Set(
            list
              .map((item: any) => item.activityId)
              .filter((id: any) => Boolean(id))
          )
        );
      })
    );
  }

  getExecutedPath(instanceId: string): Observable<ExecutedActivity[]> {
    return this.http.get<any>(`${this.baseUrl}/instances/${encodeURIComponent(instanceId)}/executed-path`).pipe(
      map(res => {
        const list = res && res.data ? res.data : Array.isArray(res) ? res : [];
        return list.map((item: any) => ({
          activityId: item.activityId,
          activityType: item.activityType,
          activityName: item.activityName,
          finished: Boolean(item.endTime),
          startTime: item.startTime,
          endTime: item.endTime,
          durationInMillis: item.durationInMillis,
        }));
      })
    );
  }

  getInstanceVariables(instanceId: string): Observable<ProcessInstanceVariable[]> {
    return this.http.get<any>(`${this.baseUrl}/instances/${encodeURIComponent(instanceId)}/variables`).pipe(
      map(res => (res && res.data ? res.data : Array.isArray(res) ? res : []))
    );
  }

  getTasks(): Observable<FlowableTask[]> {
    return this.http.get<any>(`${this.baseUrl}/tasks`).pipe(
      map(res => (res && res.data ? res.data : Array.isArray(res) ? res : []))
    );
  }

  startProcessInstance(payload: {
    processDefinitionKey: string;
    businessKey?: string;
    variables?: { name: string; value: any }[];
  }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/instances/start`, payload);
  }

  changeInstanceStatus(instanceId: string, action: 'suspend' | 'activate'): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/instances/${encodeURIComponent(instanceId)}/action`, { action });
  }

  deleteInstance(instanceId: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/instances/${encodeURIComponent(instanceId)}`);
  }
}
