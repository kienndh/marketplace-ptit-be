import fs from 'node:fs';
import { step, record, png, fileForm, http } from './lib.mjs';
import { tk, bootstrap, wsListen, sleep } from './ctx.mjs';
const me = await bootstrap();
const st = JSON.parse(fs.readFileSync(process.env.E2E_STATE, 'utf8'));
const png1 = png();
const ids = n => me[n].user_id;
const ok = (c) => [200, 201];

// ---------- Đánh giá (đơn 90 đã COMPLETED ở kịch bản 02)
let F = 'review';
await step(F,'buyer review','POST',`/orders/${st.oid}/review`,{token:tk.buyer,body:{rating:5,comment:'tot'}},{expect:ok(),type:'OrderReviewResponse'});
await step(F,'get review','GET',`/orders/${st.oid}/review`,{token:tk.buyer},{type:'OrderReviewResponse'});
await step(F,'patch review','PATCH',`/orders/${st.oid}/review`,{token:tk.buyer,body:{rating:4,comment:'ok'}},{type:'OrderReviewResponse'});
await step(F,'seller reviews list','GET',`/users/${me.seller.public_id}/reviews`,{},{type:'PublicSellerReviewsCursorPage'});

// ---------- Đơn SHIPPING (deliver)
F = 'order-shipping';
const mkApproved = async (title, listing_type = 'FIXED_PRICE', stock = 3) => {
  const c = await step(F,`create ${listing_type}`,'POST','/products',{token:tk.seller,body:{category_id:1,title,description:'e2e',listing_type,price:200000,stock}},{expect:ok(),type:'ProductDetailResponse'});
  const id = c.json?.product_id;
  await step(F,'image','POST',`/products/${id}/images/upload`,{token:tk.seller,form:fileForm({},[['file','a.png',png1,'image/png']])},{expect:ok()});
  await step(F,'submit','POST',`/products/${id}/submit-review`,{token:tk.seller},{});
  await step(F,'approve','POST',`/admin/product-approvals/${id}/decision`,{token:tk.admin,body:{decision:'APPROVE'}},{});
  return id; };
const p2 = await mkApproved('E2E shipping item');
const o2 = await step(F,'create order SHIPPING','POST','/orders',{token:tk.buyer,body:{product_id:p2,quantity:1,payment_method:'CASH_ON_MEETUP',fulfillment_method:'SHIPPING',shipping_address:'1 Tran Phu Ha Dong',contact_name:'Buyer',contact_phone:'0987654321'}},{expect:ok(),type:'OrderDetailResponse'});
const oid2 = o2.json?.order_id;
await step(F,'confirm','POST',`/orders/${oid2}/confirm`,{token:tk.seller,body:{}},{type:'OrderDetailResponse'});
await step(F,'confirm-payment','POST',`/orders/${oid2}/confirm-payment`,{token:tk.seller},{type:'OrderDetailResponse'});
await step(F,'deliver','POST',`/orders/${oid2}/deliver`,{token:tk.seller},{type:'OrderDetailResponse'});
await step(F,'complete','POST',`/orders/${oid2}/complete`,{token:tk.buyer},{type:'OrderDetailResponse'});
const p3 = await mkApproved('E2E cancel item');
const o3 = await step(F,'create order (to cancel)','POST','/orders',{token:tk.buyer,body:{product_id:p3,quantity:1,payment_method:'CASH_ON_MEETUP',fulfillment_method:'MEETUP',meetup_place:'PTIT',contact_name:'Buyer',contact_phone:'0987654321'}},{expect:ok(),type:'OrderDetailResponse'});
await step(F,'cancel','POST',`/orders/${o3.json?.order_id}/cancel`,{token:tk.buyer,body:{cancel_reason:'doi y'}},{type:'OrderDetailResponse'});

