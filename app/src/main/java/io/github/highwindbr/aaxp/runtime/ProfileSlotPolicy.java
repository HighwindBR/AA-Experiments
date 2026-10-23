package io.github.highwindbr.aaxp.runtime;

/**
 * Pure dual-slot policy shared by the manager and runtime.
 * A durable SharedPreferences pointer selects only an already-verified slot.
 */
public final class ProfileSlotPolicy {
    private ProfileSlotPolicy() {}
    public static final String POINTER_KEY = "profile.active_slot.v1";
    public static final String LEGACY_FILE = "active_profile_v2.json";
    public static final String SLOT_A = "active_profile_a_v3.json";
    public static final String SLOT_B = "active_profile_b_v3.json";
    public static final int MAX_PROFILE_BYTES = 2 * 1024 * 1024;

    public static String inactiveSlot(String active) {
        return SLOT_A.equals(active) ? SLOT_B : SLOT_A;
    }

    public static String[] readOrder(String active) {
        if (SLOT_A.equals(active)) return new String[]{SLOT_A, SLOT_B, LEGACY_FILE};
        if (SLOT_B.equals(active)) return new String[]{SLOT_B, SLOT_A, LEGACY_FILE};
        return new String[]{LEGACY_FILE, SLOT_A, SLOT_B};
    }
}
