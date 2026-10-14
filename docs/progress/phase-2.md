# Phase 2 — Vehicles and Service History (completed 2026-10-12)

## Implemented
* `V3` migration: `customers`, `customer_consents`, `vehicles`, `vehicle_service_records`.
* Customer module: CRUD, phone normalisation (E.164, AM default), duplicate-phone conflict, per-channel marketing consents, global opt-out, search by name or partial phone, delete protection while vehicles exist.
* Vehicle module: create (with existing or new customer), update, delete (audited, cascades history), search by phone / plate / VIN / brand / model, VIN format validation and uniqueness, plate normalisation, mileage ≥ 0, warning when next oil change ≤ current mileage.
* Service records: append-only visits with oil name/type and all six filter fields, newest-first history, latest-record endpoint, edit/delete audited, vehicle state follows the newest visit (mileage never decreases).
* Maintenance reminders endpoint (`kmWindow`, `daysWindow`) + maintenance status on every vehicle (OK / DUE_SOON / OVERDUE / UNKNOWN), never extrapolating mileage.
* Opt-in demo data seeder (`SEED_DEMO_DATA=true`): 3 customers, 4 vehicles, 7 visits.
* Frontend: Vehicles page with Vehicles / Customers / Reminders tabs, vehicle form in five sections with the three filter groups, vehicle detail page with service-history timeline and add/edit/delete visit dialogs, customer dialog with consents editor, reminders panel.

## Verification
| Check | Result |
|---|---|
| `mvn verify` | unit 22/22, integration 21/21 |
| `npm run build` / `npm test` | clean / 7 passed |
| Compose backend rebuilt | migrations v3 applied, demo data seeded, search + reminders verified through nginx |
| Browser | Vehicles table with live demo data, maintenance badges, Armenian labels; password-change flow |
