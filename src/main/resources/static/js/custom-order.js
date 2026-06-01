// 七域逐鹿 · Web Client
// 自由指令 — AI 裁决 + 手动裁决 + 沙盒模式

var customMode = 'auto';

async function sendCustomOrder() {
  var input = document.getElementById('custom-input');
  var order = input.value.trim();
  if (!order) { document.getElementById('status-text').textContent = '请输入指令'; return; }

  var btn = document.getElementById('custom-send');
  var statusEl = document.getElementById('status-text');
  btn.disabled = true;
  var mode = document.getElementById('custom-mode-select').value;

  statusEl.classList.add('thinking');
  var dots = ['', '.', '..', '...'];
  var dotIdx = 0;
  var label = mode === 'sandbox' ? '沙盒执行中' : 'AI 思考中';
  var thinkingInterval = setInterval(function() {
    statusEl.textContent = label + dots[dotIdx];
    dotIdx = (dotIdx + 1) % dots.length;
  }, 500);

  try {
    if (mode === 'manual') {
      clearInterval(thinkingInterval);
      statusEl.classList.remove('thinking');
      var ctxResp = await apiPost('/api/custom-order', {order: order});
      btn.disabled = false;
      if (ctxResp.error) { statusEl.textContent = '裁决失败'; alert('错误: ' + (ctxResp.error.message || ctxResp.error)); return; }
      showManualAdjudication(order, ctxResp);
      statusEl.textContent = '编辑裁决JSON后提交';
    } else {
      var sandbox = mode === 'sandbox';
      var data = await apiPost('/api/custom-order/auto', {order: order, sandbox: sandbox});
      clearInterval(thinkingInterval);
      statusEl.classList.remove('thinking');
      btn.disabled = false;
      if (data.cancelled) { statusEl.textContent = '已取消'; return; }
      if (!data.error) {
        renderAll(data);
        if (sandbox && typeof mapInitialized !== 'undefined' && mapInitialized) { refreshMapOwnership(); }
        input.value = '';
        statusEl.textContent = sandbox ? '沙盒执行完毕' : '就绪';
        if (data.narrative) { showCustomOrderPopup(data); }
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

var _manualOrderText = '';
var _manualContextData = null;

function showManualAdjudication(order, ctxResp) {
  _manualOrderText = order;
  _manualContextData = ctxResp;
  var sm = document.getElementById('submenu-panel');
  sm.classList.add('show');
  var hint = ctxResp.local_hint || {};
  var ctx = ctxResp.context || {};
  var adjJson = JSON.stringify(hint, null, 2);
  sm.innerHTML = '<h3>📝 手动裁决</h3>' +
    '<div style="font-size:0.8em;color:var(--text-dim);margin:4px 0;">指令: <b style="color:var(--white);">' + escapeHtml(order) + '</b></div>' +
    '<div style="font-size:0.75em;color:var(--text-dim);margin:4px 0;max-height:120px;overflow-y:auto;">' +
    '<b>势力:</b> ' + escapeHtml(ctx.faction||'') + ' | <b>国库:</b> ' + (ctx.treasury||0) + '💰 | <b>AP:</b> ' + (ctx.ap||0) +
    (ctx.stats ? '<br><b>属性:</b> '+escapeHtml(JSON.stringify(ctx.stats)) : '') + '</div>' +
    '<textarea id="manual-adj-json" style="width:100%;height:220px;background:var(--bg);color:var(--text);border:1px solid var(--border);border-radius:4px;font-family:var(--mono);font-size:0.78em;padding:8px;resize:vertical;">' + escapeHtml(adjJson) + '</textarea>' +
    '<div style="margin-top:8px;display:flex;gap:8px;">' +
    '<button class="btn" onclick="submitManualAdjudication()" style="background:var(--gold-dim);color:#000;border:none;padding:6px 16px;border-radius:4px;cursor:pointer;font-weight:bold;">提交裁决</button>' +
    '<button class="btn" onclick="cancelManualAdjudication()" style="background:var(--border);color:var(--text);border:none;padding:6px 16px;border-radius:4px;cursor:pointer;">取消</button></div>' +
    '<div style="font-size:0.7em;color:var(--text-dim);margin-top:6px;">编辑上方JSON，修改 feasibility / cost / effects / risk / ap_cost / narrative 后提交</div>';
}

async function submitManualAdjudication() {
  var textarea = document.getElementById('manual-adj-json');
  if (!textarea) return;
  var adj;
  try { adj = JSON.parse(textarea.value); } catch (e) { alert('JSON格式错误: ' + e.message); return; }
  document.getElementById('status-text').textContent = '提交裁决...';
  var data = await apiPost('/api/custom-order/apply', { order: _manualOrderText, adjudication: adj });
  if (!data.error) {
    renderAll(data);
    document.getElementById('custom-input').value = '';
    hideSubmenu();
    if (data.narrative) { showCustomOrderPopup(data); }
  } else { alert('错误: ' + data.error); }
}

function cancelManualAdjudication() {
  _manualOrderText = ''; _manualContextData = null;
  document.getElementById('status-text').textContent = '就绪';
  hideSubmenu();
}

function updateCustomMode() {
  customMode = document.getElementById('custom-mode-select').value;
}
