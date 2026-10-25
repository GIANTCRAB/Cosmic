package client;

/** Separates permanent HP/MP maximums from the v83 combat and client limit. */
public final class HpMpStats {
    public static final int MAX_EFFECTIVE = 30_000;

    private HpMpStats() {
    }

    public static int cap(long value) {
        return (int) Math.clamp(value, 0, MAX_EFFECTIVE);
    }

    public static int effectiveMaximum(int base, int equipment, Integer bonusPercent) {
        long maximum = (long) base + equipment;
        if (bonusPercent != null) {
            maximum = (long) (maximum + maximum * (bonusPercent / 100.0));
        }
        return cap(maximum);
    }

    static int addMaximum(int maximum, int delta) {
        return (int) Math.clamp((long) maximum + delta, 0, Integer.MAX_VALUE);
    }
}
