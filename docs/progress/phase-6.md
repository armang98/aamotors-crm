# Phase 6 — Advertising Campaigns (completed 2026-10-12)

## Implemented
* `V7` migration: `campaigns` (filters as JSONB, schedule in business time zone + UTC instant, lifecycle status) and `campaign_recipients` (materialised segment with eligibility reason, first channel, delivery link).
* Segment resolver over vehicles' latest service record (brand, model, last service window, oil type/name, the six filter fields, km remaining to next oil change) plus consent-channel filter; eligibility through the same channel providers as Quick Advertising.
* Lifecycle: create / edit / duplicate / delete draft, segment preview, schedule (future only, recipients materialised), start now, pause, resume, cancel (pending work skipped), results; in-process scheduler starts due campaigns and dispatches in batches through the Phase 5 fallback orchestrator (one `AdvertisingSend` per campaign run).
* Analytics: selected / eligible / attempted / accepted / delivered / failed / pending / skipped / uncertain, per-channel counts, estimated cost from env unit prices, actual cost only when prices are known (never fabricated).
* Frontend: campaigns table with lifecycle actions, editor with live segment preview and schedule, detail page with stat tiles, per-channel chart, recipients and deliveries tabs, polling while running; "Create reminder campaign" shortcut on the Reminders tab.

## Verification
| Check | Result |
|---|---|
| `mvn verify` | `CampaignIT` 3/3 incl. scheduled execution end to end |
| `npm run build` / `npm test` | clean / 21 passed |
