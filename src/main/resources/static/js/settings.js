// 七域逐鹿 · Web Client

// ── API配置 ──────────────────────────────────────────────────
const DEFAULT_MODELS = {deepseek: 'deepseek-chat', openai: 'gpt-4o', anthropic: 'claude-sonnet-4-6'};

async function showSettingsModal() {
  document.getElementById('settings-modal').classList.add('show');
  var body = document.getElementById('settings-body');
  // 动态构建表单（v2.2无预置HTML元素）
  body.innerHTML = '<div id="cfg-status" style="display:none;padding:8px;border-radius:4px;margin-bottom:8px;"></div>'
    + '<label style="display:block;margin:6px 0 2px;">AI模式</label>'
    + '<select id="cfg-provider" onchange="onCfgProviderChange()" style="width:100%;padding:6px;background:var(--panel2);border:1px solid var(--border);color:var(--text);border-radius:3px;">'
    + '<option value="local">本地（无API）</option><option value="deepseek">DeepSeek</option><option value="openai">OpenAI</option><option value="anthropic">Claude</option>'
    + '</select>'
    + '<label style="display:block;margin:6px 0 2px;">API Key</label>'
    + '<input id="cfg-api-key" type="password" placeholder="sk-..." style="width:100%;padding:6px;background:var(--panel2);border:1px solid var(--border);color:var(--text);border-radius:3px;">'
    + '<div style="margin:4px 0;"><label style="font-size:0.8em;color:var(--text-dim);"><input type="checkbox" id="cfg-save-key"> 保存到服务器</label></div>'
    + '<label style="display:block;margin:6px 0 2px;">模型</label>'
    + '<input id="cfg-model" placeholder="默认" style="width:100%;padding:6px;background:var(--panel2);border:1px solid var(--border);color:var(--text);border-radius:3px;">'
    + '<label style="display:block;margin:6px 0 2px;">Base URL</label>'
    + '<input id="cfg-base-url" placeholder="默认" style="width:100%;padding:6px;background:var(--panel2);border:1px solid var(--border);color:var(--text);border-radius:3px;">'
    + '<div style="margin-top:10px;display:flex;gap:8px;">'
    + '<button onclick="saveSettings()" style="background:var(--gold);color:#000;border:none;padding:6px 16px;border-radius:4px;cursor:pointer;font-weight:bold;">💾 保存</button>'
    + '<button onclick="testConnection()" style="background:var(--panel2);border:1px solid var(--border);color:var(--text);padding:6px 16px;border-radius:4px;cursor:pointer;">🔍 测试</button>'
    + '</div>';
  // 加载当前配置
  try {
    var data = await apiGet('/api/config');
    if (data && data.provider) {
      document.getElementById('cfg-provider').value = data.provider || 'local';
      document.getElementById('cfg-model').value = data.model || '';
      document.getElementById('cfg-base-url').value = data.base_url || '';
      document.getElementById('cfg-api-key').placeholder = data.api_key_set ? '(已保存，留空不修改)' : 'sk-...';
      var statusEl = document.getElementById('cfg-status');
      if (data.providers) {
        var lines = [];
        for (var name in data.providers) {
          var info = data.providers[name];
          lines.push((info.available ? '✓' : '✗') + ' ' + name + ': ' + info.message);
        }
        statusEl.innerHTML = lines.join('<br>');
        statusEl.style.display = 'block';
        statusEl.style.background = 'var(--panel2)';
        statusEl.style.color = 'var(--text-dim)';
      }
    }
  } catch(e) {}
}

function onCfgProviderChange() {
  var provider = document.getElementById('cfg-provider').value;
  var modelInput = document.getElementById('cfg-model');
  if (provider === 'local') modelInput.placeholder = '本地模式无需模型';
  else modelInput.placeholder = '默认: ' + (DEFAULT_MODELS[provider] || '');
}

async function saveSettings() {
  var provider = document.getElementById('cfg-provider').value;
  var apiKey = document.getElementById('cfg-api-key').value;
  var model = document.getElementById('cfg-model').value;
  var baseUrl = document.getElementById('cfg-base-url').value;
  var saveKey = document.getElementById('cfg-save-key').checked;
  var body = {provider:provider, save_key:saveKey};
  if (apiKey) body.api_key = apiKey;
  if (model) body.model = model;
  if (baseUrl) body.base_url = baseUrl;
  var statusEl = document.getElementById('cfg-status');
  statusEl.style.display = 'block';
  try {
    var data = await apiPost('/api/config', body);
    if (data.error) {
      statusEl.style.background = '#3a1a1a'; statusEl.style.color = 'var(--red)';
      statusEl.textContent = '错误: ' + data.error;
    } else {
      statusEl.style.background = '#1a3a2a'; statusEl.style.color = 'var(--green)';
      statusEl.textContent = '✓ ' + (data.message || '配置已更新');
      var modeNames = window._AI_MODES;
      var el = document.getElementById('ai-mode');
      if (el) { el.textContent = modeNames[provider] || provider; }
      document.getElementById('cfg-api-key').value = '';
      document.getElementById('cfg-api-key').placeholder = saveKey ? '(已保存)' : '(仅本次会话)';
    }
  } catch(e) {
    statusEl.style.background = '#3a1a1a'; statusEl.style.color = 'var(--red)';
    statusEl.textContent = '连接失败: ' + e.message;
  }
}

async function testConnection() {
  var provider = document.getElementById('cfg-provider').value;
  var statusEl = document.getElementById('cfg-status');
  statusEl.style.display = 'block';
  statusEl.style.background = 'var(--panel2)'; statusEl.style.color = 'var(--yellow)';
  statusEl.textContent = '测试中...';
  try {
    var data = await apiGet('/api/config/check?provider=' + provider);
    if (data.available) {
      statusEl.style.background = '#1a3a2a'; statusEl.style.color = 'var(--green)';
      statusEl.textContent = '✓ ' + data.message;
    } else {
      statusEl.style.background = '#3a3a1a'; statusEl.style.color = 'var(--red)';
      statusEl.textContent = '✗ ' + data.message;
    }
  } catch(e) {
    statusEl.style.background = '#3a1a1a'; statusEl.style.color = 'var(--red)';
    statusEl.textContent = '连接失败: ' + e.message;
  }
}
