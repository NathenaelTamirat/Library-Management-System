package com.library.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatusToastTest {
    @Test
    void formatsMessageWithoutChangingInternalContent() {
        assertEquals("3 book(s) found", StatusToast.format("  3 book(s) found \n"));
    }

    @Test
    void usesReadableNonBlockingDisplayDurations() {
        assertTrue(StatusToast.DISPLAY_MILLIS >= 2_000);
        assertTrue(StatusToast.DISPLAY_MILLIS <= 5_000);
        assertTrue(StatusToast.FADE_MILLIS > 0);
        assertTrue(StatusToast.FADE_MILLIS < StatusToast.DISPLAY_MILLIS);
    }
}
