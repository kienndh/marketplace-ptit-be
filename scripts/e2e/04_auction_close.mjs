// Đấu giá: 2 người đặt giá → hết hạn → worker chốt → sự kiện AUCTION_ENDED → won → checkout. Cần worker bật (start-java.ps1).
import { step, record, png, fileForm, http } from './lib.mjs';
import { tk, bootstrap, wsListen, sleep, psql } from './ctx.mjs';
await bootstrap();
const F = 'auction-close'; const ok = [200, 201];
const c = await step(F,'create product','POST','/products',{token:tk.seller,body:{category_id:1,title:'E2E auction close',description:'e2e',listing_type:'AUCTION',price:100000,stock:1}},{expect:ok});
const pid = c.json.product_id;
await step(F,'image','POST',`/products/${pid}/images/upload`,{token:tk.seller,form:fileForm({},[['file','a.png',png(),'image/png']])},{expect:ok});
await step(F,'submit','POST',`/products/${pid}/submit-review`,{token:tk.seller});
await step(F,'approve','POST',`/admin/product-approvals/${pid}/decision`,{token:tk.admin,body:{decision:'APPROVE'}});
const a = await step(F,'create auction','POST','/auctions',{token:tk.seller,body:{product_id:pid,bid_step:10000,duration_minutes:60}},{expect:ok,type:'AuctionDetailResponse'});
const aid = a.json.auction_id;
const ws = wsListen(`/ws/auctions/${aid}`); await ws.opened;
await step(F,'bid buyer','POST',`/auctions/${aid}/bids`,{token:tk.buyer,body:{amount:110000}},{expect:ok,type:'AuctionBidResponse'});
await step(F,'bid admin','POST',`/auctions/${aid}/bids`,{token:tk.admin,body:{amount:130000}},{expect:ok,type:'AuctionBidResponse'});
psql(`update auctions set starts_at = (now() at time zone 'utc') - interval '2 hours', ends_at = (now() at time zone 'utc') - interval '1 minute' where auction_id=${aid}`);
let status; for (let i = 0; i < 20; i++) { await sleep(3000); status = psql(`select status from auctions where auction_id=${aid}`); if (status === 'ENDED') break; }
record(F,'worker chốt phiên (ENDED)',status==='ENDED',`status=${status}`);
await sleep(500);
const evs = ws.events.map(e => e.event || e.__closed);
record(F,'WS nhận BID_PLACED + AUCTION_ENDED',evs.includes('BID_PLACED') && evs.includes('AUCTION_ENDED'),evs.join(','));
const ended = ws.events.find(e => e.event === 'AUCTION_ENDED'); console.log('AUCTION_ENDED payload:', JSON.stringify(ended));
await step(F,'detail ended','GET',`/auctions/${aid}`,{},{type:'AuctionDetailResponse'});
await step(F,'won list (admin thắng)','GET','/auctions/me/won',{token:tk.admin},{type:'WonAuctionResponse[]'});
await step(F,'checkout','POST',`/auctions/${aid}/checkout`,{token:tk.admin,body:{payment_method:'CASH_ON_MEETUP',fulfillment_method:'MEETUP',meetup_place:'PTIT',contact_name:'Admin',contact_phone:'0911223344'}},{expect:ok,type:'OrderDetailResponse'});
const nl = await step(F,'notifications admin','GET','/notifications',{token:tk.admin},{type:'NotificationListResponse'});
record(F,'notification đấu giá',(nl.json.items||[]).some(n=>/AUCTION/.test(n.type)),(nl.json.items||[]).map(n=>n.type).slice(0,6).join(','));
ws.ws.close(); process.exit(0);
