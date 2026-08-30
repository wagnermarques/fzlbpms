/**
 * Messages exchanged between the plugin host (extension.ts / the provider) and
 * the webview (webview/main.ts). Type-only module: imported by both bundles,
 * emitted into neither.
 */

/** Plugin host -> webview. */
export type HostMessage =
    /** Initial content, sent once the webview reports itself ready. */
    | { type: 'open'; xml: string }
    /** The underlying TextDocument changed outside the diagram (undo, editor, disk). */
    | { type: 'update'; xml: string };

/** Webview -> plugin host. */
export type WebviewMessage =
    /** bpmn-js is instantiated and listening; send the document. */
    | { type: 'ready' }
    /** The user changed the diagram; `xml` is the serialized result. */
    | { type: 'change'; xml: string }
    /** Import/export failed — surfaced as a notification by the host. */
    | { type: 'error'; message: string };
