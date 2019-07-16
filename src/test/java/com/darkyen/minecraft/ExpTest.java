package com.darkyen.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class ExpTest {

    @Test
    void expTest() {
        int total = 0;
        for (int level = 0; level < 100; level++) {
            total += Util.getExpToLevel(level);
            assertEquals(total, Util.getExperienceToReach(level + 1));
        }
    }

}