// ---------- Đấu giá
F = 'auction';
const ap = await mkApproved('E2E auction item','AUCTION',1);
const a = await step(F,'create auction','POST','/auctions',{token:tk.seller,body:{product_id:ap,bid_step:10000,duration_minutes:60}},{expect:ok(),type:'AuctionDetailResponse'});
const aid = a.json?.auction_id; st.aid = aid;
const wsA = wsListen(`/ws/auctions/${aid}`);
try { await wsA.opened; record(F,'WS /ws/auctions/{id} kết nối',true); } catch(e) { record(F,'WS /ws/auctions/{id} kết nối',false,e.message); }
await step(F,'by-product','GET',`/auctions/by-product/${ap}`,{},{type:'AuctionDetailResponse'});
await step(F,'detail','GET',`/auctions/${aid}`,{},{type:'AuctionDetailResponse'});
await step(F,'bid buyer','POST',`/auctions/${aid}/bids`,{token:tk.buyer,body:{amount:210000}},{expect:ok(),type:'AuctionBidResponse'});
await step(F,'bid admin','POST',`/auctions/${aid}/bids`,{token:tk.admin,body:{amount:230000}},{expect:ok(),type:'AuctionBidResponse'});
await step(F,'bid thấp (kỳ vọng 400)','POST',`/auctions/${aid}/bids`,{token:tk.buyer,body:{amount:230000}},{expect:[400,409,422]});
await step(F,'bids list','GET',`/auctions/${aid}/bids`,{},{type:'AuctionBidCursorPage'});
await sleep(1000);
const evA = wsA.events.map(e=>e.type||e.__closed);
record(F,'sự kiện /ws/auctions nhận được',evA.length>0,`${evA.length}: ${[...new Set(evA)].join(',')}`);
wsA.ws.close();
// Kết thúc phiên bằng SQL (chỉ DB e2e) để thử checkout
const { psql } = await import('./ctx.mjs');
try { psql(`update auctions set ends_at = now() at time zone 'utc' - interval '1 minute', starts_at = now() at time zone 'utc' - interval '2 hours' where auction_id=${aid}`); record(F,'ép hết hạn bằng SQL (chuẩn bị checkout)',true); } catch(e){ record(F,'ép hết hạn',false,e.message.slice(0,150)); }
console.log('NOTE: checkout cần worker chốt phiên; thử gọi trực tiếp');
await step(F,'detail sau hết hạn','GET',`/auctions/${aid}`,{},{type:'AuctionDetailResponse'});
await step(F,'won list admin','GET','/auctions/me/won',{token:tk.admin},{});
const co = await step(F,'checkout winner','POST',`/auctions/${aid}/checkout`,{token:tk.admin,body:{payment_method:'CASH_ON_MEETUP',fulfillment_method:'MEETUP',meetup_place:'PTIT',contact_name:'Admin',contact_phone:'0911223344'}},{expect:[200,201,400,409]});
st.checkout = co.status;

// ---------- Chat
F = 'chat';
const wsChat = wsListen('/ws/me', tk.seller);
await wsChat.opened.catch(()=>{});
const dm = await step(F,'direct','POST','/conversations/direct',{token:tk.buyer,body:{recipient_public_id:me.seller.public_id}},{expect:ok(),type:'ConversationDetailResponse'});
const cid = dm.json?.conversation_id;
await step(F,'send text','POST',`/conversations/${cid}/messages`,{token:tk.buyer,form:fileForm({content:'xin chao'},[])},{expect:ok(),type:'MessageItem'});
const m2 = await step(F,'send attachment','POST',`/conversations/${cid}/messages`,{token:tk.buyer,form:fileForm({content:'anh'},[['files','x.png',png1,'image/png']])},{expect:ok(),type:'MessageItem'});
const mid = m2.json?.message_id;
await step(F,'send product card','POST',`/conversations/${cid}/messages`,{token:tk.buyer,form:fileForm({product_id:st.pid},[])},{expect:ok(),type:'MessageItem'});
await step(F,'reply','POST',`/conversations/${cid}/messages`,{token:tk.seller,form:fileForm({content:'chao ban',reply_to_message_id:mid},[])},{expect:ok(),type:'MessageItem'});
await step(F,'messages','GET',`/conversations/${cid}/messages`,{token:tk.seller},{type:'MessageListResponse'});
await step(F,'reaction put','PUT',`/conversations/${cid}/messages/${mid}/reaction`,{token:tk.seller,body:{reaction_key:'THICH'}},{type:'MessageReactionSummaryResponse'});
await step(F,'reaction delete','DELETE',`/conversations/${cid}/messages/${mid}/reaction`,{token:tk.seller},{type:'MessageReactionSummaryResponse'});
await step(F,'conversations list','GET','/conversations',{token:tk.seller},{type:'ConversationListResponse'});
await step(F,'detail','GET',`/conversations/${cid}`,{token:tk.seller},{type:'ConversationDetailResponse'});
await step(F,'attachments list','GET',`/conversations/${cid}/attachments`,{token:tk.seller},{type:'SharedAttachmentListResponse'});
await step(F,'attachments summary','GET',`/conversations/${cid}/attachments/summary`,{token:tk.seller},{type:'SharedAttachmentSummary'});
await step(F,'presence','GET',`/conversations/${cid}/presence`,{token:tk.seller},{type:'PresenceBatchResponse'});
await step(F,'read','POST',`/conversations/${cid}/read`,{token:tk.seller},{});
const att = (await http('GET',`/conversations/${cid}/attachments`,{token:tk.seller})).json;
const aId = att?.items?.[0]?.attachment_id;
if (aId) { const tkr = await step(F,'download-ticket','POST',`/conversations/${cid}/attachments/${aId}/download-ticket`,{token:tk.seller},{expect:ok(),type:'AttachmentDownloadTicketResponse'});
  if (tkr.json?.download_url) { const u = tkr.json.download_url.startsWith('http') ? tkr.json.download_url : 'http://localhost:8081'+tkr.json.download_url; const d = await fetch(u); record(F,'download bằng ticket',d.status===200,`HTTP ${d.status} (e2e lưu cục bộ: chặn SSRF chỉ cho cloudinary → dự kiến lệch)`); } }
