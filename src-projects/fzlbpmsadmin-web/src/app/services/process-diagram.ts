import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ProcessDiagramService {
  private http = inject(HttpClient);

  // Same-origin, ungated (see containers/fzl-nginx/nginx-shared/app-server.conf
  // "location /fzlbpms/diagrams/") — returns the raw BPMN 2.0 XML for the
  // latest deployed version of the given process definition key.
  getDiagramXml(processDefinitionKey: string): Observable<string> {
    return this.http.get(`/fzlbpms/diagrams/${processDefinitionKey}`, { responseType: 'text' });
  }
}
