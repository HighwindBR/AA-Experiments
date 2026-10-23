package io.github.highwindbr.aaxp.runtime;

import static org.junit.Assert.*;
import org.junit.Test;

public class ProfileSlotPolicyTest {
    @Test public void firstPublishPreservesLegacyUntilPointerCommits() {
        assertEquals(ProfileSlotPolicy.SLOT_A, ProfileSlotPolicy.inactiveSlot(null));
        assertArrayEquals(new String[]{ProfileSlotPolicy.LEGACY_FILE},
            ProfileSlotPolicy.readOrder(null));
    }
    @Test public void writingNeverTruncatesSelectedSlot() {
        assertEquals(ProfileSlotPolicy.SLOT_B, ProfileSlotPolicy.inactiveSlot(ProfileSlotPolicy.SLOT_A));
        assertEquals(ProfileSlotPolicy.SLOT_A, ProfileSlotPolicy.inactiveSlot(ProfileSlotPolicy.SLOT_B));
        assertArrayEquals(new String[]{ProfileSlotPolicy.SLOT_A,
            ProfileSlotPolicy.SLOT_B, ProfileSlotPolicy.LEGACY_FILE},
            ProfileSlotPolicy.readOrder(ProfileSlotPolicy.SLOT_A));
        assertArrayEquals(new String[]{ProfileSlotPolicy.SLOT_B,
            ProfileSlotPolicy.SLOT_A, ProfileSlotPolicy.LEGACY_FILE},
            ProfileSlotPolicy.readOrder(ProfileSlotPolicy.SLOT_B));
    }
    @Test public void invalidPointerCannotChooseArbitraryRemoteFile() {
        assertEquals(ProfileSlotPolicy.SLOT_A, ProfileSlotPolicy.inactiveSlot("../../unexpected"));
        assertArrayEquals(ProfileSlotPolicy.readOrder(null), ProfileSlotPolicy.readOrder("../../unexpected"));
    }
}
