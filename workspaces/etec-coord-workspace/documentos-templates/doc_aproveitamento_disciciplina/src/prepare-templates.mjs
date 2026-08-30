import { ensureTemplatesPrepared, templatesDir } from './documents.mjs';

await ensureTemplatesPrepared();
console.log(`Templates generated in ${templatesDir}`);
