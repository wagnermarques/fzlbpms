# Orbeon Forms — Processo de Aproveitamento de Estudos (Docs 13.1 e 13.2)

Formulários XForms/XHTML para o **Orbeon Forms 2025.1** integrados ao endpoint Apache Camel (`etec_coord_processo__aproveitamento_disciplina`) para coleta de dados e geração automática dos documentos ODT/DOCX.

---

## Formulários Disponíveis

| Formulário | Aplicação / Form | Descrição |
| :--- | :--- | :--- |
| **Doc 13.1 - Parecer da Comissão** | `etec-coord / parecer-comissao` | Coleta dados da portaria, aluno, docentes da comissão, disciplinas solicitadas, parecer (deferido/indeferido) e justificativa técnica. |
| **Doc 13.2 - Despacho do Diretor** | `etec-coord / despacho-diretor` | Coleta dados da unidade escolar, aluno, curso/período, referência de folhas dos autos, componentes e despacho da direção. |
| **Processo Unificado (Docs 13.1 + 13.2)** | `etec-coord / processo-aproveitamento` | Formulário completo que permite preencher todos os campos uma única vez para emissão simultânea de ambos os documentos. |

---

## Mapeamento de Campos para o Endpoint Camel

Os formulários mapeiam diretamente para o contrato JSON esperado por:
`POST /fzlbpms/aproveitamento/generate`

```json
{
  "studentName": "Fábio Assato Rossi",
  "studentRm": "26375",
  "teacherPresident": "Wagner França Marques",
  "teacherMember1": "Ralf Gerônimo",
  "teacherMember2": "João Paulo Frias",
  "componentNames": [
    "Inglês Instrumental"
  ],
  "expedientNumber": "2026-001",
  "schoolName": "ZONA LESTE",
  "programName": "Habilitação Profissional de Técnico em Desenvolvimento de Sistemas",
  "courseName": "Curso Técnico de Desenvolvimento de Sistemas Noturno",
  "portariaDate": "15/08/2022",
  "parecerPageReference": "01",
  "decision": "deferido",
  "justification": "há compatibilidade entre as competências demonstradas na documentação e o componente curricular solicitado."
}
```

---

## Acesso no Orbeon Forms

- **Form Builder (Painel de Edição/Visualização)**:
  `https://fzlbpms.local/orbeon/fr/orbeon/builder/summary`

- **Preenchimento de Novo Parecer (Doc 13.1)**:
  `https://fzlbpms.local/orbeon/fr/etec-coord/parecer-comissao/new`

- **Preenchimento de Novo Despacho (Doc 13.2)**:
  `https://fzlbpms.local/orbeon/fr/etec-coord/despacho-diretor/new`

- **Preenchimento Unificado (Docs 13.1 + 13.2)**:
  `https://fzlbpms.local/orbeon/fr/etec-coord/processo-aproveitamento/new`

---

## Publicação / Atualização dos Formulários

Para publicar ou sincronizar alterações nos arquivos `form.xhtml` com o banco PostgreSQL do Orbeon:

```bash
python3 src-projects/orbeon-forms/publish_forms.py
```
