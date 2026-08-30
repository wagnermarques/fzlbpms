import fsSync from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import Docxtemplater from 'docxtemplater';
import PizZip from 'pizzip';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const workspaceDir = path.resolve(__dirname, '..');
export const templatesDir = path.join(workspaceDir, 'templates');
export const generatedDir = path.join(workspaceDir, 'generated');
export const componentsPath = path.join(workspaceDir, 'data', 'components-curriculares.json');

const originalTemplates = {
  parecer: path.join(
    workspaceDir,
    '02_Doc_13_1_anexo_I_Parecer_da_Comissão_FabioAssatoRossi_RM26375.docx',
  ),
  despacho: path.join(
    workspaceDir,
    '06 Doc 13.2_anexo II - Despacho-FabioAssatoRossi-26375.docx',
  ),
};

const renderedTemplates = {
  parecer: path.join(templatesDir, 'parecer.template.docx'),
  despacho: path.join(templatesDir, 'despacho.template.docx'),
};

const DEFAULTS = {
  expedientNumber: '',
  schoolName: 'ZONA LESTE',
  programName: 'Habilitação Profissional de Técnico em Desenvolvimento de Sistemas',
  courseName: 'Curso Técnico de Desenvolvimento de Sistemas Noturno',
  portariaDate: '15/08/2022',
  parecerPageReference: '01',
  decision: 'deferido',
  justification:
    'há compatibilidade entre as competências demonstradas na documentação e o componente curricular solicitado.',
};

function countOccurrences(text, fragment) {
  return text.split(fragment).length - 1;
}

function replaceExact(text, search, replacement, label, expectedCount = 1) {
  const occurrences = countOccurrences(text, search);
  if (occurrences !== expectedCount) {
    throw new Error(
      `Template mismatch while replacing ${label}: expected ${expectedCount} occurrence(s), found ${occurrences}.`,
    );
  }

  return text.replace(search, replacement);
}

function replaceRegexOnce(text, regex, replacement, label) {
  const matches = text.match(regex) ?? [];
  if (matches.length !== 1) {
    throw new Error(
      `Template mismatch while replacing ${label}: expected 1 regex match, found ${matches.length}.`,
    );
  }

  return text.replace(regex, replacement);
}

function getSingleTextRunReplacement(paragraphXml, placeholderText) {
  const paragraphProperties = paragraphXml.match(/<w:pPr>[\s\S]*?<\/w:pPr>/)?.[0];

  if (!paragraphProperties) {
    throw new Error('Unable to preserve paragraph properties while preparing template.');
  }

  return [
    '<w:p>',
    paragraphProperties,
    '<w:r>',
    '<w:rPr>',
    '<w:rFonts w:cs="Arial" w:ascii="Arial" w:hAnsi="Arial"/>',
    '<w:sz w:val="20"/>',
    '<w:szCs w:val="20"/>',
    '</w:rPr>',
    `<w:t>${placeholderText}</w:t>`,
    '</w:r>',
    '</w:p>',
  ].join('');
}

function prepareParecerXml(xml) {
  let nextXml = xml;

  nextXml = replaceExact(
    nextXml,
    '<w:t>Expediente de Atendimento de Aproveitamento de Estudo nº:</w:t>',
    '<w:t>Expediente de Atendimento de Aproveitamento de Estudo nº: {expedient_number}</w:t>',
    'parecer expedient number',
  );

  nextXml = replaceExact(
    nextXml,
    '<w:t>Fábio Assato Rossi</w:t>',
    '<w:t>{student_name}</w:t>',
    'parecer student name',
  );

  nextXml = replaceExact(
    nextXml,
    '<w:t>Habilitação Profissional de Técnico em Desenvolvimento de Sistemas</w:t>',
    '<w:t>{program_name}</w:t>',
    'parecer program name',
  );

  nextXml = replaceExact(
    nextXml,
    '<w:t>Em conformidade com a Portaria do Sr(a). Diretor(a) da Etec, no 15/08/2022</w:t>',
    '<w:t>Em conformidade com a Portaria do Sr(a). Diretor(a) da Etec, no {portaria_date}</w:t>',
    'parecer portaria date',
  );

  nextXml = replaceExact(
    nextXml,
    '<w:t>Wagner França Marques</w:t>',
    '<w:t>{teacher_president}</w:t>',
    'parecer teacher president',
  );

  nextXml = replaceExact(
    nextXml,
    '<w:t>Ralf Gerônimo</w:t>',
    '<w:t>{teacher_member_1}</w:t>',
    'parecer teacher member 1',
  );

  nextXml = replaceExact(
    nextXml,
    '<w:t>João Paulo Frias</w:t>',
    '<w:t>{teacher_member_2}</w:t>',
    'parecer teacher member 2',
  );

  nextXml = replaceRegexOnce(
    nextXml,
    /<w:p>(?:(?!<\/w:p>).)*?<w:t>Após analise documental a comissão entendeu que<\/w:t>(?:(?!<\/w:p>).)*?<\/w:p>/s,
    (paragraphXml) => getSingleTextRunReplacement(paragraphXml, '{decision_summary}'),
    'parecer decision summary paragraph',
  );

  nextXml = replaceExact(
    nextXml,
    '<w:t>Inglês Instrumental</w:t>',
    '<w:t>{components_display}</w:t>',
    'parecer components display',
  );

  return nextXml;
}

