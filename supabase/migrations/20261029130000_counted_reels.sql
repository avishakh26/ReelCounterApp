-- Reels counted one by one on the device (ReelCounter's accessibility service).
--
-- A new metric category, COUNTED, ranked after VERIFIED and before ESTIMATED: an exact count made
-- on the phone, but not the platform's own data. Counted rows store their count in
-- estimated_reels with low = high = count, so the existing columns and constraints apply.
-- Clients may write it: activity_insert/activity_update only forbid VERIFIED.

alter type public.metric_quality add value if not exists 'COUNTED' after 'VERIFIED';

-- Friends read totals through this view. The new value can't be used as an enum literal in the
-- transaction that adds it, so counted rows are matched by their text form. New column at the end,
-- as CREATE OR REPLACE VIEW requires.
create or replace view public.daily_activity_totals with (security_invoker = true) as
select user_id,
       platform_id,
       activity_date,
       sum(foreground_ms) filter (where source <> 'data_portability')       as foreground_ms,
       sum(session_count)                                                  as session_count,
       sum(estimated_reels) filter (where quality = 'ESTIMATED')           as estimated_reels,
       sum(estimated_reels_low) filter (where quality = 'ESTIMATED')       as estimated_reels_low,
       sum(estimated_reels_high) filter (where quality = 'ESTIMATED')      as estimated_reels_high,
       max(verified_reels) filter (where quality = 'VERIFIED')             as verified_reels,
       min(quality)                                                        as best_quality,
       bool_or(user_adjusted)                                              as user_adjusted,
       sum(estimated_reels) filter (where quality::text = 'COUNTED')       as counted_reels
from public.daily_activity_summaries
group by user_id, platform_id, activity_date;
