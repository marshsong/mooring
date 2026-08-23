/* Mooring 控制台前端（全本地，无外部请求）。 */
"use strict";

const TOKEN_KEY = "mooring_token";
let paired = false;
let status = null;

function el(id) { return document.getElementById(id); }
function esc(s) { return String(s ?? "").replace(/[&<>"']/g, c => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[c])); }
function fmtSec(s) {
  s = Number(s || 0);
  if (s >= 3600) return (s / 3600).toFixed(1) + " 小时";
  return Math.round(s / 60) + " 分钟";
}

class ApiError extends Error {
  constructor(status, msg, data) { super(msg); this.status = status; this.msg = msg; this.data = data; }
}

async function api(method, path, body, raw) {
  const headers = {};
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) headers["X-Anchor-Token"] = token;
  let payload;
  if (raw !== undefined) {
    headers["Content-Type"] = "text/plain";
    payload = raw;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }
  const res = await fetch(path, { method, headers, body: payload });
  const j = await res.json().catch(() => ({ code: -1, msg: "bad response", data: null }));
  if (!res.ok) throw new ApiError(res.status, j.msg, j.data);
  return j.data;
}

/* ---------------- 配对 ---------------- */

async function pairWithToken(token) {
  const data = await api("POST", "/api/pair", { token, userAgent: navigator.userAgent });
  localStorage.setItem(TOKEN_KEY, token);
  location.reload();
}

function startScan() {
  el("video").classList.remove("hidden");
  el("canvas").classList.remove("hidden");
  const video = el("video");
  const canvas = el("canvas");
  const ctx = canvas.getContext("2d");
  navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } })
    .then(stream => {
      video.srcObject = stream;
      video.setAttribute("playsinline", "");
      video.play();
      requestAnimationFrame(tick);
    })
    .catch(e => { el("scanMsg").textContent = "无法打开摄像头：" + e.message; });

  function tick() {
    if (video.readyState === video.HAVE_ENOUGH_DATA) {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      const img = ctx.getImageData(0, 0, canvas.width, canvas.height);
      const code = jsQR(img.data, img.width, img.height);
      if (code && code.data) {
        try {
          const obj = JSON.parse(code.data);
          if (obj.token) { el("scanMsg").textContent = "扫码成功，正在配对…"; pairWithToken(obj.token); return; }
        } catch (_) {}
      }
    }
    requestAnimationFrame(tick);
  }
}

/* ---------------- 主界面 ---------------- */

function showMain() {
  el("pairView").classList.add("hidden");
  el("mainView").classList.remove("hidden");
  bindTabs();
  el("btnFocus").addEventListener("click", async () => {
    if (!ensureWrite()) return;
    try { await api("POST", "/api/focus", { minutes: 45 }); await loadStatus(); }
    catch (e) { alert(e.msg || "专注模式启动失败"); }
  });
  loadStatus();
  setInterval(() => { loadStatus(true); }, 5000);
}

function bindTabs() {
  document.querySelectorAll(".tabs button").forEach(btn => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".tabs button").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      document.querySelectorAll(".tab").forEach(t => t.classList.remove("active"));
      el("tab-" + btn.dataset.tab).classList.add("active");
    });
  });
}

async function loadStatus(light) {
  try {
    status = await api("GET", "/api/status");
  } catch (e) { el("statusBadge").textContent = "不可达"; return; }
  paired = !!status.currentClientPaired;
  const badge = el("statusBadge");
  badge.textContent = status.moored ? "已拴牢" : "脱缰";
  badge.classList.toggle("moored", !!status.moored);
  el("readonlyBadge").classList.toggle("hidden", paired);
  el("btnFocus").classList.toggle("hidden", !paired);
  renderCooldown();
  renderFocus();
  if (light) return;
  renderStatus();
  renderApps();
  renderGroups();
  renderRules();
  renderSubs();
  renderDashboard();
}

function renderFocus() {
  el("focusBadge").classList.toggle("hidden", !status.focusActive);
  if (status.focusActive) {
    const m = Math.ceil(status.focusRemainingSeconds / 60);
    el("focusBadge").textContent = "专注中 " + m + " 分";
  }
}

