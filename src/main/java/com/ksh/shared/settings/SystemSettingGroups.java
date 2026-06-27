package com.ksh.shared.settings;

/**
 * Names of setting groups in table {@code system_settings.setting_group}.
 *
 * <p>Shared constants to avoid duplicate magic strings between
 * {@code EmailSettingsService}, {@code DbConfiguredMailSender} and other
 * settings services in the future (General, OAuth, AI).
 */
public final class SystemSettingGroups {

    public static final String SMTP = "SMTP";
    public static final String GENERAL = "GENERAL";
    public static final String OAUTH = "OAUTH";
    public static final String AI = "AI";

    private SystemSettingGroups() {
        // utility class
    }
}