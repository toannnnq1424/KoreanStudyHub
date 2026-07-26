-- Keep custom site names, but upgrade the original pre-KSH seed value by hash.
UPDATE system_settings
SET setting_value = 'Korean Study Hub'
WHERE setting_key = 'site.name'
  AND SHA2(setting_value, 256) =
      '52bd14f2ddd74a7662b75616231a72925fcf975e10f0cb07f21069d771577628';
