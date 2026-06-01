// 七域逐鹿 · Web Client

// ── 地图命令模式（移动+攻击）──
async function enterCommandMode(pid, selectedIndices, actionType) {
  if (!movePathLayer) { statusText('⚠ 地图未就绪，请稍后再试'); return; }
  exitCommandMode();
  statusText('⏳ 计算可达范围...');

  try {
    const reqBody = selectedIndices && selectedIndices.length
      ? { unit_indices: selectedIndices }
      : { position: pid };
    const reachResp = await fetch('/api/map/reachable', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(reqBody)
    });
    const reachData = await reachResp.json();
    if (reachData.error) { statusText('⚠ ' + reachData.error); return; }
    if (!reachData.reachable || !reachData.reachable.length) { statusText('⚠ 该位置无可调动部队'); return; }

    const allUnitIndices = reachData.unit_indices || [reachData.unit_index];
    moveMode = {
      unitIndices: allUnitIndices,
      unitName: reachData.unit_name,
      reachable: reachData.reachable,
      unitPid: pid,
      localUnits: reachData.local_units || [],
      selectedUnitIndices: selectedIndices ? [...selectedIndices] : [...allUnitIndices],
    };

    moveMode.actionType = actionType || 'move';

    // 高亮可达省份（绿=友好/中立, 红=敌对）
    moveHighlightLayer.clearLayers();
    const added = new Set();
    for (const dest of reachData.reachable) {
      if (dest.lat == null || dest.lng == null) continue;
      if (added.has(dest.pid)) continue;
      added.add(dest.pid);
      const isEnemy = dest.is_enemy;
      const color = isEnemy ? 'rgba(220,80,60,0.30)' : (dest.distance <= 1 ? 'rgba(80,200,120,0.25)' : 'rgba(200,160,60,0.20)');
      const r = isEnemy ? 9 : (dest.distance <= 1 ? 8 : 6);
      const circle = L.circleMarker([dest.lat, dest.lng], {
        radius: r, color: isEnemy ? 'rgba(220,80,60,0.6)' : 'transparent', weight: isEnemy ? 1.5 : 0,
        fillColor: color, fillOpacity: 0.7, interactive: true,
      }).addTo(moveHighlightLayer);
      circle.on('click', () => {
        if (dest.is_enemy) { showAttackDest(dest); }
        else { showMoveDest(dest); }
      });
    }

    // 起点高亮（白色双圈）
    const p = mapProvinceData[pid];
    if (p && p.lat != null) {
      L.circleMarker([p.lat, p.lng], {
        radius: 10, color: '#fff', weight: 2, fillColor: 'transparent', fillOpacity: 0,
        dashArray: '4,3',
      }).addTo(moveHighlightLayer);
    }

    statusText(`⚔ 命令模式 · ${reachData.unit_name} · 点击高亮省份：绿=行军 / 红=攻击 · 右键取消`);
  } catch(e) { console.log('Enter command mode failed:', e); }
}

function showMoveDest(dest) {
  if (!moveMode) return;
  moveMode.selectedDest = dest;
  moveMode.actionType = 'move';

  movePathLayer.clearLayers();
  const coords = [];
  for (const wp of dest.path) {
    const p = mapProvinceData[wp];
    if (p && p.lat != null) coords.push([p.lat, p.lng]);
  }
  if (coords.length >= 2) {
    L.polyline(coords, { color: '#80c878', weight: 3, opacity: 0.8, dashArray: '8,4' }).addTo(movePathLayer);
    L.circleMarker(coords[0], { radius: 5, color: '#fff', weight: 2, fillOpacity: 0 }).addTo(movePathLayer);
    L.circleMarker(coords[coords.length-1], { radius: 6, color: '#80c878', weight: 2.5, fillColor: '#80c878', fillOpacity: 0.3 }).addTo(movePathLayer);
  }

  const wpStr = (dest.waypoints && dest.waypoints.length) ? ' 途经: ' + dest.waypoints.join(' → ') : '';
  const html = `<div style="font-family:var(--font);min-width:180px;">
    <h4 style="color:var(--gold);margin:0;">🚚 行军至 ${dest.name}</h4>
    <div style="font-size:0.85em;color:var(--text-dim);margin:4px 0;">距离: ${dest.distance}步${wpStr}</div>
    <button onclick="confirmMove()" style="background:var(--gold-bg);border:1px solid var(--gold);color:var(--gold);padding:3px 12px;border-radius:3px;cursor:pointer;font-family:var(--font);margin:4px 4px 0 0;">✓ 出发</button>
    <button onclick="exitCommandMode()" style="background:none;border:1px solid var(--border);color:var(--text-dim);padding:3px 12px;border-radius:3px;cursor:pointer;font-family:var(--font);margin:4px 0 0;">✗ 取消</button>
  </div>`;

  const p = mapProvinceData[dest.pid];
  if (p && p.lat != null) {
    leafletMap.closePopup();
    L.popup({ closeButton: true, autoClose: false })
      .setLatLng([p.lat, p.lng])
      .setContent(html)
      .openOn(leafletMap);
  }
}

