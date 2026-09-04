import {
  Component,
  ElementRef,
  OnDestroy,
  AfterViewInit,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import { ProcessDiagramService } from '../../../services/process-diagram';

@Component({
  selector: 'app-process-diagram-view',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './process-diagram-view.html',
  styleUrl: './process-diagram-view.css',
})
export class ProcessDiagramView implements AfterViewInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private diagrams = inject(ProcessDiagramService);

  canvasRef = viewChild.required<ElementRef<HTMLDivElement>>('canvas');

  processKey = signal<string>('');
  loading = signal(true);
  errorMessage = signal<string | null>(null);

  private viewer: NavigatedViewer | null = null;

  ngAfterViewInit(): void {
    const key = this.route.snapshot.paramMap.get('key');
    if (!key) {
      this.errorMessage.set('No process definition key given in the URL.');
      this.loading.set(false);
      return;
    }
    this.processKey.set(key);

    this.viewer = new NavigatedViewer({ container: this.canvasRef().nativeElement });

    this.diagrams.getDiagramXml(key).subscribe({
      next: (xml) => this.renderDiagram(xml),
      error: () => {
        this.errorMessage.set(`Could not load the diagram for "${key}".`);
        this.loading.set(false);
      },
    });
  }

  private async renderDiagram(xml: string): Promise<void> {
    try {
      await this.viewer!.importXML(xml);
      (this.viewer!.get('canvas') as any).zoom('fit-viewport');
    } catch {
      this.errorMessage.set('The BPMN XML could not be rendered.');
    } finally {
      this.loading.set(false);
    }
  }

  ngOnDestroy(): void {
    this.viewer?.destroy();
  }
}