else record(F,'lấy attachment_id',false,JSON.stringify(att).slice(0,120));
await step(F,'delete message (thu hồi)','DELETE',`/conversations/${cid}/messages/${mid}`,{token:tk.buyer},{type:'MessageItem'});
const grp = await step(F,'group create','POST','/conversations/groups',{token:tk.seller,body:{title:'Nhom E2E',description:'d',initial_invitee_ids:[ids('buyer')]}},{expect:ok(),type:'ConversationDetailResponse'});
const gid = grp.json?.conversation_id;
await step(F,'invites buyer','GET','/conversations/invites',{token:tk.buyer},{});
const inv = (await http('GET','/conversations/invites',{token:tk.buyer})).json;
const invId = (inv?.[0]||inv?.items?.[0])?.invite_id;
if (invId) await step(F,'respond invite','POST',`/conversations/invites/${invId}/respond`,{token:tk.buyer,body:{action:'ACCEPT'}},{});
await step(F,'group members','GET',`/conversations/groups/${gid}/members`,{token:tk.seller},{type:'ConversationMemberItem[]'});
await step(F,'group msg','POST',`/conversations/${gid}/messages`,{token:tk.seller,form:fileForm({content:'hello group'},[])},{expect:ok(),type:'MessageItem'});
await step(F,'owned-group-count','GET','/conversations/owned-group-count',{token:tk.seller},{type:'OwnedGroupSummaryResponse'});
await step(F,'from-order','POST','/conversations/from-order',{token:tk.buyer,body:{order_id:st.oid}},{expect:ok(),type:'ConversationDetailResponse'});
await sleep(800);
record(F,'seller nhận realtime tin nhắn trên /ws/me',wsChat.events.length>0,`${wsChat.events.length}: ${[...new Set(wsChat.events.map(e=>e.type))].join(',')}`);
wsChat.ws.close();

