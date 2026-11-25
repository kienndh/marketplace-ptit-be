// Thư viện dùng chung cho kịch bản E2E: HTTP, kiểm kiểu theo types.ts của FE (chỉ đọc), báo kết quả.
import fs from 'node:fs'; import path from 'node:path';
export const BASE = process.env.JAVA_BASE || 'http://localhost:8081';
const FE = process.env.FE_DIR || path.resolve('../student-marketplace-fe');

// ---------- Parser tối giản cho interface/type trong types.ts ----------
const ifaces = new Map(), aliases = new Map();
function splitTop(s, sep) { const out=[]; let d=0, cur='', q=null; for (const ch of s) { if (q) { cur+=ch; if (ch===q) q=null; continue; } if (ch==="'"||ch==='"') { q=ch; cur+=ch; continue; } if ('<({['.includes(ch)) d++; if ('>)}]'.includes(ch)) d--; if (ch===sep && d===0) { out.push(cur); cur=''; } else cur+=ch; } if (cur.trim()) out.push(cur); return out; }
function parseFile(f) {
  const src = fs.readFileSync(f,'utf8').replace(/\/\*[\s\S]*?\*\//g,'').replace(/^\s*\/\/.*$/gm,'');
  const reI = /export\s+interface\s+(\w+)(?:\s+extends\s+([\w, ]+))?\s*\{/g; let m;
  while ((m = reI.exec(src))) { let i = reI.lastIndex, d = 1; const st = i; while (d && i < src.length) { if (src[i]==='{') d++; else if (src[i]==='}') d--; i++; } ifaces.set(m[1], { ext: (m[2]||'').split(',').map(x=>x.trim()).filter(Boolean), body: src.slice(st, i-1) }); }
  const reT = /export\s+type\s+(\w+)\s*=\s*([^;]*?);/gs; while ((m = reT.exec(src))) aliases.set(m[1], m[2].trim());
}
for (const d of fs.readdirSync(path.join(FE,'src/features'))) { const f = path.join(FE,'src/features',d,'types.ts'); if (fs.existsSync(f)) parseFile(f); }
function fieldsOf(name) { const it = ifaces.get(name); if (!it) return null; const fields = {}; for (const e of it.ext) Object.assign(fields, fieldsOf(e)||{});
  // tách field ở top-level theo ; hoặc xuống dòng
  const body = it.body.replace(/\n\s*\|/g,' |').replace(/\n(\s*)([\w'"]+\??\s*:)/g, ';\n$1$2');
  for (const part of splitTop(body, ';')) { const mm = part.trim().match(/^(\w+)(\?)?\s*:\s*([\s\S]+)$/); if (mm) fields[mm[1]] = { opt: !!mm[2], type: mm[3].trim() }; }
  return fields; }
export function checkType(val, type, p, errs, depth=0) {
  type = type.trim(); if (depth > 12) return;
  const parts = splitTop(type, '|').map(s=>s.trim()).filter(Boolean);
  if (parts.length > 1) { const ee = []; for (const t of parts) { const e = []; checkType(val, t, p, e, depth+1); if (!e.length) return; ee.push(...e); } errs.push(`${p}: không khớp union (${type.slice(0,60)}) giá trị=${JSON.stringify(val)?.slice(0,60)}`); return; }
  if (type.startsWith('(') && type.endsWith(')')) return checkType(val, type.slice(1,-1), p, errs, depth+1);
  if (type === 'null') { if (val !== null) errs.push(`${p}: kỳ vọng null`); return; }
  if (type === 'undefined') { if (val !== undefined) errs.push(`${p}: kỳ vọng undefined`); return; }
  if (type === 'string') { if (typeof val !== 'string') errs.push(`${p}: kỳ vọng string, có ${val===null?'null':typeof val}`); return; }
  if (type === 'number') { if (typeof val !== 'number') errs.push(`${p}: kỳ vọng number, có ${val===null?'null':typeof val}`); return; }
  if (type === 'boolean') { if (typeof val !== 'boolean') errs.push(`${p}: kỳ vọng boolean, có ${val===null?'null':typeof val}`); return; }
  if (type === 'unknown' || type === 'any' || type.startsWith('Record<')) return;
  if (/^['"].*['"]$/.test(type)) { if (val !== type.slice(1,-1)) errs.push(`${p}: kỳ vọng ${type}, có ${JSON.stringify(val)}`); return; }
  if (type.endsWith('[]')) { if (!Array.isArray(val)) { errs.push(`${p}: kỳ vọng mảng`); return; } val.slice(0,3).forEach((v,i)=>checkType(v, type.slice(0,-2), `${p}[${i}]`, errs, depth+1)); return; }
  const arr = type.match(/^Array<(.+)>$/); if (arr) return checkType(val, arr[1]+'[]', p, errs, depth+1);
  if (type.startsWith('{')) { if (val===null||typeof val!=='object') { errs.push(`${p}: kỳ vọng object`); return; } const fake = { ext: [], body: type.slice(1,-1) }; ifaces.set('__inline', fake); return checkObj(val, '__inline', p, errs, depth+1); }
  const nm = type.replace(/<.*$/,''); if (aliases.has(nm)) return checkType(val, aliases.get(nm), p, errs, depth+1);
  if (ifaces.has(nm)) { if (val===null||typeof val!=='object') { errs.push(`${p}: kỳ vọng object ${nm}, có ${val===null?'null':typeof val}`); return; } return checkObj(val, nm, p, errs, depth+1); }
}
function checkObj(val, name, p, errs, depth) { const fields = fieldsOf(name); if (!fields) return; for (const [k, f] of Object.entries(fields)) { if (!(k in val) || val[k] === undefined) { if (!f.opt) errs.push(`${p}.${k}: THIẾU (kiểu ${f.type.slice(0,50)})`); continue; } checkType(val[k], f.type, `${p}.${k}`, errs, depth+1); } }

// ---------- HTTP + báo cáo ----------
export const results = [];
export async function http(method, url, { token, body, form, headers = {} } = {}) {
  const h = { ...headers }; if (token) h.Authorization = `Bearer ${token}`;
  let b; if (form) b = form; else if (body !== undefined) { h['Content-Type'] = 'application/json'; b = JSON.stringify(body); }
  const r = await fetch(BASE + '/api/v1' + url, { method, headers: h, body: b, redirect: 'manual' });
  const text = await r.text(); let json; try { json = JSON.parse(text); } catch { json = text; }
  return { status: r.status, json, headers: r.headers };
}
export function record(flow, step, ok, note='') { results.push({ flow, step, ok, note }); console.log(`${ok?'PASS':'FAIL'}  [${flow}] ${step}${note?'  — '+note:''}`); }
// Gọi + kiểm status + kiểm kiểu FE
export async function step(flow, label, method, url, opts = {}, { expect = [200], type } = {}) {
  let r; try { r = await http(method, url, opts); } catch (e) { record(flow, label, false, 'lỗi mạng ' + e.message); return { status: 0, json: null }; }
  const exp = Array.isArray(expect) ? expect : [expect]; const errs = [];
  if (!exp.includes(r.status)) errs.push(`HTTP ${r.status} (kỳ vọng ${exp}) ${typeof r.json==='string'?r.json.slice(0,120):JSON.stringify(r.json).slice(0,200)}`);
  else if (type && r.status < 300) checkType(r.json, type, 'body', errs);
  record(flow, `${method} ${url.split('?')[0]}  ${label}`, !errs.length, errs.slice(0,4).join(' | '));
  return r;
}
export function png(color=0) { // PNG 1x1 hợp lệ
  const b = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAAFUlEQVR42mP8z8BQz0AEYBxVSF+FABJADveWkH6oAAAAAElFTkSuQmCC','base64'); return b; }
export function fileForm(fields, files) { const f = new FormData(); for (const [k,v] of Object.entries(fields||{})) f.append(k, String(v)); for (const [k,name,buf,mime] of files||[]) f.append(k, new Blob([buf],{type:mime}), name); return f; }
