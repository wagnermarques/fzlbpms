import * as vscode from 'vscode';
import { BpmnEditorProvider } from './bpmn-editor-provider';
import { EMPTY_BPMN_DIAGRAM } from './bpmn-template';

const BPMN_FILTERS = { 'BPMN 2.0': ['bpmn', 'bpmn2', 'bpmn20.xml', 'xml'] };

export function activate(context: vscode.ExtensionContext): void {
    context.subscriptions.push(
        BpmnEditorProvider.register(context),
        vscode.commands.registerCommand('fzlbpms.bpmn.new', newDiagram),
        vscode.commands.registerCommand('fzlbpms.bpmn.open', openDiagram)
    );
}

export function deactivate(): void {
    // Nothing to tear down: every disposable is owned by the extension context.
}

/**
 * Asks where to put the file, writes the template, then opens it in the BPMN
 * editor.
 *
 * Deliberately not an untitled document: the custom editor is registered by
 * filename pattern, and `untitled:` documents have no name to match against
 * until they are saved, so an untitled buffer would open as raw XML.
 */
async function newDiagram(): Promise<void> {
    const folder = vscode.workspace.workspaceFolders?.[0];
    const target = await vscode.window.showSaveDialog({
        title: 'New BPMN Diagram',
        saveLabel: 'Create',
        filters: BPMN_FILTERS,
        defaultUri: folder ? vscode.Uri.joinPath(folder.uri, 'diagram.bpmn') : undefined
    });
    if (!target) {
        return;
    }

    await vscode.workspace.fs.writeFile(target, Buffer.from(EMPTY_BPMN_DIAGRAM, 'utf8'));
    await vscode.commands.executeCommand('vscode.openWith', target, BpmnEditorProvider.viewType);
}

async function openDiagram(): Promise<void> {
    const picked = await vscode.window.showOpenDialog({
        title: 'Open BPMN Diagram',
        openLabel: 'Open',
        canSelectMany: false,
        filters: BPMN_FILTERS,
        defaultUri: vscode.workspace.workspaceFolders?.[0]?.uri
    });
    const target = picked?.[0];
    if (!target) {
        return;
    }

    await vscode.commands.executeCommand('vscode.openWith', target, BpmnEditorProvider.viewType);
}
