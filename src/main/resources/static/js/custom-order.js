// 七域逐鹿 · Web Client

// ═══════════════════════════════════════════════════════════
//  自然语言指令解析
// ═══════════════════════════════════════════════════════════

let cmdMode = 'local';
let parsedActions = [];

async function parseCommand() {
  const input = document.getElementById('cmd-input');
  const btn = document.getElementById('cmd-parse-btn');
  const execBtn = document.getElementById('cmd-execute-btn');
  const preview = document.getElementById('cmd-preview');
  const text = input.value.trim();
  if (!text) { preview.style.display = 'none'; execBtn.style.display = 'none'; return; }

  btn.disabled = true;
  btn.textContent = '...';
  preview.style.display = 'block';
  preview.innerHTML = '<span class="dim">解析中...</span>';

  try {
    const resp = await fetch('/api/command/parse', {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({text: text, use_ai: cmdMode === 'ai'})
    });
    const data = await resp.json();
    btn.disabled = false;
    btn.textContent = '解析';

    if (!data.success) {
      preview.innerHTML = `<span style="color:var(--red)">⚠ ${data.warnings?.join('; ') || '解析失败'}</span>`;
      execBtn.style.display = 'none';
      parsedActions = [];
      return;
    }

    parsedActions = data.actions || [];
    let html = `<div style="color:var(--text-dim);margin-bottom:4px;">📋 ${data.method === 'ai' ? '🤖 AI' : '⚡ 本地'}解析 · 置信度 ${Math.round(data.confidence*100)}%</div>`;
    html += `<div style="color:var(--cyan);margin-bottom:6px;">${escapeHtml(data.explanation || '')}</div>`;
    if (data.actions && data.actions.length > 0) {
      data.actions.forEach((a, i) => {
        const typeNames = {move_unit:'🚚 移动', start_campaign:'⚔ 战役', train_unit:'🎖 招募', build_construction:'🏗 建设'};
        html += `<div class="cmd-action-card">
          <span class="idx">${i+1}.</span>
          <span class="desc">${escapeHtml(a.description || JSON.stringify(a))}</span>
          <span class="type-tag">${typeNames[a.type] || a.type}</span>
        </div>`;
      });
      execBtn.style.display = 'inline-block';
    } else {
      execBtn.style.display = 'none';
    }
    if (data.warnings && data.warnings.length) {
      html += `<div style="color:var(--gold);margin-top:4px;font-size:0.85em;">⚠ ${escapeHtml(data.warnings.join('; '))}</div>`;
    }
    preview.innerHTML = html;
  } catch (e) {
    btn.disabled = false;
    btn.textContent = '解析';
    preview.innerHTML = `<span style="color:var(--red)">解析失败: ${e.message}</span>`;
    execBtn.style.display = 'none';
    parsedActions = [];
  }
}

async function executeCommand() {
  if (!parsedActions.length) return;
  const execBtn = document.getElementById('cmd-execute-btn');
  execBtn.disabled = true;
  execBtn.textContent = '执行中...';
  try {
    const resp = await fetch('/api/command/execute', {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({actions: parsedActions, mode: 'execute'})
    });
    const data = await resp.json();
    execBtn.disabled = false;
    execBtn.textContent = '▶ 执行';
    if (!data.error) {
      renderAll(data);
      document.getElementById('cmd-input').value = '';
      document.getElementById('cmd-preview').style.display = 'none';
      execBtn.style.display = 'none';
      parsedActions = [];
      // Show execution feedback
      const panel = document.getElementById('status-text');
      if (panel) panel.textContent = data.all_success ? '指令执行完毕' : '部分操作失败';
    } else {
      alert('执行失败: ' + (data.error?.message || data.error));
    }
  } catch (e) {
    execBtn.disabled = false;
    execBtn.textContent = '▶ 执行';
    alert('执行异常: ' + e.message);
  }
}

function setCmdMode(mode) {
  cmdMode = mode;
  document.getElementById('cmd-local-btn').classList.toggle('active', mode === 'local');
  document.getElementById('cmd-ai-btn').classList.toggle('active', mode === 'ai');
}

async function sendCustomOrder() {
  const input = document.getElementById('custom-input');
  const order = input.value.trim();
  if (!order) { document.getElementById('status-text').textContent = '请输入指令'; return; }

  const btn = document.getElementById('custom-send');
  const statusEl = document.getElementById('status-text');
  btn.disabled = true;
  const mode = document.getElementById('custom-mode-select').value;

  // 思考动画：AI裁决可能需要几秒，让用户知道正在处理
  statusEl.classList.add('thinking');
  const dots = ['', '.', '..', '...'];
  let dotIdx = 0;
  const label = mode === 'sandbox' ? '沙盒执行中' : 'AI 思考中';
  const thinkingInterval = setInterval(() => {
    statusEl.textContent = label + dots[dotIdx];
    dotIdx = (dotIdx + 1) % dots.length;
  }, 500);

  try {
    if (mode === 'manual') {
      clearInterval(thinkingInterval);
      statusEl.classList.remove('thinking');
      const ctxResp = await apiPost('/api/custom-order', {order: order});
      btn.disabled = false;
      if (ctxResp.error) { statusEl.textContent = '裁决失败'; alert('错误: ' + (ctxResp.error.message || ctxResp.error)); return; }
      showManualAdjudication(order, ctxResp);
      statusEl.textContent = '编辑裁决JSON后提交';
    } else {
      // auto 或 sandbox
      const sandbox = mode === 'sandbox';
      const data = await apiPost('/api/custom-order/auto', {order: order, sandbox: sandbox});
      clearInterval(thinkingInterval);
      statusEl.classList.remove('thinking');
      btn.disabled = false;
      if (data.cancelled) { statusEl.textContent = '已取消'; return; }
      if (!data.error) {
        renderAll(data);
        // 沙盒操作后强制刷新地图所有权
        if (sandbox && mapInitialized) { refreshMapOwnership(); }
        input.value = '';
        statusEl.textContent = sandbox ? '沙盒执行完毕' : '就绪';
        // 弹窗显示AI裁决叙事
        if (data.narrative) {
          showCustomOrderPopup(data);
        }
      } else {
        statusEl.textContent = '裁决失败';
        alert('裁决失败: ' + (data.error.message || data.error));
      }
    }
  } catch (e) {
    clearInterval(thinkingInterval);
    statusEl.classList.remove('thinking');
    console.error('sendCustomOrder:', e);
    btn.disabled = false;
    statusEl.textContent = '就绪';
    alert('网络错误: ' + (e.message || '未知'));
  }
}

