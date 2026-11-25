// Tạo/đăng nhập 3 tài khoản test (Email/Password) trong Firebase DEV qua REST. Không in mật khẩu/token.
// Env: E2E_CREDS (file json lưu mật khẩu ngoài repo), FE_ENV (đường dẫn .env.local của FE, chỉ đọc).
import fs from 'node:fs'; import crypto from 'node:crypto';
const FE_ENV = process.env.FE_ENV || '../student-marketplace-fe/.env.local';
const CREDS = process.env.E2E_CREDS;
const env = Object.fromEntries(fs.readFileSync(FE_ENV,'utf8').split(/\r?\n/).filter(l=>l.includes('=')&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return [l.slice(0,i).trim(), l.slice(i+1).trim().replace(/^["']|["']$/g,'')];}));
const KEY = env.VITE_FIREBASE_DEV_API_KEY; export const PROJECT = env.VITE_FIREBASE_DEV_PROJECT_ID;
const call = async (ep, body) => { const r = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:${ep}?key=${KEY}`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}); return [r.status, await r.json()]; };
const names = { seller:'e2e.seller', buyer:'e2e.buyer', admin:'e2e.admin' };
export async function tokens() {
  const creds = fs.existsSync(CREDS) ? JSON.parse(fs.readFileSync(CREDS,'utf8')) : {};
  const out = {};
  for (const [role, local] of Object.entries(names)) {
    let c = creds[role]; if (!c) { c = creds[role] = { email: `${local}@example.com`, password: crypto.randomBytes(12).toString('base64url')+'A1!' }; fs.writeFileSync(CREDS, JSON.stringify(creds)); }
    let [st, j] = await call('signInWithPassword', { email:c.email, password:c.password, returnSecureToken:true });
    if (st !== 200) { [st, j] = await call('signUp', { email:c.email, password:c.password, returnSecureToken:true }); }
    if (st !== 200) throw new Error(`${role}: Firebase ${st} ${j.error?.message}`);
    out[role] = { idToken: j.idToken, uid: j.localId, email: c.email };
  }
  return out;
}

if (process.argv[1]?.endsWith('accounts.mjs')) { const t = await tokens(); for (const [k,v] of Object.entries(t)) console.log(k, v.email, 'uid=', v.uid, 'token_len=', v.idToken.length); }