function renderCooldown() {
  const bar = el("cooldownBar");
  const cd = status.activeCooldown;
  if (!cd) { bar.classList.add("hidden"); bar.innerHTML = ""; return; }
  const now = Date.now();
  const expiresAt = cd.expiresAt;
  const windowStart = expiresAt;
  const windowEnd = expiresAt + 120000;
  let html;
  if (now < windowStart) {
    const remainMin = Math.max(1, Math.ceil((windowStart - now) / 60000));
    html = `<span>冷静期进行中，约 ${remainMin} 分钟后进入确认窗口（可修改时限为 5-30 分钟）。</span>
      <button id="btnCdCancel">取消</button>`;
  } else if (now <= windowEnd) {
    const remainSec = Math.max(0, Math.ceil((windowEnd - now) / 1000));
    html = `<span>冷静期已到期，${remainSec} 秒内点击"确认生效"应用修改，超时作废。</span>
      <button id="btnCdConfirm">确认生效</button>
      <button id="btnCdCancel">取消</button>`;
  } else {
    html = `<span>冷静期已超时作废。</span>`;
  }
  bar.innerHTML = html;
  bar.classList.remove("hidden");
  const confirmBtn = el("btnCdConfirm");
  if (confirmBtn) confirmBtn.addEventListener("click", async () => {
    if (!ensureWrite()) return;
    try { await api("POST", "/api/cooldown/" + encodeURIComponent(cd.id) + "/confirm"); await loadStatus(); }
    catch (e) { alert(e.msg || "确认失败"); await loadStatus(); }
  });
  const cancelBtn = el("btnCdCancel");
  if (cancelBtn) cancelBtn.addEventListener("click", async () => {
    if (!ensureWrite()) return;
    try { await api("POST", "/api/cooldown/" + encodeURIComponent(cd.id) + "/cancel"); await loadStatus(); }
    catch (e) { alert(e.msg || "取消失败"); }
  });
}

/* ---------------- 看板 ---------------- */

let dashboardCharts = {};

