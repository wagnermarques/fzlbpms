// esbuild turns these imports into bundled CSS (see build.mjs); TypeScript only
// needs to be told they resolve to something.
declare module '*.css';
