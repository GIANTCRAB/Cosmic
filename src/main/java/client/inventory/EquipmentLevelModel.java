/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package client.inventory;

import config.ServerConfig;
import config.YamlConfig;
import constants.inventory.ItemConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the split between an equipment's <b>true</b> level (the server-internal value, which may
 * rise past the legacy cap via {@code USE_EQUIPMNT_LVLUP}) and its <b>network</b> level (the value
 * serialized to the v83 client, whose WZ data only defines per-level info up to {@value #NETWORK_MAX_LEVEL}).
 * <p>
 * Sending a level above {@value #NETWORK_MAX_LEVEL} to the client is unsafe (its WZ tables end there),
 * and indexing the GMS EXP table past its length used to throw {@code ArrayIndexOutOfBoundsException}.
 * All such access is now routed through this class so that:
 * <ul>
 *     <li>the client only ever receives a network level (clamped to {@value #NETWORK_MAX_LEVEL}); and</li>
 *     <li>true EXP lookups for levels above the table fall back to an exponential curve instead of a
 *         fixed-length array, so the ceiling is not hardwired to a particular level.</li>
 * </ul>
 */
public final class EquipmentLevelModel {
    private EquipmentLevelModel() {
    }

    private static final Logger log = LoggerFactory.getLogger(EquipmentLevelModel.class);

    /**
     * Highest level the client can display. The v83 client's Character.wz only carries per-level
     * equipment info up to this value, so the network level must never exceed it.
     */
    public static final int NETWORK_MAX_LEVEL = 30;

    /**
     * GMS equipment EXP table for levels 1..{@value #NETWORK_MAX_LEVEL} (index == level).
     * Levels beyond this range are resolved by {@link #expNeededForTrueLevel(int)} via the curve.
     */
    private static final int[] BASE_EXP = {
            1, 15, 19, 23, 35, 43, 98, 188, 237, 280, 304, 331, 571, 656, 840,
            1060, 1193, 1467, 1784, 1976, 2357, 2791, 3052, 3560, 4128, 4469,
            5123, 5844, 6276, 7093, 10000
    };

    /** Per-level multiplier of the EXP curve once the GMS table runs out. Tunable. */
    private static final double CURVE_GROWTH = 1.15;
    /** EXP anchor the curve grows from; equals the last GMS table entry (level {@value #NETWORK_MAX_LEVEL}). */
    private static final double CURVE_BASE = BASE_EXP[NETWORK_MAX_LEVEL];

    /**
     * True ceiling for equipment leveling, driven by {@code USE_EQUIPMNT_LVLUP}. Unlike the previous
     * implementation this is no longer silently clamped to {@value #NETWORK_MAX_LEVEL}.
     */
    public static int trueMaxLevel() {
        return trueMaxLevel(YamlConfig.config.server.USE_EQUIPMNT_LVLUP);
    }

    static int trueMaxLevel(int configMax) {
        return Math.max(1, configMax);
    }

    /**
     * The "fake" level to send to the client: the true level clamped to the client's display range.
     */
    public static int networkLevelOf(int trueLevel) {
        return Math.min(Math.max(trueLevel, 1), NETWORK_MAX_LEVEL);
    }

    public static boolean isAtNetworkCap(int trueLevel) {
        return trueLevel >= NETWORK_MAX_LEVEL;
    }

    public static boolean isAtTrueMax(int trueLevel) {
        return trueLevel >= trueMaxLevel();
    }

    /**
     * EXP required to advance from {@code trueLevel} to the next level. Uses the GMS table for
     * levels below {@value #NETWORK_MAX_LEVEL} and an exponential curve from there on, so it is
     * defined for any level rather than bounded by the table length.
     */
    public static int expNeededForTrueLevel(int trueLevel) {
        if (trueLevel < NETWORK_MAX_LEVEL) {
            return BASE_EXP[Math.max(trueLevel, 0)];
        }
        long value = Math.round(CURVE_BASE * Math.pow(CURVE_GROWTH, trueLevel - NETWORK_MAX_LEVEL));
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    /**
     * EXP divisor used solely for the client EXP-bar display. Always a table lookup, guaranteed in range.
     */
    public static int expNeededForNetworkLevel(int networkLevel) {
        int idx = Math.min(Math.max(networkLevel, 1), NETWORK_MAX_LEVEL);
        return BASE_EXP[idx];
    }

    // -----------------------------------------------------------------------------------------
    // Tiered, per-slot, character-level-driven equipment cap (USE_EQUIPMNT_LVLUP_TIERED).
    //
    // The true ceiling for an equipment may now vary by the owning character's level and by the
    // equipment's slot type. All of the tiered math is pure (operates on plain records/primitives)
    // so it is fully unit-testable without touching the YamlConfig singleton. Only the thin
    // trueMaxLevelFor(...) entry point reads configuration.
    // -----------------------------------------------------------------------------------------

    /** Parsed, immutable view of the {@code EQUIP_TIERS} block. */
    public record EquipmentTierConfig(boolean enabled, List<Tier> tiers) {}

    /** One tier: applies to character levels up to and including {@code maxCharLevel}. */
    public record Tier(int maxCharLevel, Map<String, SlotCap> slots) {}

    /** A per-slot cap, either a literal value or an arithmetic formula over {@code characterLevel}. */
    public sealed interface SlotCap permits LiteralCap, FormulaCap {}

    /** Fixed cap value. */
    public record LiteralCap(int value) implements SlotCap {}

    /** Cap expressed as an arithmetic formula evaluated against the character's level. */
    public record FormulaCap(String expression) implements SlotCap {}

    /**
     * Friendly equipment-slot keys used as the configuration map keys. {@link #of(int)} derives the
     * key from an item id; {@link #DEFAULT} is the fallback used when a tier does not name a slot.
     */
    public enum SlotKey {
        DEFAULT("default"),
        CAP("cap"),
        CAPE("cape"),
        CHEST("chest"),
        PANTS("pants"),
        OVERALL("overall"),
        GLOVES("gloves"),
        SHOES("shoes"),
        SHIELD("shield"),
        WEAPON("weapon"),
        RING("ring"),
        ACCESSORY("accessory"),
        TAMING("taming");

        private final String key;

        SlotKey(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static SlotKey of(int itemId) {
            if (ItemConstants.isWeapon(itemId)) {
                return WEAPON;
            }
            return switch (itemId / 10000) {
                case 100 -> CAP;
                case 101, 102, 103, 112 -> ACCESSORY;
                case 104 -> CHEST;
                case 105 -> OVERALL;
                case 106 -> PANTS;
                case 107 -> SHOES;
                case 108 -> GLOVES;
                case 109 -> SHIELD;
                case 110 -> CAPE;
                case 111 -> RING;
                case 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191 -> TAMING;
                default -> DEFAULT;
            };
        }
    }

    /**
     * True ceiling for the given equipment, honouring the tiered config when
     * {@code USE_EQUIPMNT_LVLUP_TIERED} is enabled and falling back to the flat
     * {@link #trueMaxLevel()} otherwise. This is the entry point used by the level-up path.
     */
    public static int trueMaxLevelFor(int itemId, int characterLevel) {
        if (!YamlConfig.config.server.USE_EQUIPMNT_LVLUP_TIERED) {
            return trueMaxLevel();
        }
        return Math.max(1, tieredMaxLevel(SlotKey.of(itemId), characterLevel, configFromYaml()));
    }

    /** Tiered-aware variant of {@link #isAtTrueMax(int)} for callers that have slot/character context. */
    public static boolean isAtTrueMax(int trueLevel, int itemId, int characterLevel) {
        return trueLevel >= trueMaxLevelFor(itemId, characterLevel);
    }

    /**
     * Resolve the tiered max item level for a slot at a given character level. Pure: takes the parsed
     * config directly so it can be unit-tested without touching {@link YamlConfig}.
     */
    static int tieredMaxLevel(SlotKey slot, int characterLevel, EquipmentTierConfig cfg) {
        if (cfg == null || cfg.tiers() == null || cfg.tiers().isEmpty()) {
            return NETWORK_MAX_LEVEL;
        }
        Tier tier = selectTier(cfg.tiers(), characterLevel);
        SlotCap cap = pickCap(tier, slot);
        if (cap == null) {
            return NETWORK_MAX_LEVEL;
        }
        try {
            return resolveCap(cap, characterLevel);
        } catch (RuntimeException e) {
            log.warn("Failed evaluating tiered cap for slot '{}' at character level {}: {}",
                    slot.key(), characterLevel, e.toString());
            return NETWORK_MAX_LEVEL;
        }
    }

    private static Tier selectTier(List<Tier> tiers, int characterLevel) {
        Tier last = tiers.get(tiers.size() - 1);
        for (Tier t : tiers) {
            if (characterLevel <= t.maxCharLevel()) {
                return t;
            }
        }
        return last;
    }

    private static SlotCap pickCap(Tier tier, SlotKey slot) {
        Map<String, SlotCap> slots = tier.slots();
        if (slots == null) {
            return null;
        }
        SlotCap cap = slots.get(slot.key());
        return cap != null ? cap : slots.get(SlotKey.DEFAULT.key());
    }

    static int resolveCap(SlotCap cap, int characterLevel) {
        if (cap instanceof LiteralCap literal) {
            return Math.max(1, literal.value());
        }
        if (cap instanceof FormulaCap formula) {
            long value = CapFormula.evaluate(formula.expression(), characterLevel);
            if (value < 1) {
                return 1;
            }
            return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
        }
        throw new IllegalStateException("Unknown SlotCap: " + cap);
    }

    static SlotCap parseSlotCap(Object raw) {
        Objects.requireNonNull(raw, "slot cap");
        if (raw instanceof Number n) {
            return new LiteralCap(n.intValue());
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Empty slot cap");
        }
        if (text.matches("-?\\d+")) {
            return new LiteralCap(Integer.parseInt(text));
        }
        FormulaCap cap = new FormulaCap(text);
        // Validate syntax up front so typos surface at config load rather than at first use.
        CapFormula.evaluate(cap.expression(), 1);
        return cap;
    }

    /**
     * Parses {@link YamlConfig#config}'s tiered-equipment block into an immutable
     * {@link EquipmentTierConfig}. Cached against the source list identity so repeated calls
     * (e.g. once per equipped item per mob kill) do not re-parse.
     */
    static EquipmentTierConfig configFromYaml() {
        List<ServerConfig.EquipTierYaml> source = YamlConfig.config.server.EQUIP_TIERS;
        EquipmentTierConfig cached = cachedTierConfig;
        if (cached != null && source == cachedTierSource) {
            return cached;
        }
        EquipmentTierConfig parsed = fromYaml(YamlConfig.config.server.USE_EQUIPMNT_LVLUP_TIERED, source);
        cachedTierConfig = parsed;
        cachedTierSource = source;
        return parsed;
    }

    static EquipmentTierConfig fromYaml(boolean enabled, List<ServerConfig.EquipTierYaml> source) {
        if (source == null || source.isEmpty()) {
            return new EquipmentTierConfig(enabled, List.of());
        }
        List<Tier> tiers = new ArrayList<>();
        for (ServerConfig.EquipTierYaml y : source) {
            Map<String, SlotCap> slots = new HashMap<>();
            if (y.slots != null) {
                for (Map.Entry<String, Object> e : y.slots.entrySet()) {
                    if (e.getKey() == null) {
                        continue;
                    }
                    try {
                        slots.put(e.getKey(), parseSlotCap(e.getValue()));
                    } catch (RuntimeException ex) {
                        log.warn("Skipping invalid tiered-equip cap '{}': {}", e.getKey(), ex.toString());
                    }
                }
            }
            tiers.add(new Tier(y.maxCharLevel, Map.copyOf(slots)));
        }
        return new EquipmentTierConfig(enabled, List.copyOf(tiers));
    }

    private static volatile EquipmentTierConfig cachedTierConfig;
    private static volatile List<ServerConfig.EquipTierYaml> cachedTierSource;

    /**
     * Minimal, dependency-free arithmetic evaluator for slot-cap formulas. Supports integer literals,
     * the variable {@code characterLevel} (aliases {@code charLevel}, {@code cl}), parentheses, and
     * {@code + - * /} with Java integer (truncation-toward-zero) division. Throws on any parse error,
     * unknown identifier, or division by zero so misconfigurations are caught early.
     */
    static final class CapFormula {
        private static final String VAR_CHARACTER_LEVEL = "characterLevel";
        private static final String VAR_CHAR_LEVEL = "charLevel";
        private static final String VAR_CL = "cl";

        private CapFormula() {
        }

        static long evaluate(String expression, int characterLevel) {
            Parser p = new Parser(expression, characterLevel);
            long result = p.parseExpression();
            p.expectEnd();
            return result;
        }

        private static final class Parser {
            private final String s;
            private final int len;
            private int pos;
            private final long characterLevel;

            Parser(String s, int characterLevel) {
                this.s = s;
                this.len = s.length();
                this.pos = 0;
                this.characterLevel = characterLevel;
            }

            long parseExpression() {
                long value = parseTerm();
                skipWhitespace();
                while (pos < len) {
                    char c = peek();
                    if (c == '+') {
                        pos++;
                        value += parseTerm();
                    } else if (c == '-') {
                        pos++;
                        value -= parseTerm();
                    } else {
                        break;
                    }
                    skipWhitespace();
                }
                return value;
            }

            long parseTerm() {
                long value = parseFactor();
                skipWhitespace();
                while (pos < len) {
                    char c = peek();
                    if (c == '*') {
                        pos++;
                        value *= parseFactor();
                    } else if (c == '/') {
                        pos++;
                        long divisor = parseFactor();
                        if (divisor == 0) {
                            throw new ArithmeticException("Division by zero in formula: " + s);
                        }
                        value /= divisor;
                    } else {
                        break;
                    }
                    skipWhitespace();
                }
                return value;
            }

            long parseFactor() {
                skipWhitespace();
                if (pos < len && peek() == '-') {
                    pos++;
                    return -parseFactor();
                }
                if (pos < len && peek() == '+') {
                    pos++;
                    return parseFactor();
                }
                return parsePrimary();
            }

            long parsePrimary() {
                skipWhitespace();
                if (pos >= len) {
                    throw new IllegalArgumentException("Unexpected end of formula: " + s);
                }
                char c = peek();
                if (c == '(') {
                    pos++;
                    long value = parseExpression();
                    skipWhitespace();
                    if (pos >= len || peek() != ')') {
                        throw new IllegalArgumentException("Missing ')' in formula: " + s);
                    }
                    pos++;
                    return value;
                }
                if (Character.isDigit(c)) {
                    return parseNumber();
                }
                if (Character.isLetter(c) || c == '_') {
                    return parseIdentifier();
                }
                throw new IllegalArgumentException("Unexpected character '" + c + "' in formula: " + s);
            }

            long parseNumber() {
                int start = pos;
                while (pos < len && Character.isDigit(peek())) {
                    pos++;
                }
                return Long.parseLong(s.substring(start, pos));
            }

            long parseIdentifier() {
                int start = pos;
                while (pos < len && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
                    pos++;
                }
                String name = s.substring(start, pos);
                return switch (name) {
                    case VAR_CHARACTER_LEVEL, VAR_CHAR_LEVEL, VAR_CL -> characterLevel;
                    default -> throw new IllegalArgumentException(
                            "Unknown variable '" + name + "' in formula: " + s);
                };
            }

            char peek() {
                return s.charAt(pos);
            }

            void skipWhitespace() {
                while (pos < len && Character.isWhitespace(peek())) {
                    pos++;
                }
            }

            void expectEnd() {
                skipWhitespace();
                if (pos < len) {
                    throw new IllegalArgumentException("Trailing characters in formula: " + s);
                }
            }
        }
    }
}