function prepareDespachoXml(xml) {
  let nextXml = xml;

  nextXml = replaceExact(
    nextXml,
    '<w:t xml:space="preserve"> 01</w:t>',
    '<w:t xml:space="preserve"> {parecer_page_reference}</w:t>',
    'despacho parecer page reference',
  );

  nextXml = replaceExact(
    nextXml,
    '<w:t xml:space="preserve">O (A) Diretor (a) da Etec  ZONA LESTE </w:t>',
    '<w:t xml:space="preserve">O (A) Diretor (a) da Etec {school_name} </w:t>',
    'despacho school name',
  );

  nextXml = replaceExact(
    nextXml,
    '<w:t>Fábio Assato Rossi</w:t>',
    '<w:t>{student_name}</w:t>',
    'despacho student name',
  );

  nextXml = replaceRegexOnce(
    nextXml,
    /<w:t xml:space="preserve"> regularmente matriculado.*?<\/w:t>/,
    '<w:t xml:space="preserve"> regularmente matriculado no (a) {course_name}</w:t>',
    'despacho course name',
  );

  nextXml = replaceExact(
    nextXml,
    '<w:t>Inglês Instrumental</w:t>',
    '<w:t>{components_display}</w:t>',
    'despacho components display',
  );

  nextXml = replaceExact(
    nextXml,
    '<w:t>deferido o aproveitamento de estudos</w:t>',
    '<w:t>{result_text}</w:t>',
    'despacho result text',
  );

  return nextXml;
}

async function prepareTemplate(sourcePath, destinationPath, transformXml) {
  const content = await fs.readFile(sourcePath);
  const zip = new PizZip(content);
  const documentXmlPath = 'word/document.xml';
  const currentXml = zip.file(documentXmlPath)?.asText();

  if (!currentXml) {
    throw new Error(`Missing ${documentXmlPath} inside ${path.basename(sourcePath)}.`);
  }

  const nextXml = transformXml(currentXml);
  zip.file(documentXmlPath, nextXml);

  const buffer = zip.generate({
    type: 'nodebuffer',
    compression: 'DEFLATE',
  });

  await fs.mkdir(path.dirname(destinationPath), { recursive: true });
  await fs.writeFile(destinationPath, buffer);
}

export async function ensureTemplatesPrepared() {
  await prepareTemplate(originalTemplates.parecer, renderedTemplates.parecer, prepareParecerXml);
  await prepareTemplate(originalTemplates.despacho, renderedTemplates.despacho, prepareDespachoXml);
}

export async function getComponents() {
  const raw = await fs.readFile(componentsPath, 'utf8');
  return JSON.parse(raw);
}

function requireString(value, label) {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(`Missing required field: ${label}.`);
  }

  return value.trim();
}

function requireStringArray(value, label) {
  if (!Array.isArray(value) || value.length === 0) {
    throw new Error(`Missing required field: ${label}.`);
  }

  const normalized = value
    .map((item) => (typeof item === 'string' ? item.trim() : ''))
    .filter(Boolean);

  if (normalized.length === 0) {
    throw new Error(`Missing required field: ${label}.`);
  }

  return normalized;
}

function sanitizeSegment(value) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9-_]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase();
}

