import BpmnModeler from 'bpmn-js/lib/Modeler';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import './style.css';
import type { HostMessage, WebviewMessage } from '../protocol';
import { EMPTY_BPMN_DIAGRAM } from '../bpmn-template';

interface VsCodeApi {
    postMessage(message: WebviewMessage): void;
}
declare function acquireVsCodeApi(): VsCodeApi;

const vscode = acquireVsCodeApi();

const modeler = new BpmnModeler({
    container: document.getElementById('canvas') as HTMLElement,
    keyboard: { bindTo: document }
});

/**
 * XML currently held by the document, as far as this webview knows. Guards both
 * directions: an `update` carrying this exact text is our own edit coming back
 * and must not trigger a re-import, and a serialization equal to it is not
 * worth sending as a change.
 */
let documentXml = '';

/** Suppresses change events emitted by importXML/createDiagram themselves. */
let importing = false;

let changeTimer: ReturnType<typeof setTimeout> | undefined;

modeler.on('commandStack.changed', () => {
    if (importing) {
        return;
    }
    // Coalesce: a single drag fires commandStack.changed many times, and each
    // one would otherwise become a serialization plus a WorkspaceEdit.
    clearTimeout(changeTimer);
    changeTimer = setTimeout(sendChange, 300);
});

window.addEventListener('message', (event: MessageEvent<HostMessage>) => {
    const message = event.data;
    if (message.type !== 'open' && message.type !== 'update') {
        return;
    }
    if (message.xml === documentXml) {
        return;
    }
    void load(message.xml);
});

// The host holds off on sending content until this arrives, so a slow bundle
// parse cannot make us miss the initial 'open'.
vscode.postMessage({ type: 'ready' });

async function load(xml: string): Promise<void> {
    importing = true;
    try {
        if (xml.trim().length === 0) {
            // Someone created the file empty (touch, `New File`) — start from
            // the template and seed the document with it right away, so the
            // file on disk stops being an unparseable empty BPMN.
            await modeler.importXML(EMPTY_BPMN_DIAGRAM);
            documentXml = xml;
            importing = false;
            await sendChange();
            return;
        }
        await modeler.importXML(xml);
        documentXml = xml;
    } catch (error) {
        vscode.postMessage({ type: 'error', message: describe(error) });
    } finally {
        importing = false;
    }
}

async function sendChange(): Promise<void> {
    try {
        const { xml } = await modeler.saveXML({ format: true });
        if (!xml || xml === documentXml) {
            return;
        }
        documentXml = xml;
        vscode.postMessage({ type: 'change', xml });
    } catch (error) {
        vscode.postMessage({ type: 'error', message: describe(error) });
    }
}

function describe(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}