let _manualOrderText = '';
let _manualContextData = null;

function showManualAdjudication(order, ctxResp) {
  _manualOrderText = order;
  _manualContextData = ctxResp;
  const sm = document.getElementById('submenu-panel');
  sm.classList.add('show');
  const hint = ctxResp.local_hint || {};
  const ctx = ctxResp.context || {};
  const adjJson = JSON.stringify(hint, null, 2);
  sm.innerHTML = `<h3>📝 手动裁决</h3>
    <div style="font-size:0.8em;color:var(--text-dim);margin:4px 0;">指令: <b style="color:var(--white);">${escapeHtml(order)}</b></div>
    <div style="font-size:0.75em;color:var(--text-dim);margin:4px 0;max-height:120px;overflow-y:auto;">
      <b>势力:</b> ${escapeHtml(ctx.faction||'')} | <b>国库:</b> ${ctx.treasury||0}💰 | <b>AP:</b> ${ctx.ap||0}
      ${ctx.stats ? '<br><b>属性:</b> '+escapeHtml(JSON.stringify(ctx.stats)) : ''}
    </div>
    <textarea id="manual-adj-json" style="width:100%;height:220px;background:var(--bg);color:var(--text);border:1px solid var(--border);border-radius:4px;font-family:var(--mono);font-size:0.78em;padding:8px;resize:vertical;">${escapeHtml(adjJson)}</textarea>
    <div style="margin-top:8px;display:flex;gap:8px;">
      <button class="btn" onclick="submitManualAdjudication()" style="background:var(--gold-dim);color:#000;border:none;padding:6px 16px;border-radius:4px;cursor:pointer;font-weight:bold;">提交裁决</button>
      <button class="btn" onclick="cancelManualAdjudication()" style="background:var(--border);color:var(--text);border:none;padding:6px 16px;border-radius:4px;cursor:pointer;">取消</button>
    </div>
    <div style="font-size:0.7em;color:var(--text-dim);margin-top:6px;">编辑上方JSON，修改 feasibility / cost / effects / risk / ap_cost / narrative 后提交</div>
    <details style="margin-top:8px;font-size:0.75em;color:var(--text-dim);">
      <summary style="cursor:pointer;color:var(--gold-dim);">📋 可用 action 类型参考</summary>
      <div style="background:var(--bg);padding:8px;border-radius:4px;margin-top:4px;line-height:1.7;max-height:220px;overflow-y:auto;">
        <div><b style="color:var(--white);">train_unit</b> — unit_type: infantry/cavalry/artillery/navy, location: 省份名, early_deploy?: true</div>
        <div><b style="color:var(--white);">build_construction</b> — build_id: "2.1"~"2.11"</div>
        <div><b style="color:var(--white);">start_campaign</b> — province: 目标省, unit_names: ["部队名"], tactic?: 战术名</div>
        <div><b style="color:var(--white);">reinforce_campaign</b> — campaign_province: 战役所在省, unit_names: ["部队名"]</div>
        <div><b style="color:var(--white);">retreat_campaign</b> — campaign_province: 战役所在省</div>
        <div><b style="color:var(--white);">execute_resolution</b> — res_index: 0~N</div>
        <div><b style="color:var(--white);">diplo_action</b> — sub: 1~6, target_faction: 势力名</div>
        <div><b style="color:var(--white);">move_unit</b> — unit_name: 部队名, destination: 省份名</div>
        <div><b style="color:var(--white);">tech_research</b> — tech_id: 科技名</div>
        <div style="margin-top:6px;color:var(--gold-dim);">注意：使用actions时请将cost设为{}、ap_cost设为0，引擎自行处理扣费。</div>
      </div>
    </details>`;
}

async function submitManualAdjudication() {
  const textarea = document.getElementById('manual-adj-json');
  if (!textarea) return;
  let adj;
  try {
    adj = JSON.parse(textarea.value);
  } catch (e) {
    alert('JSON格式错误: ' + e.message);
    return;
  }
  document.getElementById('status-text').textContent = '提交裁决...';
  const data = await apiPost('/api/custom-order/apply', {
    order: _manualOrderText,
    adjudication: adj,
  });
  if (!data.error) {
    renderAll(data);
    document.getElementById('custom-input').value = '';
    hideSubmenu();
    if (data.narrative) {
      showCustomOrderPopup(data);
    }
  } else {
    alert('错误: ' + data.error);
  }
}

function cancelManualAdjudication() {
  _manualOrderText = '';
  _manualContextData = null;
  document.getElementById('status-text').textContent = '就绪';
  hideSubmenu();
}

function updateCustomMode() {
  customMode = document.getElementById('custom-mode-select').value;
}

