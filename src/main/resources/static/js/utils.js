// 七域逐鹿 · Web Client

// ── 全局常量 ────────────────────────────────────────────────
window._STAT_ICONS = {industry:'🏭',agriculture:'🌾',military:'⚔',economy:'💰',ideology:'📖',diplomacy:'🌐',naval_power:'⚓'};
window._AI_MODES  = {local:'本地模板',deepseek:'DeepSeek',openai:'OpenAI',anthropic:'Claude'};
window._OPEN_MODAL = function(id) { var el=document.getElementById(id); if(el)el.classList.add('show'); };

function statusText(msg) { var el=document.getElementById('status-text'); if(el)el.textContent=msg; }
function colorizePanel(text) {
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

// ═══════════════════ Toast 通知系统 ═══════════════════
var _toastTimer = null;
function showToast(msg, type) {
  type = type || 'info';  // info | error | warn | success
  var colors = {
    info:    {bg:'#1a2a3a', border:'var(--cyan)',   color:'var(--cyan)'},
    error:   {bg:'#2a1a1a', border:'var(--red)',    color:'var(--red)'},
    warn:    {bg:'#2a2a1a', border:'var(--gold)',   color:'var(--gold-dim)'},
    success: {bg:'#1a2a1a', border:'var(--green)',  color:'var(--green)'},
  };
  var c = colors[type] || colors.info;

  // 移除旧toast
  var old = document.querySelector('.toast-notify');
  if (old) old.remove();
  if (_toastTimer) clearTimeout(_toastTimer);

  var toast = document.createElement('div');
  toast.className = 'toast-notify';
  toast.textContent = msg;
  toast.style.cssText = 'position:fixed;top:60px;left:50%;transform:translateX(-50%);'
    + 'background:'+c.bg+';color:'+c.color+';padding:8px 20px;border-radius:6px;'
    + 'font-size:0.9em;z-index:99999;border:1px solid '+c.border
    + ';pointer-events:none;transition:opacity 0.3s;max-width:600px;text-align:center;';
  document.body.appendChild(toast);
  _toastTimer = setTimeout(function() {
    toast.style.opacity = '0';
    setTimeout(function() { if (toast.parentNode) toast.remove(); }, 300);
  }, type === 'error' ? 5000 : 3000);
}

// ═══════════════════ 全局JS错误捕获 ═══════════════════
window._jsErrors = [];
window.addEventListener('error', function(e) {
  var ctx = '(unknown)';
  try {
    ctx = e.target ? (e.target.tagName || e.target.src || e.target.href || e.target.nodeName) : '';
    if (e.target === window) ctx = '(global)';
  } catch(_) {}
  var entry = {
    msg: e.message || '未知JS错误',
    file: e.filename || '',
    line: e.lineno || 0,
    col: e.colno || 0,
    ctx: ctx,
    time: new Date().toISOString()
  };
  window._jsErrors.push(entry);
  console.error('❌ [JS错误] ' + entry.file + ':' + entry.line
    + ' — ' + entry.msg + ' (ctx=' + entry.ctx + ')', e.error || '');
  showToast('⚠ ' + entry.msg + ' [' + entry.file.split('/').pop() + ':' + entry.line + ']', 'error');
});

// 未捕获Promise rejection
window.addEventListener('unhandledrejection', function(e) {
  console.error('❌ [未捕获Promise rejection]', e.reason);
  var msg = (e.reason && e.reason.message) ? e.reason.message : String(e.reason || '');
  showToast('⚠ 异步错误: ' + msg.substring(0, 80), 'error');
});

// ═══════════════════ 辅助 ═══════════════════
function closeModal(id) {
  var el = document.getElementById(id);
  if (el) el.classList.remove('show');
}

// ═══════════════════ API 调用（带错误拦截） ═══════════════════
// 调用计数，用于日志跟踪
var _apiCallSeq = 0;

function _apiBaseUrl() {
  return (typeof window !== 'undefined' && window.API) ? window.API
    : (typeof API !== 'undefined' ? API : '');
}

/**
 * GET 请求。成功返回JSON，失败返回 {error: "..."}。
 * 同时自动写控制台日志，方便排查。
 */
async function apiGet(path) {
  var seq = ++_apiCallSeq;
  var base = _apiBaseUrl();
  var url = base + path;
  var start = performance.now();
  try {
    var r = await fetch(url);
    var elapsed = (performance.now() - start).toFixed(0);
    if (!r.ok) {
      var text = await r.text().catch(function() { return ''; });
      console.error('❌ [apiGet#'+seq+'] HTTP ' + r.status + ' ' + path
        + ' (' + elapsed + 'ms) — ' + text.substring(0, 100));
      return {error: 'HTTP ' + r.status + ': ' + (text || r.statusText).substring(0, 150)};
    }
    var json = await r.json();
    console.debug('✅ [apiGet#'+seq+'] ' + path + ' (' + elapsed + 'ms)');
    // 服务端返回的 error 字段 → 前台视为错误
    if (json && json.error) {
      console.warn('⚠ [apiGet#'+seq+'] 服务端报错: ' + json.error + (json.error_id ? ' [id='+json.error_id+']' : ''));
    }
    return json;
  } catch (e) {
    var elapsed = (performance.now() - start).toFixed(0);
    console.error('❌ [apiGet#'+seq+'] 网络异常 ' + path + ' (' + elapsed + 'ms) — ' + e.message);
    return {error: '网络请求失败: ' + e.message};
  }
}

/**
 * POST 请求。body 对象自动 JSON.stringify。
 */
async function apiPost(path, body) {
  if (!body) body = {};
  var seq = ++_apiCallSeq;
  var base = _apiBaseUrl();
  var url = base + path;
  var start = performance.now();
  try {
    var r = await fetch(url, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(body),
    });
    var elapsed = (performance.now() - start).toFixed(0);
    if (!r.ok) {
      var text = await r.text().catch(function() { return ''; });
      console.error('❌ [apiPost#'+seq+'] HTTP ' + r.status + ' ' + path
        + ' (' + elapsed + 'ms) — ' + text.substring(0, 200));
      return {error: 'HTTP ' + r.status + ': ' + (text || r.statusText).substring(0, 200)};
    }
    var json = await r.json();
    console.debug('✅ [apiPost#'+seq+'] ' + path + ' (' + elapsed + 'ms)');
    if (json && json.error) {
      console.warn('⚠ [apiPost#'+seq+'] 服务端报错: ' + json.error + (json.error_id ? ' [id='+json.error_id+']' : ''));
    }
    return json;
  } catch (e) {
    var elapsed = (performance.now() - start).toFixed(0);
    console.error('❌ [apiPost#'+seq+'] 网络异常 ' + path + ' (' + elapsed + 'ms) — ' + e.message);
    return {error: '网络请求失败: ' + e.message};
  }
}

/**
 * 带UI反馈的API调用：自动处理error（弹toast），成功返回data。
 * 用法: var data = await apiCall('/api/state');
 *        if (!data) return; // 已自动弹toast
 */
async function apiCall(path, body) {
  var data;
  if (body !== undefined) {
    data = await apiPost(path, body);
  } else {
    data = await apiGet(path);
  }
  if (data && data.error) {
    showToast(data.error, 'error');
    return null;
  }
  return data;
}

// ═══════════════════ 游戏专用API封装 ═══════════════════

async function refreshState() {
  var data = await apiGet('/api/state');
  if (!data.error) {
    if (typeof renderAll === 'function') renderAll(data);
  } else {
    console.warn('刷新状态失败:', data.error);
    showToast('连接异常，请检查服务器', 'warn');
  }
}

async function sendAction(action, extra) {
  var el = document.getElementById('status-text');
  if (el) el.textContent = '执行 ' + action + '...';
  var body = {action: action};
  if (extra) Object.assign(body, extra);
  var data = await apiPost('/api/action', body);
  if (!data.error) {
    if (typeof renderAll === 'function') renderAll(data);
  } else {
    showToast('操作失败: ' + data.error, 'error');
    console.error('sendAction 失败: action=' + action, data);
  }
}
