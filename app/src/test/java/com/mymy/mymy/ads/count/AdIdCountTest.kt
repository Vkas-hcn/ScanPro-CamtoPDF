package com.mymy.mymy.ads.count

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdIdCountTest {

    @Test
    fun passesWhenUnderCaps() {
        assertTrue(AdIdCount.passes(show = 0, click = 0, showCap = 10, clickCap = 10))
        assertTrue(AdIdCount.passes(show = 9, click = 9, showCap = 10, clickCap = 10))
    }

    @Test
    fun failsWhenShowCountReachesCap() {
        assertFalse(AdIdCount.passes(show = 10, click = 0, showCap = 10, clickCap = 10))
    }

    @Test
    fun failsWhenClickCountReachesCap() {
        assertFalse(AdIdCount.passes(show = 0, click = 10, showCap = 10, clickCap = 10))
    }

    @Test
    fun failsWhenCapIsZero() {
        assertFalse(AdIdCount.passes(show = 0, click = 0, showCap = 0, clickCap = 10))
        assertFalse(AdIdCount.passes(show = 0, click = 0, showCap = 10, clickCap = 0))
    }

    @Test
    fun failsWhenCapIsNegative() {
        assertFalse(AdIdCount.passes(show = 0, click = 0, showCap = -1, clickCap = 10))
    }
}
