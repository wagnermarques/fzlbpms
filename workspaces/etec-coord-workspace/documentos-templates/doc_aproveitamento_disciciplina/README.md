# Gerador de documentos de aproveitamento de estudos

Este protótipo transforma os dois arquivos `.docx` originais em um fluxo simples:

1. um formulário coleta os dados;
2. a lista de componentes curriculares vem de um arquivo JSON;
3. os dois documentos finais são gerados automaticamente em `.docx`.

## O que foi montado

- `public/` — formulário web simples
- `data/components-curriculares.json` — lista para o seletor
- `src/prepare-templates.mjs` — cria cópias template a partir dos `.docx` originais
- `src/server.mjs` — expõe a UI e o endpoint de geração
- `src/generate-sample.mjs` — gera um exemplo sem precisar abrir o navegador

## Como usar

```bash
npm install
npm run generate:sample
npm start
```

Depois abra:

```text
http://localhost:3210
```

## Saída

- Os documentos gerados ficam em `generated/<timestamp>_<aluno>_<rm>/`
- O navegador também baixa um `.zip` com os dois `.docx`

## Campos com valor padrão

Os campos abaixo já vêm preenchidos, mas podem ser alterados no formulário:

- Etec
- Programa/Habilitação
- Curso
- Data da portaria
- Folha do parecer
- Resultado
- Justificativa

## Próximo passo para integração real

Se quiser levar isso para o stack principal:

1. mover o formulário para o Orbeon Forms;
2. manter este gerador como serviço HTTP;
3. chamar a geração a partir do envio do formulário ou de um processo no Flowable.
