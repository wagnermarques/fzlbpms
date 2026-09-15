import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  AfterViewInit,
  inject,
  signal,
  viewChild,
  ChangeDetectorRef,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatCardModule } from '@angular/material/card';
import { MatBadgeModule } from '@angular/material/badge';
import { MatTooltipModule } from '@angular/material/tooltip';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import {
  BpmsProcessService,
  ProcessDefinition,
  ProcessInstance,
  FlowableTask,
  ExecutedActivity,
} from '../../../services/bpms-process.service';
import { AuthService } from '../../../services/auth';

interface SubProcessInfo {
  id: string;
  name: string;
}

interface ErrorItem {
  activityId: string;
  status: 'error' | 'conflict';
  message: string;
}

@Component({
  selector: 'app-process-diagram-view',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatTabsModule,
    MatCardModule,
    MatBadgeModule,
    MatTooltipModule,
  ],
  templateUrl: './process-diagram-view.html',
  styleUrl: './process-diagram-view.css',
})
export class ProcessDiagramView implements OnInit, AfterViewInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private bpmsService = inject(BpmsProcessService);
  public auth = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);

  canvasRef = viewChild.required<ElementRef<HTMLDivElement>>('canvas');

  // Definitions and Instances state
  definitions = signal<ProcessDefinition[]>([]);
  selectedProcessKey = signal<string>('');
  selectedProcessDefinition = signal<ProcessDefinition | null>(null);

  runningInstances = signal<ProcessInstance[]>([]);
  historicInstances = signal<ProcessInstance[]>([]);
  tasks = signal<FlowableTask[]>([]);
  selectedInstance = signal<ProcessInstance | null>(null);

  activeTab = signal<'running' | 'historic'>('running');
  filterQuery = signal<string>('');

  loading = signal(true);
  loadingInstances = signal(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Diagram & Highlights state
  private viewer: NavigatedViewer | null = null;
  private rawDefinitionXml = '';
  subProcessInfos: SubProcessInfo[] = [];
  subProcessParentMap: Record<string, string> = {};
  collapsedSubProcessIds = new Set<string>();

  highlightedIds: string[] = [];
  highlightedPathIds: string[] = [];
  highlightedErrorIds: { id: string; cls: string }[] = [];
  activeErrors = signal<ErrorItem[]>([]);

  // Auto-refresh timer
  autoRefreshEnabled = signal(false);
  private autoRefreshTimer: any = null;

  // Start Instance Modal
  showStartModal = signal(false);
  startProcessKey = '';
  startBusinessKey = '';
  startProcessName = '';
  startVariablesJson = '{\n  "projectName": "Desenvolvimento BPMS",\n  "assignee": "fzlbpmsadmin",\n  "issueTitle": "Nova Tarefa"\n}';
  startingProcess = signal(false);

  // Variables Details Modal
  showVariablesModal = signal(false);
  instanceVariables = signal<any[]>([]);

  ngOnInit(): void {
    if (!this.auth.currentUser()) {
      this.auth.login();
      return;
    }
  }

  ngAfterViewInit(): void {
    this.viewer = new NavigatedViewer({
      container: this.canvasRef().nativeElement,
    });

    ((this.viewer as any).get('eventBus') as any).on('element.click', (e: any) => {
      const el = e.element;
      if (el && el.businessObject && el.businessObject.$type === 'bpmn:SubProcess') {
        this.toggleSubProcess(el.id);
      }
    });

    this.loadDefinitionsAndInitialDiagram();
    this.loadInstancesAndTasks();
  }

  loadDefinitionsAndInitialDiagram(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    const initialKey = this.route.snapshot.paramMap.get('key') || this.route.snapshot.queryParamMap.get('processDefinitionKey');

    this.bpmsService.getDefinitions().subscribe({
      next: (defs) => {
        this.definitions.set(defs);
        if (defs.length > 0) {
          let chosen = defs.find((d) => d.key === initialKey);
          if (!chosen) {
            chosen = defs[0];
          }
          this.selectedProcessKey.set(chosen.key);
          this.selectedProcessDefinition.set(chosen);
          this.loadDiagram(chosen.key);
        } else {
          // Fallback to route key or default if definitions list is empty
          const fallbackKey = initialKey || 'create-moodle-user';
          this.selectedProcessKey.set(fallbackKey);
          this.loadDiagram(fallbackKey);
        }
      },
      error: (err) => {
        const fallbackKey = initialKey || 'create-moodle-user';
        this.selectedProcessKey.set(fallbackKey);
        this.loadDiagram(fallbackKey);
      },
    });
  }

  onProcessDefinitionChange(key: string): void {
    this.selectedProcessKey.set(key);
    const found = this.definitions().find((d) => d.key === key);
    this.selectedProcessDefinition.set(found || null);
    this.loadDiagram(key);
  }

  loadDiagram(key: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.clearHighlights();

    this.bpmsService.getDiagramXml(key).subscribe({
      next: (xml) => {
        this.rawDefinitionXml = xml;
        this.subProcessInfos = this.parseSubProcessInfos(xml);
        this.subProcessParentMap = this.buildSubProcessParentMap(xml);
        this.collapsedSubProcessIds = new Set();
        this.renderDiagram(false).then(() => {
          this.loading.set(false);
          const initialInstanceId = this.route.snapshot.queryParamMap.get('processInstanceId');
          if (initialInstanceId) {
            this.selectInstanceById(initialInstanceId);
          }
        });
      },
      error: () => {
        this.errorMessage.set(`Não foi possível carregar o diagrama BPMN para o processo "${key}".`);
        this.loading.set(false);
      },
    });
  }

  loadInstancesAndTasks(): void {
    this.loadingInstances.set(true);

    this.bpmsService.getRunningInstances().subscribe({
      next: (running) => {
        this.runningInstances.set(running);
        this.loadingInstances.set(false);
        this.enrichRunningInstances(running);
      },
      error: () => {
        this.loadingInstances.set(false);
      },
    });

    this.bpmsService.getHistoricInstances().subscribe({
      next: (historic) => {
        this.historicInstances.set(historic);
      },
      error: () => {},
    });

    this.bpmsService.getTasks().subscribe({
      next: (tasks) => {
        this.tasks.set(tasks);
      },
      error: () => {},
    });
  }

  private enrichRunningInstances(instances: ProcessInstance[]): void {
    instances.forEach((inst) => {
      this.bpmsService.getCurrentActivities(inst.id).subscribe({
        next: (activities) => {
          inst.currentActivities = activities;
          this.cdr.markForCheck();
        },
      });

      this.bpmsService.getInstanceVariables(inst.id).subscribe({
        next: (vars) => {
          inst.variables = vars;
          // Check for errors or conflicts in variable response bodies
          for (const v of vars) {
            if (v.name && v.name.endsWith('ResponseBody') && typeof v.value === 'string') {
              try {
                const parsed = JSON.parse(v.value);
                if (parsed.status === 'error') {
                  inst.hasError = true;
                  inst.errorMessage = parsed.message;
                } else if (parsed.status === 'conflict') {
                  inst.hasConflict = true;
                  inst.errorMessage = parsed.message;
                }
              } catch (e) {}
            }
          }
          this.cdr.markForCheck();
        },
      });
    });
  }

  filteredRunningInstances(): ProcessInstance[] {
    const q = this.filterQuery().toLowerCase().trim();
    const list = this.runningInstances();
    if (!q) return list;
    return list.filter(
      (i) =>
        i.id.toLowerCase().includes(q) ||
        (i.businessKey && i.businessKey.toLowerCase().includes(q)) ||
        (i.name && i.name.toLowerCase().includes(q)) ||
        (i.processDefinitionKey && i.processDefinitionKey.toLowerCase().includes(q))
    );
  }

  filteredHistoricInstances(): ProcessInstance[] {
    const q = this.filterQuery().toLowerCase().trim();
    const list = this.historicInstances();
    if (!q) return list;
    return list.filter(
      (i) =>
        i.id.toLowerCase().includes(q) ||
        (i.businessKey && i.businessKey.toLowerCase().includes(q)) ||
        (i.name && i.name.toLowerCase().includes(q)) ||
        (i.processDefinitionKey && i.processDefinitionKey.toLowerCase().includes(q))
    );
  }

  selectInstance(inst: ProcessInstance): void {
    this.selectedInstance.set(inst);
    const key = inst.processDefinitionKey || (inst.processDefinitionId ? inst.processDefinitionId.split(':')[0] : null);
    if (key && key !== this.selectedProcessKey()) {
      this.selectedProcessKey.set(key);
      this.loadDiagram(key);
    } else {
      this.refreshInstanceHighlights(inst.id);
    }
  }

  selectInstanceById(id: string): void {
    const found =
      this.runningInstances().find((i) => i.id === id) ||
      this.historicInstances().find((i) => i.id === id);
    if (found) {
      this.selectInstance(found);
    } else {
      this.refreshInstanceHighlights(id);
    }
  }

  refreshInstanceHighlights(instanceId: string): void {
    this.clearHighlights();

    // 1. Fetch executed path (history)
    this.bpmsService.getExecutedPath(instanceId).subscribe({
      next: (activities) => {
        this.applyExecutedPath(activities);
      },
    });

    // 2. Fetch current active activities
    this.bpmsService.getCurrentActivities(instanceId).subscribe({
      next: (activityIds) => {
        this.applyActiveHighlights(activityIds);
      },
    });

    // 3. Fetch variables to check for errors/conflicts
    this.bpmsService.getInstanceVariables(instanceId).subscribe({
      next: (vars) => {
        const errors: ErrorItem[] = [];
        for (const v of vars) {
          if (v.name && v.name.endsWith('ResponseBody') && typeof v.value === 'string') {
            try {
              const body = JSON.parse(v.value);
              if (body.status === 'error' || body.status === 'conflict') {
                errors.push({
                  activityId: v.name.replace('ResponseBody', ''),
                  status: body.status,
                  message: body.message || 'Falha na execução',
                });
              }
            } catch (e) {}
          }
        }
        this.activeErrors.set(errors);
        this.applyErrors(errors);
      },
    });
  }

  // --- BPMN Highlighting & Rendering logic ---

  private parseSubProcessInfos(xml: string): SubProcessInfo[] {
    const doc = new DOMParser().parseFromString(xml, 'text/xml');
    return Array.from(doc.getElementsByTagNameNS('*', 'subProcess')).map((el) => ({
      id: el.getAttribute('id') || '',
      name: el.getAttribute('name') || el.getAttribute('id') || '',
    }));
  }

  private buildSubProcessParentMap(xml: string): Record<string, string> {
    const doc = new DOMParser().parseFromString(xml, 'text/xml');
    const map: Record<string, string> = {};
    function walk(el: Element, parentSubProcessId: string | null) {
      Array.from(el.children || []).forEach((child) => {
        const id = child.getAttribute && child.getAttribute('id');
        if (id && parentSubProcessId) {
          map[id] = parentSubProcessId;
        }
        walk(child as Element, child.localName === 'subProcess' ? id : parentSubProcessId);
      });
    }
    const processEl = doc.getElementsByTagNameNS('*', 'process')[0];
    if (processEl) {
      walk(processEl, null);
    }
    return map;
  }

  private buildDisplayXml(xml: string, collapsedIds: Set<string>): string {
    const doc = new DOMParser().parseFromString(xml, 'text/xml');
    Array.from(doc.getElementsByTagNameNS('*', 'BPMNShape')).forEach((shape) => {
      const refId = shape.getAttribute('bpmnElement');
      if (refId && this.subProcessInfos.some((info) => info.id === refId)) {
        shape.setAttribute('isExpanded', collapsedIds.has(refId) ? 'false' : 'true');
      }
    });
    return new XMLSerializer().serializeToString(doc);
  }

  private resolveVisibleElementId(id: string): string | null {
    if (!this.viewer) return null;
    const elementRegistry = this.viewer.get('elementRegistry') as any;
    let current: string | null = id;
    const seen = new Set<string>();
    while (current && !seen.has(current)) {
      if (elementRegistry.get(current)) {
        return current;
      }
      seen.add(current);
      current = this.subProcessParentMap[current] || null;
    }
    return null;
  }

  toggleSubProcess(id: string): void {
    if (this.collapsedSubProcessIds.has(id)) {
      this.collapsedSubProcessIds.delete(id);
    } else {
      this.collapsedSubProcessIds.add(id);
    }
    this.renderDiagram(true);
  }

  toggleAllSubProcesses(): void {
    const allCollapsed = this.collapsedSubProcessIds.size === this.subProcessInfos.length;
    this.collapsedSubProcessIds = allCollapsed
      ? new Set()
      : new Set(this.subProcessInfos.map((i) => i.id));
    this.renderDiagram(true);
  }

  private async renderDiagram(preserveViewbox: boolean): Promise<void> {
    if (!this.viewer) return;
    const canvas = this.viewer.get('canvas') as any;
    const viewbox = preserveViewbox ? canvas.viewbox() : null;
    const displayXml = this.buildDisplayXml(this.rawDefinitionXml, this.collapsedSubProcessIds);

    await this.viewer.importXML(displayXml);
    if (viewbox) {
      canvas.viewbox(viewbox);
    } else {
      canvas.zoom('fit-viewport');
    }

    if (this.selectedInstance()) {
      this.refreshInstanceHighlights(this.selectedInstance()!.id);
    }
  }

  clearHighlights(): void {
    if (!this.viewer) return;
    const canvas = this.viewer.get('canvas') as any;

    this.highlightedIds.forEach((id) => {
      try {
        canvas.removeMarker(id, 'highlight-active');
      } catch (e) {}
    });
    this.highlightedIds = [];

    this.highlightedPathIds.forEach((id) => {
      try {
        canvas.removeMarker(id, 'highlight-executed');
      } catch (e) {}
    });
    this.highlightedPathIds = [];

    this.highlightedErrorIds.forEach(({ id, cls }) => {
      try {
        canvas.removeMarker(id, cls);
      } catch (e) {}
    });
    this.highlightedErrorIds = [];
    this.activeErrors.set([]);
  }

  applyActiveHighlights(activityIds: string[]): void {
    if (!this.viewer) return;
    const canvas = this.viewer.get('canvas') as any;
    const resolvedIds = new Set<string>();

    activityIds.forEach((id) => {
      const visibleId = this.resolveVisibleElementId(id);
      if (visibleId) {
        resolvedIds.add(visibleId);
      }
    });

    resolvedIds.forEach((id) => {
      try {
        canvas.addMarker(id, 'highlight-active');
        this.highlightedIds.push(id);
      } catch (e) {}
    });
  }

  applyExecutedPath(activities: ExecutedActivity[]): void {
    if (!this.viewer) return;
    const canvas = this.viewer.get('canvas') as any;
    const finishedIds = new Set<string>();

    activities.forEach((act) => {
      if (act.finished) {
        const visibleId = this.resolveVisibleElementId(act.activityId);
        if (visibleId) {
          finishedIds.add(visibleId);
        }
      }
    });

    finishedIds.forEach((id) => {
      try {
        canvas.addMarker(id, 'highlight-executed');
        this.highlightedPathIds.push(id);
      } catch (e) {}
    });
  }

  applyErrors(errors: ErrorItem[]): void {
    if (!this.viewer) return;
    const canvas = this.viewer.get('canvas') as any;

    errors.forEach((err) => {
      const cls = err.status === 'conflict' ? 'highlight-conflict' : 'highlight-error';
      const visibleId = this.resolveVisibleElementId(err.activityId);
      if (visibleId) {
        try {
          canvas.addMarker(visibleId, cls);
          this.highlightedErrorIds.push({ id: visibleId, cls });
        } catch (e) {}
      }
    });
  }

  // --- Zoom Controls ---

  zoomIn(): void {
    if (!this.viewer) return;
    const canvas = this.viewer.get('canvas') as any;
    canvas.zoom(canvas.zoom() * 1.2);
  }

  zoomOut(): void {
    if (!this.viewer) return;
    const canvas = this.viewer.get('canvas') as any;
    canvas.zoom(canvas.zoom() * 0.8);
  }

  zoomFit(): void {
    if (!this.viewer) return;
    const canvas = this.viewer.get('canvas') as any;
    canvas.zoom('fit-viewport');
  }

  // --- Auto-refresh ---

  toggleAutoRefresh(): void {
    const newState = !this.autoRefreshEnabled();
    this.autoRefreshEnabled.set(newState);

    if (this.autoRefreshTimer) {
      clearInterval(this.autoRefreshTimer);
      this.autoRefreshTimer = null;
    }

    if (newState) {
      this.autoRefreshTimer = setInterval(() => {
        this.loadInstancesAndTasks();
        if (this.selectedInstance()) {
          this.refreshInstanceHighlights(this.selectedInstance()!.id);
        }
      }, 3000);
    }
  }

  // --- Instance Actions ---

  toggleInstanceStatus(inst: ProcessInstance, event: Event): void {
    event.stopPropagation();
    const action = inst.suspended ? 'activate' : 'suspend';
    this.bpmsService.changeInstanceStatus(inst.id, action).subscribe({
      next: () => {
        inst.suspended = !inst.suspended;
        this.successMessage.set(`Instância #${inst.id} ${action === 'activate' ? 'reativada' : 'suspensa'} com sucesso.`);
        setTimeout(() => this.successMessage.set(null), 4000);
        this.loadInstancesAndTasks();
      },
      error: (err) => {
        this.errorMessage.set(`Erro ao alterar status da instância #${inst.id}: ${err.message}`);
        setTimeout(() => this.errorMessage.set(null), 5000);
      },
    });
  }

  deleteInstance(inst: ProcessInstance, event: Event): void {
    event.stopPropagation();
    if (!confirm(`Tem certeza que deseja cancelar/remover a instância #${inst.id}?`)) {
      return;
    }

    this.bpmsService.deleteInstance(inst.id).subscribe({
      next: () => {
        this.successMessage.set(`Instância #${inst.id} cancelada com sucesso.`);
        setTimeout(() => this.successMessage.set(null), 4000);
        if (this.selectedInstance()?.id === inst.id) {
          this.selectedInstance.set(null);
          this.clearHighlights();
        }
        this.loadInstancesAndTasks();
      },
      error: (err) => {
        this.errorMessage.set(`Erro ao cancelar instância #${inst.id}: ${err.message}`);
        setTimeout(() => this.errorMessage.set(null), 5000);
      },
    });
  }

  openStartModal(): void {
    this.startProcessKey = this.selectedProcessKey() || (this.definitions()[0]?.key || 'create-moodle-user');
    this.startBusinessKey = `Proc-${Date.now().toString().slice(-4)}`;
    this.showStartModal.set(true);
  }

  closeStartModal(): void {
    this.showStartModal.set(false);
  }

  submitStartInstance(): void {
    this.startingProcess.set(true);
    let vars: any[] = [];
    try {
      if (this.startVariablesJson.trim()) {
        const parsed = JSON.parse(this.startVariablesJson);
        vars = Object.keys(parsed).map((k) => ({ name: k, value: parsed[k] }));
      }
    } catch (e: any) {
      alert(`JSON de variáveis inválido: ${e.message}`);
      this.startingProcess.set(false);
      return;
    }

    const payload = {
      processDefinitionKey: this.startProcessKey,
      businessKey: this.startBusinessKey,
      variables: vars,
    };

    this.bpmsService.startProcessInstance(payload).subscribe({
      next: (res) => {
        this.startingProcess.set(false);
        this.showStartModal.set(false);
        this.successMessage.set(`Processo iniciado com sucesso! ID #${res.id || ''}`);
        setTimeout(() => this.successMessage.set(null), 4000);
        this.loadInstancesAndTasks();
        if (res.id) {
          this.selectInstanceById(res.id);
        }
      },
      error: (err) => {
        this.startingProcess.set(false);
        alert(`Erro ao iniciar processo: ${err.message}`);
      },
    });
  }

  viewVariables(inst: ProcessInstance, event: Event): void {
    event.stopPropagation();
    this.instanceVariables.set(inst.variables || []);
    this.showVariablesModal.set(true);
  }

  closeVariablesModal(): void {
    this.showVariablesModal.set(false);
  }

  formatDuration(millis?: number | null): string {
    if (!millis) return '-';
    const seconds = Math.floor(millis / 1000);
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    const remSec = seconds % 60;
    return `${minutes}m ${remSec}s`;
  }

  formatDate(dateStr?: string | null): string {
    if (!dateStr) return '-';
    try {
      const d = new Date(dateStr);
      return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) + ' ' + d.toLocaleDateString('pt-BR');
    } catch {
      return dateStr;
    }
  }

  ngOnDestroy(): void {
    if (this.autoRefreshTimer) {
      clearInterval(this.autoRefreshTimer);
    }
    this.viewer?.destroy();
  }
}
