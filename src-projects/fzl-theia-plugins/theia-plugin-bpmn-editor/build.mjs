// Bundles both halves of the plugin with esbuild:
//
//   src/extension.ts   -> dist/extension.js   (Node, runs in the plugin host)
//   src/webview/main.ts -> media/bpmn-editor.js + media/bpmn-editor.css
//                                              (browser, runs in the webview)
//
// Bundling is what lets the .vsix be packaged with `vsce --no-dependencies`:
// bpmn-js and its CSS/fonts end up inside the two output files, so no
// node_modules has to be shipped or resolved at runtime.
import { context, build } from 'esbuild';

const watch = process.argv.includes('--watch');
const minify = process.argv.includes('--minify');

/** @type {import('esbuild').BuildOptions} */
const extensionConfig = {
    entryPoints: ['src/extension.ts'],
    outfile: 'dist/extension.js',
    bundle: true,
    platform: 'node',
    format: 'cjs',
    target: 'node18',
    // Provided by the plugin host (Theia's or VS Code's) — never bundled.
    external: ['vscode'],
    sourcemap: !minify,
    minify,
    logLevel: 'info'
};

/** @type {import('esbuild').BuildOptions} */
const webviewConfig = {
    entryPoints: ['src/webview/main.ts'],
    outfile: 'media/bpmn-editor.js',
    bundle: true,
    platform: 'browser',
    format: 'iife',
    target: 'es2020',
    // The CSS imported by main.ts is emitted alongside as media/bpmn-editor.css.
    // bpmn-font is inlined as data: URIs rather than shipped as separate files:
    // webview asset URLs are rewritten by the host, and a data: URI sidesteps
    // both that and the font-src half of the Content-Security-Policy.
    loader: {
        '.woff': 'dataurl',
        '.woff2': 'dataurl',
        '.ttf': 'dataurl',
        '.eot': 'dataurl',
        '.svg': 'dataurl',
        '.png': 'dataurl'
    },
    sourcemap: !minify,
    minify,
    logLevel: 'info'
};

if (watch) {
    const contexts = await Promise.all([context(extensionConfig), context(webviewConfig)]);
    await Promise.all(contexts.map(ctx => ctx.watch()));
    console.log('[build] watching for changes...');
} else {
    await Promise.all([build(extensionConfig), build(webviewConfig)]);
}
