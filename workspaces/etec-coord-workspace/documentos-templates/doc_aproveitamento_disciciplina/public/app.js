const form = document.querySelector('#generator-form');
const componentSelect = document.querySelector('#componentNames');
const statusNode = document.querySelector('#status');

async function loadComponents() {
  const response = await fetch('/api/components');
  const components = await response.json();

  for (const component of components) {
    const option = document.createElement('option');
    option.value = component;
    option.textContent = component;
    componentSelect.appendChild(option);
  }
}

function getSelectedComponents() {
  return Array.from(componentSelect.selectedOptions).map((option) => option.value);
}

function setStatus(message, isError = false) {
  statusNode.textContent = message;
  statusNode.className = isError ? 'error' : 'success';
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  setStatus('Gerando documentos...');

  const formData = new FormData(form);
  const payload = Object.fromEntries(formData.entries());
  payload.componentNames = getSelectedComponents();

  if (payload.componentNames.length === 0) {
    setStatus('Selecione ao menos um componente curricular.', true);
    return;
  }

  const response = await fetch('/api/generate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const error = await response.json();
    setStatus(error.error || 'Falha ao gerar documentos.', true);
    return;
  }

  const blob = await response.blob();
  const fileName =
    response.headers.get('Content-Disposition')?.match(/filename="(.+)"/)?.[1] ||
    'documentos-aproveitamento.zip';
  const outputDir = response.headers.get('X-Generated-Output-Dir');
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);

  setStatus(`Documentos gerados. Arquivos também salvos em ${outputDir}.`);
});

loadComponents().catch((error) => {
  setStatus(error.message || 'Falha ao carregar a lista de componentes.', true);
});
