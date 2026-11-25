// Quét lời gọi API của FE (chỉ đọc) và đối chiếu với docs/ROUTE_PARITY.md của Java.
// Chạy: node scripts/e2e/fe_routes.mjs [đường dẫn FE]
import fs from 'node:fs'; import path from 'node:path';
const fe = process.argv[2] || path.resolve('../student-marketplace-fe');
const parity = fs.readFileSync('docs/ROUTE_PARITY.md', 'utf8');
const javaRoutes = new Set();
for (const l of parity.split('\n')) { const m = l.match(/\|\s*(\w+)\s*\|\s*(GET|POST|PUT|PATCH|DELETE)\s*\|\s*`([^`]+)`/); if (m && m[1] === 'Done') javaRoutes.add(`${m[2]} ${m[3]}`); }
const files = [];
(function walk(d){ for (const e of fs.readdirSync(d,{withFileTypes:true})) { const p=path.join(d,e.name); if (e.isDirectory()) { if (e.name!=='__tests__') walk(p);} else if (/\.(ts|tsx)$/.test(e.name)) files.push(p);} })(path.join(fe,'src'));
const re = /\b(?:apiClient|api|axios)\s*\.\s*(get|post|put|patch|delete)\s*(?:<[^()]*?>)?\s*\(\s*([`'"])((?:(?!\2)[\s\S])*?)\2/g;
const found = new Map();
for (const f of files) { const s = fs.readFileSync(f,'utf8'); let m; while ((m = re.exec(s))) {
  let p = m[3].replace(/\$\{[^}]*\}/g,'{}').split('?')[0].replace(/\{\}\{\}/g,'{}');
  if (!p.startsWith('/')) continue;
  if (!p.startsWith('/api/v1')) p = '/api/v1' + p;
  const k = `${m[1].toUpperCase()} ${p.replace(/\/$/,'')}`; (found.get(k) || found.set(k,[]).get(k)).push(path.relative(fe,f)); } }
const miss = [];
for (const [k, fs_] of [...found].sort()) { if (!javaRoutes.has(k)) miss.push(`${k}   <- ${[...new Set(fs_)].join(', ')}`); }
console.log(`FE gọi ${found.size} (method,path) khác nhau; Java có ${javaRoutes.size} route.`);
console.log(`FE gọi mà Java không khớp (${miss.length}):`); miss.forEach(x=>console.log('  '+x));
fs.writeFileSync(process.env.OUT || 'fe_routes.json', JSON.stringify([...found.keys()].sort(), null, 1));
