import * as vscode from 'vscode';
import type { HostMessage, WebviewMessage } from './protocol';

/**
 * Backs `.bpmn` / `.bpmn20.xml` files with a bpmn-js modeler running in a
 * webview.
 *
 * A CustomTextEditorProvider (not a CustomEditorProvider): the document stays a
 * plain TextDocument holding the BPMN XML, so dirty state, Ctrl+S, undo/redo,
 * hot exit, "Reopen With... Text Editor" and SCM diffing all keep working
 * without this plugin implementing any of them. Every diagram edit becomes a
 * WorkspaceEdit that replaces the whole document.
 */
export class BpmnEditorProvider implements vscode.CustomTextEditorProvider {
    public static readonly viewType = 'fzlsoft.bpmn-editor';

    public static register(context: vscode.ExtensionContext): vscode.Disposable {
        return vscode.window.registerCustomEditorProvider(
            BpmnEditorProvider.viewType,
            new BpmnEditorProvider(context),
            {
                // bpmn-js state (selection, viewport, its own command stack) is
                // expensive to rebuild and lives only in the webview — keep it
                // alive while the tab is in the background.
                webviewOptions: { retainContextWhenHidden: true },
                supportsMultipleEditorsPerDocument: false
            }
        );
    }

    private constructor(private readonly context: vscode.ExtensionContext) {}

    public async resolveCustomTextEditor(
        document: vscode.TextDocument,
        panel: vscode.WebviewPanel,
        _token: vscode.CancellationToken
    ): Promise<void> {
        const mediaRoot = vscode.Uri.joinPath(this.context.extensionUri, 'media');
        panel.webview.options = {
            enableScripts: true,
            localResourceRoots: [mediaRoot]
        };
        panel.webview.html = this.render(panel.webview, mediaRoot);

        const post = (message: HostMessage) => panel.webview.postMessage(message);

        // Last XML the webview sent us. onDidChangeTextDocument fires for our
        // own WorkspaceEdit too; without this guard we would echo it straight
        // back and make bpmn-js re-import on every edit, losing the selection
        // and viewport each time.
        let xmlFromWebview: string | undefined;

        const changeSubscription = vscode.workspace.onDidChangeTextDocument(event => {
            if (event.document.uri.toString() !== document.uri.toString()) {
                return;
            }
            const xml = document.getText();
            if (xml === xmlFromWebview) {
                return;
            }
            post({ type: 'update', xml });
        });
        panel.onDidDispose(() => changeSubscription.dispose());

        panel.webview.onDidReceiveMessage(async (message: WebviewMessage) => {
            switch (message.type) {
                case 'ready':
                    post({ type: 'open', xml: document.getText() });
                    break;
                case 'change':
                    xmlFromWebview = message.xml;
                    await this.replaceDocument(document, message.xml);
                    break;
                case 'error':
                    vscode.window.showErrorMessage(
                        `BPMN Editor (${document.uri.path.split('/').pop()}): ${message.message}`
                    );
                    break;
            }
        });
    }

    /**
     * Writes the diagram back as a whole-document replacement. Fine-grained
     * edits would be nicer for the diff view, but bpmn-js re-serializes the
     * entire model on every change and there is no reliable mapping from a
     * shape edit to a range in the XML.
     */
    private async replaceDocument(document: vscode.TextDocument, xml: string): Promise<void> {
        if (document.getText() === xml) {
            return;
        }
        const edit = new vscode.WorkspaceEdit();
        edit.replace(document.uri, new vscode.Range(0, 0, document.lineCount, 0), xml);
        await vscode.workspace.applyEdit(edit);
    }

    private render(webview: vscode.Webview, mediaRoot: vscode.Uri): string {
        const script = webview.asWebviewUri(vscode.Uri.joinPath(mediaRoot, 'bpmn-editor.js'));
        const style = webview.asWebviewUri(vscode.Uri.joinPath(mediaRoot, 'bpmn-editor.css'));
        const nonce = createNonce();

        // 'unsafe-inline' for styles because diagram-js writes inline <style>
        // rules at runtime; data: fonts because build.mjs inlines bpmn-font.
        const csp = [
            "default-src 'none'",
            `img-src ${webview.cspSource} data: blob:`,
            `style-src ${webview.cspSource} 'unsafe-inline'`,
            `font-src ${webview.cspSource} data:`,
            `script-src 'nonce-${nonce}'`
        ].join('; ');

        return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Security-Policy" content="${csp}">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="${style}" rel="stylesheet">
    <title>BPMN Editor</title>
</head>
<body>
    <div id="canvas"></div>
    <script nonce="${nonce}" src="${script}"></script>
</body>
</html>`;
    }
}

function createNonce(): string {
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let nonce = '';
    for (let i = 0; i < 32; i++) {
        nonce += alphabet.charAt(Math.floor(Math.random() * alphabet.length));
    }
    return nonce;
}
