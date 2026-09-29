package pl.mzebrows.pwt.ui.hud;

/**
 * The pending tell in a player's panel ({@code UI:hud/bottom/upgrade}): that his slot holds drafts, how
 * many, and the key that opens them - right-aligned on the panel's first row, beside whatever status is
 * there. The whole tell while it fits, a short one when the status leaves less room - without the key if
 * even that is too long - and never over the status: its columns are what the row has left.
 *
 * @param one the tell for one draft, {@code {0}} the key
 * @param many the tell for more, {@code {0}} the key and {@code {1}} the count
 * @param brief the short form, with the same arguments
 */
public record UpgradeTell(String one, String many, String brief) {

    /** The tell for this many drafts in this many columns, or empty when he holds none or there is no room. */
    public String text(int drafts, String key, int columns) {
        if (drafts <= 0 || columns <= 0) {
            return "";
        }
        String full = fill(drafts == 1 ? one : many, key, drafts);
        if (full.length() <= columns) {
            return full;
        }
        String shorter = fill(brief, key, drafts);
        if (shorter.length() <= columns) {
            return shorter;
        }
        String bare = fill(brief, "", drafts);
        return bare.length() <= columns ? bare : TextFit.fit(bare, columns);
    }

    private static String fill(String template, String key, int drafts) {
        return template.replace("{0}", key)
                .replace("{1}", Integer.toString(drafts))
                .trim();
    }
}
