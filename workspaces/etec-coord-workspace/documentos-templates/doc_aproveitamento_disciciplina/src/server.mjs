import path from 'node:path';

import archiver from 'archiver';
import express from 'express';

import { generateDocuments, getComponents, workspaceDir } from './documents.mjs';

const app = express();
const port = process.env.PORT || 3210;

app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(workspaceDir, 'public')));

app.get('/api/components', async (_req, res, next) => {
  try {
    const components = await getComponents();
    res.json(components);
  } catch (error) {
    next(error);
  }
});

app.post('/api/generate', async (req, res, next) => {
  try {
    const result = await generateDocuments(req.body);
    const fileBase = result.outputDir.split(path.sep).pop() || 'documentos';

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${fileBase}.zip"`);
    res.setHeader('X-Generated-Output-Dir', result.outputDir);

    const archive = archiver('zip', { zlib: { level: 9 } });
    archive.on('error', next);
    archive.pipe(res);

    for (const file of result.files) {
      archive.append(file.buffer, { name: file.fileName });
    }

    await archive.finalize();
  } catch (error) {
    next(error);
  }
});

app.use((error, _req, res, _next) => {
  res.status(400).json({
    error: error.message || 'Unexpected error while generating documents.',
  });
});

app.listen(port, () => {
  console.log(`Form available at http://localhost:${port}`);
});
