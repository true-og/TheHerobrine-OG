package uk.hotten.herobrine.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Replaces the player chosen as Herobrine with the configured Herobrine skin
 * for the duration of a round, then restores their original skin on game end or
 * quit. Uses the Paper PlayerProfile API -- no resource pack required.
 *
 * Configuration lives in plugin config.yml under herobrineSkinValue /
 * herobrineSkinSignature. Both must be present for the override to apply. The
 * optional herobrineSkinTextureUrl is used only as a sanity check against the
 * texture URL embedded in the signed MineSkin value.
 */
public final class HerobrineSkin {

    public static final String TEXTURES_PROPERTY = "textures";

    private static final String SKIN_VALUE_CONFIG = "herobrineSkinValue";
    private static final String LEGACY_SKIN_VALUE_CONFIG = "herobrineSkinTexture";
    private static final String SKIN_SIGNATURE_CONFIG = "herobrineSkinSignature";
    private static final String SKIN_TEXTURE_URL_CONFIG = "herobrineSkinTextureUrl";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<UUID, OriginalProperty> originals = new HashMap<>();

    private HerobrineSkin() {

    }

    /**
     * Apply the configured Herobrine textures property to the player. No-ops if the
     * configuration is incomplete. Captures the player's prior textures value so it
     * can be restored on round end.
     */
    public static void apply(JavaPlugin plugin, Player player) {

        if (plugin == null || player == null || !player.isOnline())
            return;

        ConfiguredSkin configuredSkin = readConfiguredSkin(plugin);
        if (configuredSkin == null)
            return;

        try {

            PlayerProfile profile = player.getPlayerProfile();
            ProfileProperty original = null;
            for (ProfileProperty prop : profile.getProperties()) {

                if (TEXTURES_PROPERTY.equals(prop.getName())) {

                    original = prop;
                    break;

                }

            }

            originals.put(player.getUniqueId(), new OriginalProperty(original));

            profile.removeProperty(TEXTURES_PROPERTY);
            profile.setProperty(new ProfileProperty(TEXTURES_PROPERTY, configuredSkin.value, configuredSkin.signature));
            player.setPlayerProfile(profile);

            Console.info("Applied Herobrine skin override to " + player.getName() + " using texture "
                    + configuredSkin.textureUrl + ".");

        } catch (Throwable t) {

            Console.error("Failed to apply Herobrine skin to " + player.getName() + ": " + t.getMessage());
            t.printStackTrace();

        }

    }

    /**
     * Restore the player's original textures property captured at apply() time.
     * Safe to call repeatedly or on a player that was never overridden.
     */
    public static void restore(Player player) {

        if (player == null)
            return;

        OriginalProperty saved = originals.remove(player.getUniqueId());
        if (saved == null)
            return;

        if (!player.isOnline())
            return;

        try {

            PlayerProfile profile = player.getPlayerProfile();
            profile.removeProperty(TEXTURES_PROPERTY);
            if (saved.property != null)
                profile.setProperty(saved.property);
            player.setPlayerProfile(profile);
            Console.info("Restored original skin for " + player.getName() + ".");

        } catch (Throwable t) {

            Console.error("Failed to restore original skin for " + player.getName() + ": " + t.getMessage());
            t.printStackTrace();

        }

    }

    private static ConfiguredSkin readConfiguredSkin(JavaPlugin plugin) {

        String value = normalizeBase64(plugin.getConfig().getString(SKIN_VALUE_CONFIG));
        if (value == null)
            value = normalizeBase64(plugin.getConfig().getString(LEGACY_SKIN_VALUE_CONFIG));

        String signature = normalizeBase64(plugin.getConfig().getString(SKIN_SIGNATURE_CONFIG));

        if (value == null || signature == null) {

            Console.debug("Herobrine skin override skipped: " + SKIN_VALUE_CONFIG + " / " + SKIN_SIGNATURE_CONFIG
                    + " not set in config.yml.");
            return null;

        }

        String textureUrl = readTextureUrl(value);
        if (textureUrl == null)
            return null;

        String expectedTextureUrl = trimToNull(plugin.getConfig().getString(SKIN_TEXTURE_URL_CONFIG));
        if (expectedTextureUrl != null && !sameTexture(expectedTextureUrl, textureUrl)) {

            Console.error("Herobrine skin override skipped: " + SKIN_VALUE_CONFIG + " decodes to " + textureUrl
                    + " but " + SKIN_TEXTURE_URL_CONFIG + " is " + expectedTextureUrl + ".");
            return null;

        }

        return new ConfiguredSkin(value, signature, textureUrl);

    }

    private static String readTextureUrl(String skinValue) {

        try {

            byte[] decoded = Base64.getMimeDecoder().decode(skinValue);
            JsonNode root = JSON.readTree(new String(decoded, StandardCharsets.UTF_8));
            JsonNode textureUrl = root.path("textures").path("SKIN").path("url");
            if (textureUrl.isTextual() && !textureUrl.asText().isBlank())
                return textureUrl.asText().trim();

        } catch (IllegalArgumentException | IOException e) {

            Console.error("Herobrine skin override skipped: " + SKIN_VALUE_CONFIG
                    + " must be MineSkin's Skin Value base64 JSON, not the Direct Link or Texture URL.");
            Console.debug("Herobrine skin value parse error: " + e.getMessage());
            return null;

        }

        Console.error("Herobrine skin override skipped: " + SKIN_VALUE_CONFIG + " does not contain textures.SKIN.url.");
        return null;

    }

    private static boolean sameTexture(String expectedTextureUrl, String actualTextureUrl) {

        return textureId(expectedTextureUrl).equals(textureId(actualTextureUrl));

    }

    private static String textureId(String textureUrl) {

        int queryStart = textureUrl.indexOf('?');
        String withoutQuery = queryStart == -1 ? textureUrl : textureUrl.substring(0, queryStart);
        int slash = withoutQuery.lastIndexOf('/');
        return slash == -1 ? withoutQuery : withoutQuery.substring(slash + 1);

    }

    private static String normalizeBase64(String value) {

        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.replaceAll("\\s+", "");

    }

    private static String trimToNull(String value) {

        if (value == null)
            return null;

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;

    }

    private static final class ConfiguredSkin {

        private final String value;
        private final String signature;
        private final String textureUrl;

        private ConfiguredSkin(String value, String signature, String textureUrl) {

            this.value = value;
            this.signature = signature;
            this.textureUrl = textureUrl;

        }

    }

    private static final class OriginalProperty {

        private final ProfileProperty property;

        private OriginalProperty(ProfileProperty property) {

            this.property = property;

        }

    }

}
