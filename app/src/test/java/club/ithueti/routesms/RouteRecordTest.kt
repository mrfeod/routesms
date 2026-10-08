package club.ithueti.routesms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRecordTest {
    @Test
    fun aliasHasPriorityInDisplayName() {
        val route = RouteRecord(
            id = "sim_5",
            kind = RouteKind.SIM,
            phoneNumber = "+70000000000",
            alias = "Рабочий"
        )
        assertEquals("Рабочий", route.displayName())
    }

    @Test
    fun manualNumberOverridesDetectedNumber() {
        val route = RouteRecord(
            id = "sim_1",
            kind = RouteKind.SIM,
            phoneNumber = "+70000000000",
            manualPhoneNumber = "+79999999999"
        )
        assertEquals("+79999999999", route.effectivePhoneNumber)
        assertEquals("+79999999999", route.displayName())
    }

    @Test
    fun configurationRequiresBothValues() {
        assertFalse(BotConfig("token", "").isConfigured)
        assertFalse(BotConfig("", "chat").isConfigured)
        assertTrue(BotConfig("token", "chat").isConfigured)
    }

    @Test
    fun specialRoutesHaveStableNames() {
        assertEquals("Default", RouteRecord("default", RouteKind.DEFAULT).displayName())
        assertEquals("Служебный чат", RouteRecord("service", RouteKind.SERVICE).displayName())
    }
}
