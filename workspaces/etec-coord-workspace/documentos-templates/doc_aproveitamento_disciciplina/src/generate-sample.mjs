import fs from 'node:fs/promises';
import path from 'node:path';

import { generateDocuments, workspaceDir } from './documents.mjs';

const requestPath = path.join(workspaceDir, 'data', 'sample-request.json');
const request = JSON.parse(await fs.readFile(requestPath, 'utf8'));
const result = await generateDocuments(request);

console.log(`Generated files in ${result.outputDir}`);
for (const file of result.files) {
  console.log(`- ${file.path}`);
}
