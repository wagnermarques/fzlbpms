// Local storage management for diagrams in fzl-bpmn-drawer
const Storage = {
  KEY: 'fzlbpms_diagrams',

  getAll() {
    try {
      const data = localStorage.getItem(Storage.KEY);
      return data ? JSON.parse(data) : [];
    } catch (e) {
      console.error('Failed to load diagrams from localStorage', e);
      return [];
    }
  },

  get(id) {
    const list = Storage.getAll();
    return list.find(d => String(d.id) === String(id)) || null;
  },

  save(diagram) {
    const list = Storage.getAll();
    const now = new Date().toISOString();
    let existingIndex = list.findIndex(d => String(d.id) === String(diagram.id));

    if (existingIndex >= 0) {
      diagram.updatedAt = now;
      diagram.version = (list[existingIndex].version || 1) + 1;
      list[existingIndex] = { ...list[existingIndex], ...diagram };
    } else {
      diagram.id = diagram.id || 'd_' + Date.now().toString(36) + Math.random().toString(36).substr(2, 5);
      diagram.createdAt = now;
      diagram.updatedAt = now;
      diagram.version = 1;
      list.unshift(diagram);
    }

    localStorage.setItem(Storage.KEY, JSON.stringify(list));
    return diagram;
  },

  delete(id) {
    const list = Storage.getAll().filter(d => String(d.id) !== String(id));
    localStorage.setItem(Storage.KEY, JSON.stringify(list));
  }
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