// ---------- Report
F = 'report';
const rp = await step(F,'create','POST','/reports',{token:tk.buyer,body:{target_type:'PRODUCT',target_id:p2,reason:'SPAM',description:'e2e'}},{expect:ok(),type:'ReportResponse'});
const rid = rp.json?.report_id;
await step(F,'evidence','POST',`/reports/${rid}/evidence`,{token:tk.buyer,form:fileForm({},[['files','e.png',png1,'image/png']])},{expect:ok(),type:'ReportEvidenceUploadResponse'});
await step(F,'admin list','GET','/admin/reports',{token:tk.admin},{type:'AdminReportListResponse'});
const rd = await step(F,'admin detail','GET',`/admin/reports/${rid}`,{token:tk.admin},{type:'AdminReportDetailResponse'});
await step(F,'status REVIEWING','PATCH',`/admin/reports/${rid}/status`,{token:tk.admin,body:{status:'REVIEWING'}},{type:'AdminReportDetailResponse'});
await step(F,'enforcement PRODUCT_HIDE','POST',`/admin/reports/${rid}/enforcement`,{token:tk.admin,body:{action:'PRODUCT_HIDE'}},{type:'AdminReportDetailResponse'});
await step(F,'user report-history','GET',`/admin/users/${ids('seller')}/report-history`,{token:tk.admin},{type:'SubjectReportHistoryResponse'});
const evid = rd.json?.attachments?.[0]?.attachment_id ?? rd.json?.evidence?.[0]?.attachment_id;
if (evid) { const r = await fetch(`http://localhost:8081/api/v1/admin/reports/${rid}/evidence/${evid}?token=${tk.admin}`); record(F,'admin xem evidence (?token=)',r.status===200,`HTTP ${r.status} (e2e: nguồn local → có thể lệch do chặn SSRF cloudinary)`); }

// ---------- Feedback / moment / daily need / notification
F = 'feedback';
const fb = await step(F,'create','POST','/feedback',{token:tk.buyer,form:fileForm({content:'Gop y e2e'},[['images','f.png',png1,'image/png']])},{expect:ok(),type:'FeedbackCreateResponse'});
await step(F,'mine','GET','/feedback/me',{token:tk.buyer},{type:'MyFeedbackCursorPage'});
await step(F,'admin list','GET','/admin/feedback',{token:tk.admin},{type:'AdminFeedbackListPage'});
await step(F,'admin detail','GET',`/admin/feedback/${fb.json?.feedback_id}`,{token:tk.admin},{type:'AdminFeedbackDetail'});
await step(F,'claims me','GET','/feedback/support/claims/me',{token:tk.buyer},{type:'SupportClaimUserListPage'});
await step(F,'admin support-contributions','GET','/admin/support-contributions',{token:tk.admin},{type:'AdminSupportContributionCursorPage'});
await step(F,'admin support-claims','GET','/admin/support-claims',{token:tk.admin},{type:'AdminSupportClaimCursorPage'});
F = 'community-moment';
const cm = await step(F,'submit','POST','/community-moments',{token:tk.buyer,form:fileForm({note:'khoanh khac e2e'},[['file','m.png',png1,'image/png']])},{expect:ok(),type:'CommunityMomentSubmissionResponse'});
const lst = await step(F,'admin list','GET','/admin/community-moments',{token:tk.admin},{type:'AdminCommunityMomentCursorPage'});
const mom = lst.json?.items?.[0]?.moment_id ?? cm.json?.moment_id;
await step(F,'admin approve','POST',`/admin/community-moments/${mom}/approve`,{token:tk.admin},{type:'AdminCommunityMomentItem'});
await step(F,'hero','GET','/community-moments/hero',{},{type:'PublicHeroResponse'});
F = 'daily-need';
await step(F,'put today','PUT','/daily-needs/me/today',{token:tk.buyer,body:{content:'Can mua sach'}},{type:'DailyNeed'});
await step(F,'me today','GET','/daily-needs/me/today',{token:tk.buyer},{type:'DailyNeed'});
await step(F,'summary','GET','/daily-needs/me/summary',{token:tk.buyer},{type:'DailyNeedSummary'});
await step(F,'recent','GET','/daily-needs/recent?limit=10',{},{type:'DailyNeedListResponse'});
await step(F,'user today','GET',`/daily-needs/users/${me.buyer.public_id}/today`,{},{type:'DailyNeedPublic'});
F = 'notification';
const nl = await step(F,'list buyer','GET','/notifications',{token:tk.buyer},{type:'NotificationListResponse'});
await step(F,'unread-count','GET','/notifications/unread-count',{token:tk.buyer},{type:'UnreadCountResponse'});
const nid = nl.json?.items?.[0]?.notification_id;
if (nid) await step(F,'mark read','PATCH',`/notifications/${nid}/read`,{token:tk.buyer},{expect:[200,204]});
await step(F,'read-all','PATCH','/notifications/read-all',{token:tk.buyer},{expect:[200,204]});
fs.writeFileSync(process.env.E2E_STATE, JSON.stringify({...st,me}));
process.exit(0);
