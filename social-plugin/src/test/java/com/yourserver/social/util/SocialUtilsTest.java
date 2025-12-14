package com.yourserver.social.util;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class SocialUtilsTest {

    @Test
    void formatTimeAgo_justNow_returnsCorrectString() {
        Instant now = Instant.now();
        String result = SocialUtils.formatTimeAgo(now);
        assertEquals("just now", result);
    }

    @Test
    void formatTimeAgo_minutesAgo_returnsCorrectString() {
        Instant fiveMinutesAgo = Instant.now().minusSeconds(300);
        String result = SocialUtils.formatTimeAgo(fiveMinutesAgo);
        assertEquals("5 minutes ago", result);
    }

    @Test
    void formatTimeAgo_hoursAgo_returnsCorrectString() {
        Instant twoHoursAgo = Instant.now().minusSeconds(7200);
        String result = SocialUtils.formatTimeAgo(twoHoursAgo);
        assertEquals("2 hours ago", result);
    }

    @Test
    void formatTimeAgo_daysAgo_returnsCorrectString() {
        Instant threeDaysAgo = Instant.now().minusSeconds(259200);
        String result = SocialUtils.formatTimeAgo(threeDaysAgo);
        assertEquals("3 days ago", result);
    }

    @Test
    void isValidName_validName_returnsTrue() {
        assertTrue(SocialUtils.isValidName("TestClan", 3, 16));
        assertTrue(SocialUtils.isValidName("My Clan", 3, 16));
        assertTrue(SocialUtils.isValidName("ABC", 3, 16));
    }

    @Test
    void isValidName_tooShort_returnsFalse() {
        assertFalse(SocialUtils.isValidName("AB", 3, 16));
    }

    @Test
    void isValidName_tooLong_returnsFalse() {
        assertFalse(SocialUtils.isValidName("ThisNameIsWayTooLongForAClan", 3, 16));
    }

    @Test
    void isValidName_specialCharacters_returnsFalse() {
        assertFalse(SocialUtils.isValidName("Test@Clan", 3, 16));
        assertFalse(SocialUtils.isValidName("Test_Clan", 3, 16));
    }

    @Test
    void isValidName_leadingTrailingSpaces_returnsFalse() {
        assertFalse(SocialUtils.isValidName(" TestClan", 3, 16));
        assertFalse(SocialUtils.isValidName("TestClan ", 3, 16));
    }

    @Test
    void isValidTag_validTag_returnsTrue() {
        assertTrue(SocialUtils.isValidTag("TEST", 2, 6));
        assertTrue(SocialUtils.isValidTag("ABC", 2, 6));
        assertTrue(SocialUtils.isValidTag("T3ST", 2, 6));
    }

    @Test
    void isValidTag_withSpaces_returnsFalse() {
        assertFalse(SocialUtils.isValidTag("T E", 2, 6));
    }

    @Test
    void isValidTag_specialCharacters_returnsFalse() {
        assertFalse(SocialUtils.isValidTag("T@ST", 2, 6));
    }

    @Test
    void pluralize_singular_returnsCorrectString() {
        assertEquals("1 member", SocialUtils.pluralize(1, "member"));
    }

    @Test
    void pluralize_plural_returnsCorrectString() {
        assertEquals("5 members", SocialUtils.pluralize(5, "member"));
        assertEquals("0 members", SocialUtils.pluralize(0, "member"));
    }

    @Test
    void joinArgs_fromIndex_joinsCorrectly() {
        String[] args = {"party", "chat", "hello", "world"};

        assertEquals("hello world", SocialUtils.joinArgs(args, 2));
        assertEquals("chat hello world", SocialUtils.joinArgs(args, 1));
    }

    @Test
    void joinArgs_outOfBounds_returnsEmpty() {
        String[] args = {"party", "chat"};
        assertEquals("", SocialUtils.joinArgs(args, 5));
    }

    @Test
    void calculatePages_correctCalculation() {
        assertEquals(1, SocialUtils.calculatePages(10, 20));
        assertEquals(2, SocialUtils.calculatePages(21, 20));
        assertEquals(3, SocialUtils.calculatePages(50, 20));
    }
}