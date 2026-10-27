package com.ninsky.cronos.mail.infrastructure;

import com.ninsky.cronos.mail.domain.EmailBrand;
import com.ninsky.cronos.mail.domain.EmailSender;
import com.ninsky.cronos.mail.domain.EmailSettings;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The tenant's brand and sender ({@code email_settings}, one row per tenant, revision-guarded). */
@Repository
public class EmailSettingsCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public EmailSettingsCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The saved settings with their revision; empty until the first save. */
    public Optional<EmailSettings> find(UUID tenant) {
        return jdbc.query("SELECT * FROM email_settings WHERE tenant_id = :tenant", Map.of("tenant", tenant), (rs, n) -> new EmailSettings(
                new EmailBrand(rs.getString("brand_name"), rs.getString("primary_color"), rs.getString("accent_color"),
                        rs.getString("logo_url"), rs.getString("header_image_url"), rs.getString("support_email"), rs.getString("address"),
                        rs.getString("preferences_url")),
                new EmailSender(rs.getString("from_name"), rs.getString("from_address"), rs.getString("reply_to")),
                rs.getLong("revision"))).stream().findFirst();
    }

    /** First save (revision 1); false when a row already exists. */
    public boolean insert(UUID tenant, EmailSettings settings, UUID actor, Instant now) {
        return jdbc.update("""
                INSERT INTO email_settings (tenant_id, brand_name, primary_color, accent_color, logo_url, header_image_url, support_email, address,
                                            preferences_url, from_name, from_address, reply_to, revision, updated_at, updated_by)
                VALUES (:tenant, :name, :primary, :accent, :logo, :header, :support, :address, :preferences, :fromName, :fromAddress, :replyTo,
                        1, :now, :actor)
                ON CONFLICT (tenant_id) DO NOTHING""", params(tenant, settings, actor, now)) == 1;
    }

    /** {@code UPDATE … WHERE revision = :expected}; false when someone saved first. */
    public boolean update(UUID tenant, long expected, EmailSettings settings, UUID actor, Instant now) {
        return jdbc.update("""
                UPDATE email_settings SET brand_name = :name, primary_color = :primary, accent_color = :accent, logo_url = :logo,
                    header_image_url = :header, support_email = :support, address = :address, preferences_url = :preferences,
                    from_name = :fromName, from_address = :fromAddress, reply_to = :replyTo, revision = revision + 1, updated_at = :now,
                    updated_by = :actor
                WHERE tenant_id = :tenant AND revision = :expected""", params(tenant, settings, actor, now).addValue("expected", expected)) == 1;
    }

    private static MapSqlParameterSource params(UUID tenant, EmailSettings settings, UUID actor, Instant now) {
        EmailBrand brand = settings.brand();
        EmailSender sender = settings.sender();
        return new MapSqlParameterSource().addValue("tenant", tenant).addValue("name", brand.name())
                .addValue("primary", brand.primaryColor()).addValue("accent", brand.accentColor()).addValue("logo", brand.logoUrl())
                .addValue("header", brand.headerImageUrl()).addValue("support", brand.supportEmail()).addValue("address", brand.address())
                .addValue("preferences", brand.preferencesUrl()).addValue("fromName", sender.fromName())
                .addValue("fromAddress", sender.fromAddress()).addValue("replyTo", sender.replyTo()).addValue("actor", actor)
                .addValue("now", Timestamp.from(now));
    }
}
