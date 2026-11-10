package io.edgeos.car

import io.edgeos.car.maps.Maps
import org.junit.Assert.assertEquals
import org.junit.Test

class MapsTest {
    @Test fun tabla() {
        val html = """
<table><tr><th>Name</th><th>Last modified</th><th>Size</th></tr>
<tr><td valign="top"><img src="/icons/back.gif"></td><td><a href="/maps/v5/">Parent Directory</a></td><td>&nbsp;</td><td align="right">  - </td></tr>
<tr><td valign="top"><img src="/icons/folder.gif"></td><td><a href="spain/">spain/</a></td><td align="right">2025-01-01 10:00  </td><td align="right">  - </td></tr>
<tr><td valign="top"><img src="/icons/unknown.gif"></td><td><a href="spain.map">spain.map</a></td><td align="right">2025-01-01 10:00  </td><td align="right">478M</td></tr>
<tr><td valign="top"><img src="/icons/unknown.gif"></td><td><a href="great-britain.map">great-britain.map</a></td><td align="right">2025-01-01 10:00  </td><td align="right">1.2G</td></tr>
<tr><td><a href="?C=N;O=D">Name</a></td></tr>
</table>"""
        val e = Maps.parse("europe/", html)
        assertEquals(listOf("europe/spain/", "europe/spain.map", "europe/great-britain.map"), e.map { it.path })
        assertEquals(listOf("", "478M", "1.2G"), e.map { it.size })
        assertEquals("Great britain", e[2].name)
    }

    @Test fun pre() {
        val html = """<pre><a href="?C=N;O=D">Name</a>
<a href="../">Parent Directory</a>
<a href="madrid.map">madrid.map</a>              01-Jan-2025 10:00   52M
<a href="comunidad-valenciana.map">comunidad-valenciana.map</a>  01-Jan-2025 10:00  110M
</pre>"""
        val e = Maps.parse("europe/spain/", html)
        assertEquals(listOf("europe/spain/madrid.map", "europe/spain/comunidad-valenciana.map"), e.map { it.path })
        assertEquals(listOf("52M", "110M"), e.map { it.size })
    }

    @Test fun bytes() {
        assertEquals(478_000_000L, Maps.bytes("478M"))
        assertEquals(1_200_000_000L, Maps.bytes("1.2G"))
    }
}
