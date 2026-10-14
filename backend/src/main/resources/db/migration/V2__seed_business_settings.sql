-- Seed confirmed A&A Motors business details (editable through the Business Settings dialog).
INSERT INTO business_settings (
    singleton_key, business_name, address, primary_phone, secondary_phone,
    working_hours, default_language, time_zone, currency
) VALUES (
    1,
    'A&A Motors',
    '52/2 Vazgen Sargsyan Street, Gyumri, Armenia',
    '+37494889881',
    '+37493099473',
    'Mon–Sat 09:00–19:00',
    'hy',
    'Asia/Yerevan',
    'AMD'
);
