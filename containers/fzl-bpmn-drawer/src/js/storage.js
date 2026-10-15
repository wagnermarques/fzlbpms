// Server-side persistence for diagrams in fzl-bpmn-drawer (bpmndrawer REST API,
// same-origin behind nginx's oauth2-proxy gate — no token handling needed here,
// the session cookie rides along with fetch() automatically).
const Storage = {
  API_BASE: '/api/bpmndrawer/diagrams',
  LOCAL_KEY: 'fzlbpms_diagrams',
  _list: [],
  _full: new Map(),

  async init() {
    const res = await fetch(Storage.API_BASE);
    if (!res.ok) throw new Error('Falha ao carregar diagramas (HTTP ' + res.status + ')');
    Storage._list = await res.json();
  },

  getAll() {
    return Storage._list;
  },

  async get(id) {
    if (Storage._full.has(id)) return Storage._full.get(id);
    const res = await fetch(`${Storage.API_BASE}/${encodeURIComponent(id)}`);
    if (!res.ok) return null;
    const d = await res.json();
    Storage._full.set(id, d);
    return d;
  },

  async save(diagram) {
    const isUpdate = !!diagram.id;
    const res = await fetch(isUpdate ? `${Storage.API_BASE}/${encodeURIComponent(diagram.id)}` : Storage.API_BASE, {
      method: isUpdate ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: diagram.name, xml: diagram.xml, processKey: diagram.processKey }),
    });
    if (!res.ok) throw new Error('Falha ao salvar diagrama (HTTP ' + res.status + ')');
    const saved = await res.json();
    Storage._full.set(saved.id, saved);
    await Storage.init();
    return saved;
  },

  async delete(id) {
    const res = await fetch(`${Storage.API_BASE}/${encodeURIComponent(id)}`, { method: 'DELETE' });
    if (!res.ok) throw new Error('Falha ao excluir diagrama (HTTP ' + res.status + ')');
    Storage._full.delete(id);
    await Storage.init();
  },
};

// ---------- Migração única a partir do localStorage (bug de origem/domínio) ----------
// Diagramas salvos antes deste backend existir ficaram presos no localStorage
// do navegador/origem em que foram criados. Oferece importá-los uma vez.
const LocalImport = {
  pending() {
    try {
      const raw = localStorage.getItem(Storage.LOCAL_KEY);
      const list = raw ? JSON.parse(raw) : [];
      return Array.isArray(list) ? list : [];
    } catch (e) {
      return [];
    }
  },

  async importAll(localList) {
    const existingNames = new Set(Storage.getAll().map(d => d.name));
    let imported = 0;
    for (const d of localList) {
      if (existingNames.has(d.name)) continue;
      await Storage.save({ name: d.name, xml: d.xml, processKey: d.processKey });
      imported++;
    }
    localStorage.removeItem(Storage.LOCAL_KEY);
    return imported;
  },
};

function toast(msg, kind = '') {
  let el = document.getElementById('toast');
  if (!el) {
    el = document.createElement('div');
    el.id = 'toast';
    document.body.appendChild(el);
  }
  el.textContent = msg;
  el.className = kind;
  el.hidden = false;
  clearTimeout(el._t);
  el._t = setTimeout(() => { el.hidden = true; }, kind === 'error' ? 8000 : 3000);
}

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function fmtDate(d) {
  return d ? new Date(d).toLocaleString('pt-BR') : '';
}
