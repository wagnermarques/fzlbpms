// Imagens de documentação e utilitários de exportação
const INTEGRA_MODDLE = {
  name: 'Integra',
  prefix: 'integra',
  uri: 'http://fzlbpms.org/bpmn/integra',
  xml: { tagAlias: 'lowerCase' },
  associations: [],
  types: [
    {
      name: 'Documentavel',
      isAbstract: true,
      extends: ['bpmn:BaseElement'],
      properties: [
        { name: 'imagem', isAttr: true, type: 'String' },
        { name: 'imagemLargura', isAttr: true, type: 'Integer' },
      ],
    },
  ],
};

const Imagens = {
  LARGURA_PADRAO: 240,
  OVERLAY: 'integra-imagem',

  // Redesenha todas as imagens nos elementos
  desenhar(bpmnjs) {
    const overlays = bpmnjs.get('overlays');
    const registry = bpmnjs.get('elementRegistry');
    overlays.remove({ type: Imagens.OVERLAY });
    registry.forEach((el) => {
      if (el.type === 'label' || !el.businessObject || el.waypoints) return;
      const dataUri = el.businessObject.get('integra:imagem');
      if (!dataUri) return;
      const largura = el.businessObject.get('integra:imagemLargura') || Imagens.LARGURA_PADRAO;
      const html = document.createElement('a');
      html.className = 'integra-imagem';
      html.href = dataUri;
      html.target = '_blank';
      html.rel = 'noopener';
      html.title = 'Abrir imagem em tamanho real';
      const img = document.createElement('img');
      img.src = dataUri;
      img.alt = el.businessObject.name || el.businessObject.text || 'Imagem de documentação';
      img.style.width = `${largura}px`;
      html.appendChild(img);
      overlays.add(el, Imagens.OVERLAY, { position: { top: el.height + 8, left: 0 }, html });
    });
  },

  // Exporta SVG com imagens embutidas
  async svgComImagens(bpmnjs) {
    const { svg } = await bpmnjs.saveSVG();
    const doc = new DOMParser().parseFromString(svg, 'image/svg+xml');
    const root = doc.documentElement;
    const NS = 'http://www.w3.org/2000/svg';

    let [vx, vy, vw, vh] = root.getAttribute('viewBox').split(/[\s,]+/).map(Number);
    let minX = vx, minY = vy, maxX = vx + vw, maxY = vy + vh;
    const MARGEM = 20;

    const alvos = bpmnjs.get('elementRegistry').filter((el) =>
      el.type !== 'label' && !el.waypoints && el.businessObject && el.businessObject.get('integra:imagem'));

    for (const el of alvos) {
      const bo = el.businessObject;
      const dataUrl = bo.get('integra:imagem');
      if (!dataUrl) continue;
      const { naturalWidth, naturalHeight } = await Imagens.carregar(dataUrl);
      const largura = bo.get('integra:imagemLargura') || Imagens.LARGURA_PADRAO;
      const altura = naturalWidth ? Math.round(largura * naturalHeight / naturalWidth) : largura;
      const x = el.x, y = el.y + el.height + 8;

      const moldura = doc.createElementNS(NS, 'rect');
      Object.entries({ x, y, width: largura, height: altura, fill: '#fff', stroke: '#d9e2ec', rx: 4 })
        .forEach(([k, v]) => moldura.setAttribute(k, v));
      const img = doc.createElementNS(NS, 'image');
      Object.entries({ x, y, width: largura, height: altura, href: dataUrl, preserveAspectRatio: 'xMidYMid meet' })
        .forEach(([k, v]) => img.setAttribute(k, v));
      root.append(moldura, img);

      minX = Math.min(minX, x - MARGEM); minY = Math.min(minY, y - MARGEM);
      maxX = Math.max(maxX, x + largura + MARGEM); maxY = Math.max(maxY, y + altura + MARGEM);
    }

    const w = Math.ceil(maxX - minX), h = Math.ceil(maxY - minY);
    root.setAttribute('viewBox', `${minX} ${minY} ${w} ${h}`);
    root.setAttribute('width', w);
    root.setAttribute('height', h);
    return { svg: new XMLSerializer().serializeToString(doc), width: w, height: h };
  },

  // Rasteriza SVG em PNG
  async png(bpmnjs) {
    const { svg, width, height } = await Imagens.svgComImagens(bpmnjs);
    const escala = Math.min(2, 16000 / Math.max(width, height));
    const url = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml' }));
    try {
      const img = await Imagens.carregar(url);
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(width * escala);
      canvas.height = Math.round(height * escala);
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = '#fff';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      return await new Promise((ok, falha) =>
        canvas.toBlob((b) => (b ? ok(b) : falha(new Error('Falha ao gerar PNG'))), 'image/png'));
    } finally {
      URL.revokeObjectURL(url);
    }
  },

  carregar(src) {
    return new Promise((ok, falha) => {
      const img = new Image();
      img.onload = () => ok(img);
      img.onerror = () => falha(new Error('Não foi possível carregar a imagem'));
      img.src = src;
    });
  },

  readFileAsDataURL(file) {
    return new Promise((ok, falha) => {
      const reader = new FileReader();
      reader.onload = () => ok(reader.result);
      reader.onerror = () => falha(reader.error);
      reader.readAsDataURL(file);
    });
  }
};
