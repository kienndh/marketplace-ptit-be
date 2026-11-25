// Endpoint công khai: parse response bằng kiểu FE (types.ts). Chạy: node scripts/e2e/01_public.mjs
import { step, results } from './lib.mjs';
const F = 'public';
await step(F,'categories','GET','/categories',{}, {type:'CategoryResponse[]'});
await step(F,'category 1','GET','/categories/1',{}, {type:'CategoryResponse'});
await step(F,'products list','GET','/products?limit=10',{}, {type:'ProductListResponse'});
await step(F,'banks','GET','/users/banks',{}, {type:'SupportedBank[]'});
await step(F,'tutorials','GET','/tutorials',{}, {type:'PublicTutorial[]'});
await step(F,'community hero','GET','/community-moments/hero',{}, {type:'PublicHeroResponse'});
await step(F,'feedback list','GET','/feedback?limit=10',{}, {type:'PublicFeedbackCursorPage'});
await step(F,'support leaderboard','GET','/feedback/support/leaderboard',{}, {type:'SupporterLeaderboardResponse'});
await step(F,'daily needs recent','GET','/daily-needs/recent?limit=10',{}, {type:'DailyNeedListResponse'});
await step(F,'daily needs today','GET','/daily-needs/today',{}, {type:'DailyNeedListResponse'});
await step(F,'feedback support','GET','/feedback/support',{}, {type:'SupportResponse'});
await step(F,'me không token','GET','/users/me',{}, {expect:[401]});
await step(F,'orders không token','GET','/orders/me',{}, {expect:[401,404,405]});
process.exitCode = results.some(r=>!r.ok)?1:0;