function formatComponentsInline(componentNames) {
  if (componentNames.length === 1) {
    return componentNames[0];
  }

  return componentNames.join(', ');
}

function buildDecisionSummary(componentNames, decision, justification) {
  const inlineComponents = formatComponentsInline(componentNames);

  if (componentNames.length === 1) {
    const predicate =
      decision === 'deferido'
        ? `o componente curricular ${inlineComponents} pode ser deferido`
        : `o componente curricular ${inlineComponents} não pode ser deferido`;
    return `Após analise documental, a comissão entendeu que ${predicate}, pois ${justification}`;
  }

  const predicate =
    decision === 'deferido'
      ? `os componentes curriculares ${inlineComponents} podem ser deferidos`
      : `os componentes curriculares ${inlineComponents} não podem ser deferidos`;
  return `Após analise documental, a comissão entendeu que ${predicate}, pois ${justification}`;
}

function buildTemplateData(payload) {
  const studentName = requireString(payload.studentName, 'studentName');
  const componentNames = requireStringArray(payload.componentNames, 'componentNames');
  const decision = (payload.decision ?? DEFAULTS.decision).trim().toLowerCase();

  if (!['deferido', 'indeferido'].includes(decision)) {
    throw new Error('Field decision must be either "deferido" or "indeferido".');
  }

  const justification = requireString(payload.justification ?? DEFAULTS.justification, 'justification');

  return {
    student_name: studentName,
    expedient_number: (payload.expedientNumber ?? DEFAULTS.expedientNumber).trim(),
    program_name: (payload.programName ?? DEFAULTS.programName).trim(),
    portaria_date: (payload.portariaDate ?? DEFAULTS.portariaDate).trim(),
    teacher_president: requireString(payload.teacherPresident, 'teacherPresident'),
    teacher_member_1: requireString(payload.teacherMember1, 'teacherMember1'),
    teacher_member_2: requireString(payload.teacherMember2, 'teacherMember2'),
    components_display: componentNames.join('\n'),
    course_name: (payload.courseName ?? DEFAULTS.courseName).trim(),
    school_name: (payload.schoolName ?? DEFAULTS.schoolName).trim(),
    parecer_page_reference: (payload.parecerPageReference ?? DEFAULTS.parecerPageReference).trim(),
    result_text:
      decision === 'deferido'
        ? 'deferido o aproveitamento de estudos'
        : 'indeferido o aproveitamento de estudos',
    decision_summary: buildDecisionSummary(componentNames, decision, justification),
  };
}

function renderBuffer(templatePath, templateData) {
  const content = fsSync.readFileSync(templatePath);
  const zip = new PizZip(content);
  const doc = new Docxtemplater(zip, {
    paragraphLoop: true,
    linebreaks: true,
  });

  doc.render(templateData);

  return doc.getZip().generate({
    type: 'nodebuffer',
    compression: 'DEFLATE',
  });
}

export async function generateDocuments(payload) {
  await ensureTemplatesPrepared();

  const templateData = buildTemplateData(payload);
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const studentSlug = sanitizeSegment(payload.studentName);
  const rmSlug = sanitizeSegment(payload.studentRm ?? '');
  const folderName = [timestamp, studentSlug, rmSlug].filter(Boolean).join('_');
  const outputDir = path.join(generatedDir, folderName);

  await fs.mkdir(outputDir, { recursive: true });

  const parecerFileName = `parecer-${studentSlug || 'aluno'}${rmSlug ? `-${rmSlug}` : ''}.docx`;
  const despachoFileName = `despacho-${studentSlug || 'aluno'}${rmSlug ? `-${rmSlug}` : ''}.docx`;

  const parecerBuffer = renderBuffer(renderedTemplates.parecer, templateData);
  const despachoBuffer = renderBuffer(renderedTemplates.despacho, templateData);

  const parecerPath = path.join(outputDir, parecerFileName);
  const despachoPath = path.join(outputDir, despachoFileName);

  await Promise.all([
    fs.writeFile(parecerPath, parecerBuffer),
    fs.writeFile(despachoPath, despachoBuffer),
  ]);

  return {
    outputDir,
    files: [
      { fileName: parecerFileName, path: parecerPath, buffer: parecerBuffer },
      { fileName: despachoFileName, path: despachoPath, buffer: despachoBuffer },
    ],
  };
}
