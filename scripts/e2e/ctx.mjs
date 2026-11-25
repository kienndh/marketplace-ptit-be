// Ngữ cảnh chung cho các kịch bản có đăng nhập: token 3 tài khoản, đặt role ADMIN trong marketplace_java_e2e, WS helper.
import { execSync } from 'node:child_process';
import { tokens } from './accounts.mjs'; import { http, BASE } from './lib.mjs';
export const T = await tokens();
export const tk = Object.fromEntries(Object.entries(T).map(([k,v])=>[k,v.idToken]));
export function psql(sql) { return execSync(`docker exec -i sm_postgres psql -U postgres -d marketplace_java_e2e -At -c "${sql.replace(/"/g,'\\"')}"`,{encoding:'utf8'}).trim(); }
export async function bootstrap() {
  const me = {}; for (const r of ['seller','buyer','admin']) { const x = await http('GET','/users/me',{token:tk[r]}); me[r]=x.json; if (x.status!==200) throw new Error(`${r} /users/me ${x.status} ${JSON.stringify(x.json)}`); }
  psql(`update users set role='ADMIN' where user_id=${me.admin.user_id}`);
  return me;
}
// WebSocket ghi sự kiện
export function wsListen(path, token) {
  const events = []; const url = BASE.replace(/^http/,'ws') + path + (token ? `?token=${token}` : '');
  const ws = new WebSocket(url); const opened = new Promise((res,rej)=>{ ws.onopen=()=>res(true); ws.onerror=e=>rej(new Error('ws error')); });
  ws.onmessage = e => { try { events.push(JSON.parse(e.data)); } catch { events.push({raw:String(e.data)}); } };
  ws.onclose = e => events.push({__closed:e.code});
  return { ws, events, opened };
}
export const sleep = ms => new Promise(r=>setTimeout(r,ms));
