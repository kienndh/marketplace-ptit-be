import fs from 'node:fs';
import { step, record, png, fileForm } from './lib.mjs';
import { tk, bootstrap, wsListen, sleep } from './ctx.mjs';
const me = await bootstrap();
const st = {};
// ---- Hồ sơ
let F = 'profile';
await step(F,'me seller','GET','/users/me',{token:tk.seller},{type:'UserResponse'});
await step(F,'access-status','GET','/users/me/access-status',{token:tk.seller},{type:'UserAccessStatusResponse'});
for (const [r,n] of [['seller','Seller E2E'],['buyer','Buyer E2E'],['admin','Admin E2E']])
  await step(F,`update ${r}`,'PATCH','/users/me',{token:tk[r],body:{full_name:n,phone:'09'+(r==='seller'?'12345678':r==='buyer'?'87654321':'11223344'),bio:'e2e',delivery_area:'Ha Noi',default_meetup_area:'PTIT'}},{type:'UserResponse'});
const banks = await step(F,'banks','GET','/users/banks',{},{type:'SupportedBank[]'});
await step(F,'put bank','PUT','/users/me/bank-account',{token:tk.seller,body:{bank_bin:banks.json[0].bin,account_number:'0123456789',account_name:'NGUYEN VAN A'}},{type:'BankAccount'});
await step(F,'get bank','GET','/users/me/bank-account',{token:tk.seller},{type:'BankAccount'});
await step(F,'public profile','GET',`/users/${me.seller.public_id}`,{},{type:'PublicUserProfileResponse'});
await step(F,'sold products','GET',`/users/${me.seller.public_id}/sold-products`,{},{type:'SoldProductListResponse'});
await step(F,'users search','GET','/users/search?q=E2E',{token:tk.buyer},{});
// ---- Sản phẩm
F = 'product';
const mk = async (title, listing_type='FIXED_PRICE') => {
  const c = await step(F,`create ${listing_type}`,'POST','/products',{token:tk.seller,body:{category_id:1,title,description:'mo ta e2e',listing_type,price:100000,stock:listing_type==='AUCTION'?1:3}},{expect:[200,201],type:'ProductDetailResponse'});
  const id = c.json.product_id;
  await step(F,'upload image','POST',`/products/${id}/images/upload`,{token:tk.seller,form:fileForm({},[['file','a.png',png(),'image/png']])},{expect:[200,201],type:'ProductImageResponse'});
  await step(F,'submit-review','POST',`/products/${id}/submit-review`,{token:tk.seller},{type:'SellerProductResponse'});
  return id; };
st.pid = await mk('E2E Fixed item');
await step(F,'admin approvals list','GET','/admin/product-approvals',{token:tk.admin},{type:'AdminProductApprovalListResponse'});
await step(F,'approval detail','GET',`/admin/product-approvals/${st.pid}`,{token:tk.admin},{type:'AdminProductApprovalDetailResponse'});
await step(F,'approve','POST',`/admin/product-approvals/${st.pid}/decision`,{token:tk.admin,body:{decision:'APPROVE'}},{type:'AdminProductApprovalDetailResponse'});
await step(F,'list','GET','/products?limit=10',{},{type:'ProductListResponse'});
await step(F,'detail','GET',`/products/${st.pid}`,{},{type:'ProductDetailResponse'});
await step(F,'view','POST',`/products/${st.pid}/view`,{token:tk.buyer},{type:'ProductViewResponse'});
await step(F,'my products','GET','/products/me',{token:tk.seller},{type:'SellerProductListResponse'});
await step(F,'update','PATCH',`/products/${st.pid}`,{token:tk.seller,body:{description:'mo ta moi'}},{type:'ProductDetailResponse'});
await step(F,'fav add','POST',`/favorites/${st.pid}`,{token:tk.buyer},{expect:[200,201,204]});
await step(F,'fav list','GET','/favorites',{token:tk.buyer},{type:'FavoriteListResponse'});
await step(F,'fav ids','GET','/favorites/ids',{token:tk.buyer},{type:'FavoriteIdsResponse'});
await step(F,'fav remove','DELETE',`/favorites/${st.pid}`,{token:tk.buyer},{expect:[200,204]});
// ---- Đơn hàng + realtime
F = 'order';
const wsS = wsListen('/ws/me', tk.seller), wsB = wsListen('/ws/me', tk.buyer);
try { await Promise.all([wsS.opened, wsB.opened]); record(F,'WS /ws/me kết nối (seller+buyer)',true); } catch(e){ record(F,'WS /ws/me kết nối',false,e.message); }
const o = await step(F,'create','POST','/orders',{token:tk.buyer,body:{product_id:st.pid,quantity:1,payment_method:'BANK_TRANSFER',fulfillment_method:'MEETUP',meetup_place:'PTIT Ha Dong',contact_name:'Buyer',contact_phone:'0987654321',buyer_note:'e2e'}},{expect:[200,201],type:'OrderDetailResponse'});
st.oid = o.json?.order_id;
await step(F,'buying list','GET','/orders/me/buying',{token:tk.buyer},{type:'OrderCursorPage'});
await step(F,'selling list','GET','/orders/me/selling',{token:tk.seller},{type:'OrderCursorPage'});
await step(F,'detail','GET',`/orders/${st.oid}`,{token:tk.buyer},{type:'OrderDetailResponse'});
await step(F,'seller confirm','POST',`/orders/${st.oid}/confirm`,{token:tk.seller,body:{payment_note:'ck'}},{type:'OrderDetailResponse'});
await step(F,'edit terms','PATCH',`/orders/${st.oid}/transaction-terms`,{token:tk.seller,body:{meetup_place:'Cong PTIT',payment_note:'doi diem'}},{type:'OrderDetailResponse'});
await step(F,'receipt','POST',`/orders/${st.oid}/receipt`,{token:tk.buyer,form:fileForm({},[['file','r.png',png(),'image/png']])},{type:'OrderDetailResponse'});
await step(F,'confirm-payment','POST',`/orders/${st.oid}/confirm-payment`,{token:tk.seller},{type:'OrderDetailResponse'});
await step(F,'deliver MEETUP bị từ chối (đúng nghiệp vụ)','POST',`/orders/${st.oid}/deliver`,{token:tk.seller},{expect:[400]});
await step(F,'complete','POST',`/orders/${st.oid}/complete`,{token:tk.buyer},{type:'OrderDetailResponse'});
await sleep(1500);
const names = ev => ev.map(e=>e.type||JSON.stringify(e).slice(0,40));
const evS = names(wsS.events), evB = names(wsB.events);
record('realtime','seller nhận sự kiện /ws/me',evS.length>0,`${evS.length} sự kiện: ${[...new Set(evS)].join(',')}`);
record('realtime','buyer nhận sự kiện /ws/me',evB.length>0,`${evB.length} sự kiện: ${[...new Set(evB)].join(',')}`);
for (const k of ['seller','buyer']) await step('notification',`list ${k}`,'GET','/notifications',{token:tk[k]},{type:'NotificationCursorPage'});
wsS.ws.close(); wsB.ws.close();
fs.writeFileSync(process.env.E2E_STATE, JSON.stringify({...st,me}));
console.log(JSON.stringify(st));
process.exit(0);