function renderDashboard() {
  const t = el("tab-dashboard");
  const targets = status.targets || [];
  const enabled = targets.filter(x => x.enabled);
  const today = status.today || {};
  const bonuses = status.todayBonuses || {};

  let html = `<div class="section-title">今日用量</div>
    <canvas id="chartToday" height="120"></canvas>`;
  html += `<div class="section-title">近 7 天总用量（分钟）</div>
    <canvas id="chart7d" height="120"></canvas>`;
  html += `<div class="section-title">事件流水（最近 50 条）</div>
    <div id="eventStream" class="muted"></div>`;
  html += `<div class="section-title">导出</div>
    <button id="btnCsv" class="secondary">导出今日用量 CSV</button>`;
  t.innerHTML = html;

  // 今日柱状图
  const todayLabels = enabled.map(x => x.label);
  const todayVals = enabled.map(x => Math.round((today[x.targetId] || 0) / 60));
  if (window.Chart) {
    if (dashboardCharts.today) dashboardCharts.today.destroy();
    dashboardCharts.today = new Chart(el("chartToday"), {
      type: "bar",
      data: { labels: todayLabels, datasets: [{ label: "分钟", data: todayVals, backgroundColor: "#3B5BDB" }] },
      options: { responsive: true, plugins: { legend: { display: false } } }
    });
  }

  // 7 天序列
  api("GET", "/api/usage?days=7").then(series => {
    const days = (series.days || []).map(d => d.date);
    const totals = (series.days || []).map(d => Math.round(Object.values(d.targets || {}).reduce((a, b) => a + b, 0) / 60));
    if (window.Chart && el("chart7d")) {
      if (dashboardCharts.seven) dashboardCharts.seven.destroy();
      dashboardCharts.seven = new Chart(el("chart7d"), {
        type: "line",
        data: { labels: days, datasets: [{ label: "分钟", data: totals, borderColor: "#30A46C", tension: .3 }] },
        options: { responsive: true, plugins: { legend: { display: false } } }
      });
    }
  });

  // 事件流
  api("GET", "/api/events?limit=50").then(events => {
    const stream = el("eventStream");
    if (!stream) return;
    stream.innerHTML = (events || []).map(e =>
      `<div class="row"><span class="label">${new Date(e.ts).toLocaleTimeString()} ${esc(e.type)} ${esc(e.targetId || "")} ${esc(e.detailJson || "")}</span></div>`
    ).join("") || "暂无事件";
  });

  // CSV
  el("btnCsv").addEventListener("click", () => {
    const rows = [["target", "label", "used_minutes", "bonus_minutes"]];
    enabled.forEach(x => rows.push([x.targetId, x.label, Math.round((today[x.targetId] || 0) / 60), Math.round((bonuses[x.targetId] || 0) / 60)]));
    const csv = rows.map(r => r.map(v => `"${String(v).replace(/"/g, '""')}"`).join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = "mooring_usage_today.csv";
    a.click();
  });
}

function ensureWrite() {
  if (!paired) { alert("当前浏览器为只读模式。请在电脑浏览器完成配对后操作。"); return false; }
  return true;
}

/* ---------------- 状态 ---------------- */

function renderStatus() {
  const t = el("tab-status");
  const targets = status.targets || [];
  const enabled = targets.filter(x => x.enabled);
  let html = `<div class="status-cards">
    <div class="stat"><div class="num">${status.moored ? "已拴牢" : "脱缰"}</div><div class="cap">服务状态</div></div>
    <div class="stat"><div class="num">${status.accessibilityEnabled ? "开" : "关"}</div><div class="cap">无障碍</div></div>
    <div class="stat"><div class="num">${status.pairedClients}</div><div class="cap">已配对浏览器（上限 3）</div></div>
    <div class="stat"><div class="num">${enabled.length}</div><div class="cap">已启用目标</div></div>
  </div>`;
  html += `<div class="section-title">今日用量</div>`;
  const today = status.today || {};
  const groupsToday = status.groupsToday || {};
  if (targets.length === 0) {
    html += `<div class="row muted">尚未启用任何目标，前往「应用」页启用。</div>`;
  } else {
    targets.filter(x => x.enabled || (today[x.targetId] ?? 0) > 0).forEach(t => {
      html += `<div class="row"><span class="label">${esc(t.label)} <span class="sub">${esc(t.packageName)}</span></span><span>${fmtSec(today[t.targetId])}</span></div>`;
    });
  }
  if (Object.keys(groupsToday).length) {
    html += `<div class="section-title">组用量</div>`;
    Object.entries(groupsToday).forEach(([gid, sec]) => {
      const g = (status.groups || []).find(x => x.id === gid);
      html += `<div class="row"><span class="label">${esc(g ? g.name : gid)}</span><span>${fmtSec(sec)}</span></div>`;
    });
  }
  t.innerHTML = html;
}

/* ---------------- 应用 ---------------- */

async function renderApps() {
  const t = el("tab-apps");
  const [targets, catalog, installed] = await Promise.all([
    api("GET", "/api/targets"),
    api("GET", "/api/catalog"),
    api("GET", "/api/apps/installed"),
  ]);
  const byPkg = {};
  targets.forEach(x => byPkg[x.packageName] = x);
  const catByPkg = {};
  catalog.forEach(c => catByPkg[c.packageName] = c);

  let html = `<div class="section-title">内置目录一键启用</div>`;
  catalog.forEach(c => {
    const tgt = byPkg[c.packageName];
    const on = !!(tgt && tgt.enabled);
    html += `<div class="row">
      <span class="label">${esc(c.label)} <span class="sub">${esc(c.packageName)}</span></span>
      ${on ? `<button class="secondary" data-act="unlock" data-tid="${esc(tgt.targetId)}">+15 解锁</button>` : ""}
      <button class="${on ? "danger" : ""}" data-act="toggle-cat" data-pkg="${esc(c.packageName)}" data-label="${esc(c.label)}">${on ? "停用" : "启用"}</button>
    </div>`;
  });

  html += `<div class="section-title">已安装应用（未管控提醒区）</div>`;
  const unmanaged = installed.filter(a => !byPkg[a.packageName] || !byPkg[a.packageName].enabled);
  if (unmanaged.length === 0) {
    html += `<div class="row muted">无未管控应用</div>`;
  } else {
    unmanaged.slice(0, 30).forEach(a => {
      html += `<div class="row">
        <span class="label">${esc(a.label)} <span class="sub">${esc(a.packageName)}</span></span>
        <button data-act="enable-manual" data-pkg="${esc(a.packageName)}" data-label="${esc(a.label)}">启用</button>
      </div>`;
    });
  }

  html += `<div class="section-title">手动添加</div>
    <div class="manual"><input id="manualPkg" placeholder="包名，如 com.example.app"><input id="manualQuota" type="number" placeholder="每日配额(分钟)" style="width:120px"><button id="btnManualAdd">添加并启用</button></div>`;

  t.innerHTML = html;

  document.querySelectorAll("#tab-apps [data-act=toggle-cat], #tab-apps [data-act=enable-manual]").forEach(btn => {
    btn.addEventListener("click", async () => {
      if (!ensureWrite()) return;
      const pkg = btn.dataset.pkg, label = btn.dataset.label;
      const tgt = byPkg[pkg];
      try {
        if (tgt && tgt.enabled) {
          await api("DELETE", "/api/targets/" + encodeURIComponent(tgt.targetId));
        } else {
          await api("POST", "/api/targets", { packageName: pkg, label, kind: "APP" });
        }
        await loadStatus();
      } catch (e) { alert(e.msg || "操作失败"); }
    });
  });
  document.querySelectorAll("#tab-apps [data-act=unlock]").forEach(btn => {
    btn.addEventListener("click", async () => {
      if (!ensureWrite()) return;
      try {
        await api("POST", "/api/unlock", { targetId: btn.dataset.tid, minutes: 15 });
        await loadStatus();
        alert("已发起临时解锁申请，请等待冷静期到期后确认。");
      } catch (e) { alert(e.msg || "解锁申请失败"); }
    });
  });
  el("btnManualAdd").addEventListener("click", async () => {
    if (!ensureWrite()) return;
    const pkg = el("manualPkg").value.trim();
    const quota = parseInt(el("manualQuota").value, 10);
    if (!pkg) return alert("请输入包名");
    try {
      await api("POST", "/api/targets", { packageName: pkg, label: pkg, kind: "APP", quotaMinutes: quota > 0 ? quota : null });
      await loadStatus();
    } catch (e) { alert(e.msg || "操作失败"); }
  });
}

/* ---------------- 组 ---------------- */

async function renderGroups() {
  const t = el("tab-groups");
  const [groups, targets] = await Promise.all([api("GET", "/api/groups"), api("GET", "/api/targets")]);
  let html = `<div class="section-title">应用组</div>`;
  if (groups.length === 0) html += `<div class="row muted">暂无组。</div>`;
  groups.forEach(g => {
    const members = targets.filter(x => x.groupId === g.id);
    html += `<div class="row">
      <span class="label">${esc(g.name)} <span class="sub">${members.map(m => esc(m.label)).join("、") || "空"}</span></span>
      <button class="secondary" data-del-group="${esc(g.id)}">删除</button>
    </div>`;
  });
  html += `<div class="section-title">新建组</div>
    <div class="manual"><input id="newGroupName" placeholder="组名，如 视频组"><input id="newGroupQuota" type="number" placeholder="组配额(分钟)"><button id="btnAddGroup">创建</button></div>`;
  t.innerHTML = html;
  el("btnAddGroup").addEventListener("click", async () => {
    if (!ensureWrite()) return;
    const name = el("newGroupName").value.trim();
    const quota = parseInt(el("newGroupQuota").value, 10);
    if (!name) return alert("请输入组名");
    try { await api("POST", "/api/groups", { name, quotaMinutes: quota > 0 ? quota : null }); await loadStatus(); }
    catch (e) { alert(e.msg || "操作失败"); }
  });
  document.querySelectorAll("#tab-groups [data-del-group]").forEach(btn => {
    btn.addEventListener("click", async () => {
      if (!ensureWrite()) return;
      try { await api("DELETE", "/api/groups/" + encodeURIComponent(btn.dataset.delGroup)); await loadStatus(); }
      catch (e) { alert(e.msg || "操作失败"); }
    });
  });
}

/* ---------------- 规则 ---------------- */

async function renderRules() {
  const t = el("tab-rules");
  const [rules, targets, groups] = await Promise.all([api("GET", "/api/rules"), api("GET", "/api/targets"), api("GET", "/api/groups")]);
  const label = id => {
    if (id.startsWith("GROUP:")) { const g = groups.find(x => "GROUP:" + x.id === id); return g ? g.name + "（组）" : id; }
    const tg = targets.find(x => x.targetId === id); return tg ? tg.label : id;
  };
  const typeName = { "DAILY_QUOTA": "每日配额", "SCHEDULE_BLOCK": "时段禁用", "ALWAYS_BLOCK": "永久禁用" };
  let html = `<div class="section-title">规则列表</div>`;
  if (rules.length === 0) html += `<div class="row muted">暂无规则。</div>`;
  rules.forEach(r => {
    const extra = r.type === "DAILY_QUOTA" ? ` ${r.quotaMinutes} 分钟`
      : r.type === "SCHEDULE_BLOCK" ? ` ${String(r.startHHmm).padStart(4, "0")}–${String(r.endHHmm).padStart(4, "0")}`
      : "";
    html += `<div class="row">
      <span class="label">${esc(label(r.targetId))} <span class="sub">${esc(typeName[r.type] || r.type)}${esc(extra)}</span></span>
      <button class="danger" data-del-rule="${esc(r.id)}">删除</button>
    </div>`;
  });
  html += `<div class="section-title">新增规则</div>
    <div class="manual">
      <select id="newRuleTarget">
        ${targets.filter(x => x.enabled).map(x => `<option value="${esc(x.targetId)}">${esc(x.label)}</option>`).join("")}
        ${groups.map(g => `<option value="GROUP:${esc(g.id)}">${esc(g.name)}（组）</option>`).join("")}
      </select>
      <select id="newRuleType">
        <option value="DAILY_QUOTA">每日配额</option>
        <option value="SCHEDULE_BLOCK">时段禁用</option>
        <option value="ALWAYS_BLOCK">永久禁用</option>
      </select>
    </div>
    <div class="manual" id="ruleParams" style="margin-top:8px">
      <input id="newRuleQuota" type="number" placeholder="配额(分钟)">
      <input id="newRuleStart" type="number" placeholder="开始 HHmm，如 900" style="width:130px">
      <input id="newRuleEnd" type="number" placeholder="结束 HHmm，如 2200" style="width:130px">
    </div>
    <div class="manual" style="margin-top:8px"><button id="btnAddRule">添加规则</button></div>`;
  t.innerHTML = html;
  el("btnAddRule").addEventListener("click", async () => {
    if (!ensureWrite()) return;
    const targetId = el("newRuleTarget").value;
    const type = el("newRuleType").value;
    try {
      await api("POST", "/api/rules", {
        targetId,
        type,
        quotaMinutes: type === "DAILY_QUOTA" ? parseInt(el("newRuleQuota").value, 10) : null,
        startHHmm: type === "SCHEDULE_BLOCK" ? parseInt(el("newRuleStart").value, 10) : null,
        endHHmm: type === "SCHEDULE_BLOCK" ? parseInt(el("newRuleEnd").value, 10) : null,
      });
      await loadStatus();
    } catch (e) { alert(e.msg || "操作失败"); }
  });
  document.querySelectorAll("#tab-rules [data-del-rule]").forEach(btn => {
    btn.addEventListener("click", async () => {
      if (!ensureWrite()) return;
      try { await api("DELETE", "/api/rules/" + encodeURIComponent(btn.dataset.delRule)); await loadStatus(); }
      catch (e) { alert(e.msg || "操作失败"); }
    });
  });
}

/* ---------------- 订阅 ---------------- */

async function renderSubs() {
  const t = el("tab-subs");
  const subs = await api("GET", "/api/subscriptions");
  let html = `<div class="section-title">导入订阅（T2 特征）</div>
    <textarea id="subText" placeholder="粘贴订阅 JSON"></textarea>
    <div class="manual" style="margin-top:8px"><button id="btnImportSub">导入订阅</button></div>`;
  html += `<div class="section-title">已导入订阅</div>`;
  if (subs.length === 0) html += `<div class="row muted">暂无订阅。</div>`;
  subs.forEach(s => {
    html += `<div class="row">
      <span class="label">${esc(s.name)} <span class="sub">v${s.version} · ${s.enabled ? "启用" : "停用"}</span></span>
      <button class="${s.enabled ? "danger" : ""}" data-toggle-sub="${esc(s.id)}" data-enabled="${s.enabled}">${s.enabled ? "停用" : "启用"}</button>
    </div>`;
  });
  t.innerHTML = html;
  el("btnImportSub").addEventListener("click", async () => {
    if (!ensureWrite()) return;
    const text = el("subText").value.trim();
    if (!text) return alert("请粘贴订阅 JSON");
    try {
      await api("POST", "/api/subscriptions", undefined, text);
      await loadStatus();
      alert("订阅导入成功，已热生效");
    } catch (e) { alert(e.msg || "导入失败"); }
  });
  document.querySelectorAll("#tab-subs [data-toggle-sub]").forEach(btn => {
    btn.addEventListener("click", async () => {
      if (!ensureWrite()) return;
      try {
        await api("PUT", "/api/subscriptions/" + encodeURIComponent(btn.dataset.toggleSub), { enabled: btn.dataset.enabled === "false" });
        await loadStatus();
      } catch (e) { alert(e.msg || "操作失败"); }
    });
  });
}

/* ---------------- 启动 ---------------- */

async function boot() {
  if (localStorage.getItem(TOKEN_KEY)) {
    showMain();
  } else {
    el("pairView").classList.remove("hidden");
  }
  el("btnScan").addEventListener("click", startScan);
  el("btnPairManual").addEventListener("click", async () => {
    const token = el("tokenInput").value.trim();
    if (!token) return;
    el("pairMsg").textContent = "";
    try { await pairWithToken(token); }
    catch (e) {
      el("pairMsg").textContent = "配对失败：" + (e.msg || e.message);
      el("pairMsg").classList.add("error");
    }
  });
}
boot();