// ── 攻击目标弹窗（命令模式下点击敌方省份）──
function showAttackDest(dest) {
  if (!moveMode) return;
  moveMode.selectedDest = dest;
  moveMode.actionType = 'attack';
  // 保留已有选择，没有则默认全选
  if (!moveMode.selectedUnitIndices || moveMode.selectedUnitIndices.length === 0) {
    moveMode.selectedUnitIndices = [...moveMode.unitIndices];
  }

  movePathLayer.clearLayers();
  const coords = [];
  for (const wp of dest.path) {
    const p = mapProvinceData[wp];
    if (p && p.lat != null) coords.push([p.lat, p.lng]);
  }
  if (coords.length >= 2) {
    L.polyline(coords, { color: '#e05040', weight: 3, opacity: 0.85, dashArray: '6,4' }).addTo(movePathLayer);
    L.circleMarker(coords[0], { radius: 5, color: '#fff', weight: 2, fillOpacity: 0 }).addTo(movePathLayer);
    L.circleMarker(coords[coords.length-1], { radius: 7, color: '#e05040', weight: 2.5, fillColor: '#e05040', fillOpacity: 0.35 }).addTo(movePathLayer);
  }

  // Build unit selection UI
  let unitRows = '';
  const selSet = new Set(moveMode.selectedUnitIndices || []);
  for (const u of (moveMode.localUnits || [])) {
    const inRange = dest.distance <= 3; // can reach for attack
    const isSelected = selSet.has(u.index);
    const checked = (isSelected && inRange) ? 'checked' : '';
    const disabled = inRange ? '' : 'disabled style="opacity:0.4"';
    unitRows += `<label ${disabled}><input type="checkbox" ${checked} value="${u.index}" onchange="toggleAttackUnit(this)" style="margin-right:3px;vertical-align:middle;">${u.icon||'?'} ${u.name} ⚔${u.attack} 🛡${u.defense} ❤${u.strength}%</label>`;
  }
  if (!unitRows) unitRows = '<div style="font-size:0.75em;color:var(--text-dim);">无可参战部队</div>';

  const html = `<div style="font-family:var(--font);min-width:220px;max-width:300px;">
    <h4 style="color:#e05040;margin:0;">⚔ 攻击 ${dest.name}</h4>
    <div style="font-size:0.82em;color:var(--text-dim);margin:4px 0;">距离: ${dest.distance}步 · 敌方领土</div>
    <div style="margin:6px 0;max-height:150px;overflow-y:auto;font-size:0.78em;">${unitRows}</div>
    <div style="margin:6px 0;border-top:1px solid var(--border);padding-top:4px;">
      <button onclick="confirmAttack()" style="background:#602020;border:1px solid #c04040;color:#faa;padding:3px 12px;border-radius:3px;cursor:pointer;font-family:var(--font);margin-right:4px;">✓ 发动攻击</button>
      <button onclick="exitCommandMode()" style="background:none;border:1px solid var(--border);color:var(--text-dim);padding:3px 12px;border-radius:3px;cursor:pointer;font-family:var(--font);">✗ 取消</button>
    </div>
  </div>`;

  const p = mapProvinceData[dest.pid];
  if (p && p.lat != null) {
    leafletMap.closePopup();
    L.popup({ closeButton: true, autoClose: false })
      .setLatLng([p.lat, p.lng])
      .setContent(html)
      .openOn(leafletMap);
  }
}

function toggleAttackUnit(cb) {
  if (!moveMode || moveMode.actionType !== 'attack') return;
  const idx = parseInt(cb.value);
  if (cb.checked) {
    if (!moveMode.selectedUnitIndices.includes(idx)) moveMode.selectedUnitIndices.push(idx);
  } else {
    moveMode.selectedUnitIndices = moveMode.selectedUnitIndices.filter(i => i !== idx);
  }
}

async function confirmMove() {
  if (!moveMode || !moveMode.selectedDest) return;
  const destPid = moveMode.selectedDest.pid;
  const unitIndices = moveMode.selectedUnitIndices || moveMode.unitIndices || [];
  try {
    const resp = await fetch('/api/map/move', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({unit_indices: unitIndices, dest_pid: destPid})
    });
    const data = await resp.json();
    if (data.error) { statusText('❌ ' + data.error); exitCommandMode(); return; }
    statusText('✅ ' + data.message);
    exitCommandMode();
    clearUnitSelection();
    setTimeout(() => refreshState(), 300);
  } catch(e) { console.log('Move failed:', e); exitCommandMode(); }
}

async function confirmAttack() {
  if (!moveMode || moveMode.actionType !== 'attack' || !moveMode.selectedDest) return;
  const destPid = moveMode.selectedDest.pid;
  const unitIndices = moveMode.selectedUnitIndices || [];
  if (!unitIndices.length) { statusText('⚠ 至少选择一支部队'); return; }

  try {
    const resp = await fetch('/api/map/attack', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({unit_indices: unitIndices, dest_pid: destPid})
    });
    const data = await resp.json();
    if (data.error) { statusText('❌ ' + data.error); exitCommandMode(); return; }
    statusText('⚔ ' + data.message);
    exitCommandMode();
    clearUnitSelection();
    setTimeout(() => refreshState(), 500);
  } catch(e) { console.log('Attack failed:', e); exitCommandMode(); }
}

function exitCommandMode() {
  moveMode = null;
  if (moveHighlightLayer) moveHighlightLayer.clearLayers();
  if (movePathLayer) movePathLayer.clearLayers();
  try { if (leafletMap) leafletMap.closePopup(); } catch(e) {}
}

