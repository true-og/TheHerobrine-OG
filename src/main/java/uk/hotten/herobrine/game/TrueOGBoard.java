package uk.hotten.herobrine.game;

// Text helpers for the Herobrine sidebar in the Scoreboard-OG house style.
// Every line stays within 16 legacy characters so 1.8 clients see it whole.
public final class TrueOGBoard {

    public static final String TITLE = "&4♥ &a&lTheHerobrine&c&l-OG &4♥";
    public static final String FOOTER = "&etrue-og.net";
    // Visible characters left on a line after one leading color code.
    public static final int VALUE_WIDTH = 14;

    private TrueOGBoard() {

    }

    // Cuts plain text to the given visible width.
    public static String fit(String text, int width) {

        if (text == null) {

            return "";

        }

        return text.length() <= width ? text : text.substring(0, width);

    }

    // Keeps counters at four characters for a shared line: 12345 becomes 12k.
    public static String compact(int value) {

        if (value < 10000) {

            return String.valueOf(value);

        }

        if (value < 1000000) {

            return (value / 1000) + "k";

        }

        return (value / 1000000) + "m";

    }

    // Formats seconds as mm:ss, clamped at zero.
    public static String clock(int seconds) {

        final int clamped = Math.max(0, seconds);
        return String.format("%02d:%02d", clamped / 60, clamped % 60);

    }

}
