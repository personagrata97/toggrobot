package com.toggrobot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sitenin 13.10.2026'daki gercek verisiyle ayristirma ve eslestirme testleri. */
class MatchingTest {
    private fun load(name: String, col: String) =
        Api.parse(col, javaClass.classLoader!!.getResource(name).readText())

    private val t10f = load("t10f.json", "t10f-configurator")
    private val rwd = t10f.first { it.name == "T10F V2 RWD Uzun Menzil" }

    private fun robot(vararg want: Pair<String, Set<String>>, max: Long = 0, any: Long = 0) = Robot(
        "t", "Test", true, "t10f-configurator", "T10F V2 RWD Uzun Menzil",
        want.associate { it.first to it.second.toMutableSet() }.toMutableMap(), max, any,
    )

    @Test fun parsesRealData() {
        assertEquals(listOf("T10F V2 RWD Uzun Menzil", "T10F V2 4More"), t10f.map { it.name })
        assertEquals(3, rwd.variants.size)
        val cheapest = rwd.variants.first()
        assertEquals(3_132_130L, cheapest.turnkey)
        assertEquals(55, cheapest.sctRate)
        assertEquals("mardin", cheapest.choice["Renk"])
        assertEquals("VAR", cheapest.choice["Meridian Premium Ses Sistemi"])
        assertEquals("YOK", cheapest.choice["Panoramik Cam Tavan"])
        assertTrue(rwd.options.any { it.title == "Siyah Renkte Tavan ve Yan Aynalar" })
        // T10X de ayrisabiliyor
        assertEquals(2, load("t10x.json", "t10x-configurator").size)
    }

    @Test fun defaultRobotFindsNothingToday() {
        val r = Robot.defaultRobot()
        assertTrue(r.matchesProduct(rwd))
        assertTrue(r.fullHits(rwd).isEmpty()) // bugun Kula yok, hepsinde Meridian var
        assertTrue(r.anyHits(rwd).isEmpty())  // en ucuz 3.13M > 2.6M
    }

    @Test fun forbiddenEquipmentExcludes() {
        // Meridian olmasin dersek hicbiri uymaz (bugun hepsinde zorunlu)
        assertEquals(0, robot("Meridian Premium Ses Sistemi" to setOf("YOK")).fullHits(rwd).size)
        // Panoramik VAR -> 2 konfigurasyon
        assertEquals(2, robot("Panoramik Cam Tavan" to setOf("VAR")).fullHits(rwd).size)
        // + cift renk (siyah tavan) olmasin -> 1
        assertEquals(1, robot("Panoramik Cam Tavan" to setOf("VAR"), "Siyah Renkte Tavan ve Yan Aynalar" to setOf("YOK")).fullHits(rwd).size)
    }

    @Test fun colorOrAndPriceLimit() {
        assertEquals(3, robot("Renk" to setOf("mardin", "ayder")).fullHits(rwd).size)
        assertEquals(1, robot("Renk" to setOf("mardin", "Kula")).fullHits(rwd).size)
        assertEquals(0, robot("Renk" to setOf("kula", "seyhan")).fullHits(rwd).size)
        assertEquals(1, robot(max = 3_150_000).fullHits(rwd).size)
        assertEquals(1, robot(any = 3_150_000).anyHits(rwd).size)
        assertEquals(3, robot("Renk" to emptySet()).fullHits(rwd).size) // bos = fark etmez
    }

    @Test fun prettyNames() {
        assertEquals("Siyah vegan deri", Fmt.value("seats-faux-leather-black"))
        assertEquals("Krem vegan deri", Fmt.value("seats-faux-leather-creme"))
        assertEquals("19 inç jant", Fmt.value("19-inch-jant"))
        assertEquals("Kula", Fmt.value("kula"))
        assertEquals("Yok", Fmt.value("YOK"))
    }
}
