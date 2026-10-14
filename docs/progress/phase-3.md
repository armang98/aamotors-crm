# Phase 3 — Referral Bonuses (completed 2026-10-12)

## Implemented
* `V4` migration: `referrals` (unique invited customer, no self-referral check, discount check `= 10`) and `referral_rewards` (one per referral, unique redeemed order, discount check `= 10`).
* `ReferralPolicy.DISCOUNT_PERCENT = 10` constant; no API or UI can change it.
* Workflow: create (PENDING) → confirm first paid service (CONFIRMED → REWARDED, reward AVAILABLE, one transaction) → redeem once (REDEEMED) → further redemptions refused; cancel voids unused rewards. Service-order gateway validates that qualifying/redeemed orders are paid and belong to the right customer (wired in Phase 4).
* Search with status / reward filters, summary counters, monthly or daily analytics buckets in the business time zone, per-customer available/redeemed rewards.
* Frontend: summary cards with locked 10 % card, add-referral form with searchable customer pickers (and inline customer creation), history table with gated actions (confirm, redeem, cancel, view), Recharts analytics with year/month filters, referral discount line on the vehicle detail page.

## Verification
| Check | Result |
|---|---|
| `mvn verify` | unit 22/22, integration 25/25 (`ReferralIT` 4) |
| `npm run build` / `npm test` | clean / 11 passed |
| Browser | Referrals page live: summary, pickers searching the demo customers, form |
