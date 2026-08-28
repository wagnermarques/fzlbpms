Camel Router Project for Blueprint (OSGi)
=========================================

This bundle exposes a small REST API to generate the two
"aproveitamento de estudos" DOCX files from a single JSON payload.

See also:

    README.org
    API.org
    restclient.http

Build:

    mvn test
    mvn package

Deploy in Karaf:

    osgi:install -s mvn:fzlbpms/aproveitamento-docs/1.0-SNAPSHOT

Configuration (system property or environment variable):

    fzlbpms.aproveitamento.template.parecer
    FZLBPMS_APROVEITAMENTO_TEMPLATE_PARECER

    fzlbpms.aproveitamento.template.despacho
    FZLBPMS_APROVEITAMENTO_TEMPLATE_DESPACHO

Optional:

    fzlbpms.aproveitamento.components.path
    FZLBPMS_APROVEITAMENTO_COMPONENTS_PATH

    fzlbpms.aproveitamento.output.dir
    FZLBPMS_APROVEITAMENTO_OUTPUT_DIR

REST API (Jetty port 9092):

    GET  /fzlbpms/aproveitamento/components
    POST /fzlbpms/aproveitamento/generate

Example POST body:

    {
      "studentName": "Fábio Assato Rossi",
      "studentRm": "26375",
      "teacherPresident": "Wagner França Marques",
      "teacherMember1": "Ralf Gerônimo",
      "teacherMember2": "João Paulo Frias",
      "componentNames": ["Inglês Instrumental"],
      "expedientNumber": "2026-001",
      "schoolName": "ZONA LESTE",
      "programName": "Habilitação Profissional de Técnico em Desenvolvimento de Sistemas",
      "courseName": "Curso Técnico de Desenvolvimento de Sistemas Noturno",
      "portariaDate": "15/08/2022",
      "parecerPageReference": "01",
      "decision": "deferido",
      "justification": "há compatibilidade entre as competências demonstradas na documentação e o componente curricular solicitado."
    }

Response:

    application/zip

The ZIP contains the two generated DOCX files, and the same files are also
saved to the configured output directory.
