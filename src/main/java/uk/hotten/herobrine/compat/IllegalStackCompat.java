package uk.hotten.herobrine.compat;

import java.util.UUID;
import main.java.me.dniym.IllegalStack;

/**
 * Soft-dependency bridge to IllegalStack-OG. This class is only loaded by the
 * JVM when IllegalStack-OG is present, avoiding ClassNotFoundException when the
 * plugin is absent.
 */
public class IllegalStackCompat {

    public static void exemptPlayer(UUID uuid) {

        IllegalStack.addExemptPlayer(uuid);

    }

    public static void unexemptPlayer(UUID uuid) {

        IllegalStack.removeExemptPlayer(uuid);

    }

}
