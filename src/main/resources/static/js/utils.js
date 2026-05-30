// 七域逐鹿 · Web Client

function statusText(msg) { document.getElementById('status-text').textContent = msg; }
function colorizePanel(text) {
  // ANSI SGR → HTML span (single-pass regex)
  const ansiToClass = {
    '0': '', '1': 'bold', '2': 'dim',
    '30': '', '31': 'red', '32': 'green', '33': 'yellow',
    '34': 'blue', '35': 'purple', '36': 'cyan', '37': 'white',
    '90': 'dim', '91': 'red', '92': 'green', '93': 'gold',
    '94': 'blue', '95': 'purple', '96': 'cyan', '97': 'white',
    '1;30': 'dim', '1;31': 'red', '1;32': 'green', '1;33': 'yellow',
    '1;34': 'blue', '1;35': 'purple', '1;36': 'cyan', '1;37': 'white',
    '1;90': 'dim', '1;91': 'red', '1;92': 'green', '1;93': 'gold',
    '1;94': 'blue', '1;95': 'purple', '1;96': 'cyan', '1;97': 'white',
  };
  let html = escapeHtml(text);
  let openSpans = 0;
  html = html.replace(/\x1b\[([\d;]*)m/g, (match, codes) => {
    if (codes === '0' || codes === '') {
      let closes = '';
      for (let i = 0; i < openSpans; i++) closes += '</span>';
      openSpans = 0;
      return closes;
    }
    const cls = ansiToClass[codes];
    if (cls) { openSpans++; return `<span class="${cls}">`; }
    return '';
  });
  for (let i = 0; i < openSpans; i++) html += '</span>';
  return html;
}

function escapeHtml(s) {
  const d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}

function showToast(msg) {
  const toast = document.createElement('div');
  toast.textContent = msg;
  toast.style.cssText = 'position:fixed;top:60px;left:50%;transform:translateX(-50%);background:#1a3a2a;color:var(--green);padding:8px 20px;border-radius:20px;font-size:0.9em;z-index:10000;border:1px solid var(--green);pointer-events:none;transition:opacity 0.4s;';
  document.body.appendChild(toast);
  setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 400); }, 2500);
}
// ── 辅助 ──────────────────────────────────────────────────
function closeModal(id) {
  document.getElementById(id).classList.remove('show');
}

// ── API 调用 ────────────────────────────────────────────────
async function apiGet(path) {
  try {
    const r = await fetch(window.API + path);
    return await r.json();
  } catch (e) {
    return {error: e.message};
  }
}

async function apiPost(path, body={}) {
  try {
    const r = await fetch(API + path, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(body),
    });
    return await r.json();
  } catch (e) {
    return {error: e.message};
  }
}

async function refreshState() {
  const data = await apiGet('/api/state');
  if (!data.error) renderAll(data);
  else updateConnection(false);
}

async function sendAction(action) {
  document.getElementById('status-text').textContent = `执行 ${action}...`;
  const data = await apiPost('/api/action', {action});
  if (!data.error) renderAll(data);
  else alert('错误: ' + data.error);
}

