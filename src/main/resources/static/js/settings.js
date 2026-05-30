// 七域逐鹿 · Web Client

// ── API配置 ──────────────────────────────────────────────────
const DEFAULT_MODELS = {deepseek: 'deepseek-chat', openai: 'gpt-4o', anthropic: 'claude-sonnet-4-6'};

async function showSettingsModal() {
  document.getElementById('settings-modal').classList.add('show');
  // 加载当前配置
  try {
    const data = await apiGet('/api/config');
    if (data && data.config) {
      const cfg = data.config;
      document.getElementById('cfg-provider').value = cfg.provider || 'local';
      document.getElementById('cfg-model').value = cfg.model || '';
      document.getElementById('cfg-base-url').value = cfg.base_url || '';
      document.getElementById('cfg-api-key').value = '';
      document.getElementById('cfg-save-key').checked = false;
      document.getElementById('cfg-api-key').placeholder = data.api_key_set ? '(已保存，留空不修改)' : 'sk-...';
      // 显示各供应商状态
      const statusEl = document.getElementById('cfg-status');
      if (data.providers) {
        const lines = [];
        for (const [name, info] of Object.entries(data.providers)) {
          const icon = info.available ? '✓' : '✗';
          lines.push(icon + ' ' + name + ': ' + info.message);
        }
        statusEl.innerHTML = lines.join('<br>');
        statusEl.style.display = 'block';
        statusEl.style.background = 'var(--panel2)';
        statusEl.style.color = 'var(--text-dim)';
      }
    }
  } catch (e) {
    // 离线模式
  }
}

function onCfgProviderChange() {
  const provider = document.getElementById('cfg-provider').value;
  const modelInput = document.getElementById('cfg-model');
  if (provider === 'local') {
    modelInput.placeholder = '本地模式无需模型';
  } else {
    modelInput.placeholder = '默认: ' + (DEFAULT_MODELS[provider] || '');
  }
}

async function saveSettings() {
  const provider = document.getElementById('cfg-provider').value;
  const apiKey = document.getElementById('cfg-api-key').value;
  const model = document.getElementById('cfg-model').value;
  const baseUrl = document.getElementById('cfg-base-url').value;
  const saveKey = document.getElementById('cfg-save-key').checked;

  const body = {provider, save_key: saveKey};
  if (apiKey) body.api_key = apiKey;
  if (model) body.model = model;
  if (baseUrl) body.base_url = baseUrl;

  const statusEl = document.getElementById('cfg-status');
  statusEl.style.display = 'block';

  try {
    const data = await apiPost('/api/config', body);
    if (data.error) {
      statusEl.style.background = '#3a1a1a';
      statusEl.style.color = 'var(--red)';
      statusEl.textContent = '错误: ' + data.error;
    } else {
      statusEl.style.background = '#1a3a2a';
      statusEl.style.color = 'var(--green)';
      statusEl.textContent = '✓ ' + (data.message || '配置已更新') +
        ' | ' + data.provider + ' ' + (data.model || '') +
        ' | 可用: ' + (data.available ? '是' : data.status || '否');
      // 更新顶部AI模式指示器
      const modeNames = {local:'本地模板', deepseek:'DeepSeek', openai:'OpenAI', anthropic:'Claude'};
      const el = document.getElementById('ai-mode');
      if (el) {
        el.textContent = modeNames[provider] || provider;
        el.title = 'AI GM: ' + (data.available ? '可用' : '未配置');
      }
      document.getElementById('cfg-api-key').value = '';
      document.getElementById('cfg-api-key').placeholder = saveKey ? '(已保存)' : '(仅本次会话)';
    }
  } catch (e) {
    statusEl.style.background = '#3a1a1a';
    statusEl.style.color = 'var(--red)';
    statusEl.textContent = '连接失败: ' + e.message;
  }
}

async function testConnection() {
  const provider = document.getElementById('cfg-provider').value;
  const statusEl = document.getElementById('cfg-status');
  statusEl.style.display = 'block';
  statusEl.style.background = 'var(--panel2)';
  statusEl.style.color = 'var(--yellow)';
  statusEl.textContent = '测试中...';

  try {
    const data = await apiGet('/api/config/check?provider=' + provider);
    if (data.available) {
      statusEl.style.background = '#1a3a2a';
      statusEl.style.color = 'var(--green)';
      statusEl.textContent = '✓ ' + data.message;
    } else {
      statusEl.style.background = '#3a1a1a';
      statusEl.style.color = 'var(--red)';
      statusEl.textContent = '✗ ' + data.message + (data.requires_key ? ' (需要API Key)' : '');
    }
  } catch (e) {
    statusEl.style.background = '#3a1a1a';
    statusEl.style.color = 'var(--red)';
    statusEl.textContent = '连接失败: ' + e.message;
  }
}

