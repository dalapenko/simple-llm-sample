package llmchat.agent.invariant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvariantCategoryTest {

    @Test
    fun `fromCliName resolves by exact cli name`() {
        assertEquals(InvariantCategory.STACK, InvariantCategory.fromCliName("stack"))
        assertEquals(InvariantCategory.ARCHITECTURE, InvariantCategory.fromCliName("architecture"))
        assertEquals(InvariantCategory.BUSINESS_RULE, InvariantCategory.fromCliName("business-rule"))
        assertEquals(InvariantCategory.GENERAL, InvariantCategory.fromCliName("general"))
    }

    @Test
    fun `fromCliName is case insensitive`() {
        assertEquals(InvariantCategory.STACK, InvariantCategory.fromCliName("STACK"))
        assertEquals(InvariantCategory.STACK, InvariantCategory.fromCliName("Stack"))
        assertEquals(InvariantCategory.BUSINESS_RULE, InvariantCategory.fromCliName("BUSINESS-RULE"))
        assertEquals(InvariantCategory.BUSINESS_RULE, InvariantCategory.fromCliName("Business-Rule"))
    }

    @Test
    fun `fromCliName resolves by enum name`() {
        assertEquals(InvariantCategory.STACK, InvariantCategory.fromCliName("STACK"))
        assertEquals(InvariantCategory.BUSINESS_RULE, InvariantCategory.fromCliName("BUSINESS_RULE"))
    }

    @Test
    fun `fromCliName returns null for unknown name`() {
        assertNull(InvariantCategory.fromCliName("unknown"))
        assertNull(InvariantCategory.fromCliName(""))
        assertNull(InvariantCategory.fromCliName("tech-stack"))
    }

    @Test
    fun `displayName values are correct`() {
        assertEquals("Technology Stack", InvariantCategory.STACK.displayName)
        assertEquals("Architecture", InvariantCategory.ARCHITECTURE.displayName)
        assertEquals("Business Rule", InvariantCategory.BUSINESS_RULE.displayName)
        assertEquals("General", InvariantCategory.GENERAL.displayName)
    }

    @Test
    fun `cliName values are correct`() {
        assertEquals("stack", InvariantCategory.STACK.cliName)
        assertEquals("architecture", InvariantCategory.ARCHITECTURE.cliName)
        assertEquals("business-rule", InvariantCategory.BUSINESS_RULE.cliName)
        assertEquals("general", InvariantCategory.GENERAL.cliName)
    }

    @Test
    fun `all four categories exist`() {
        assertEquals(4, InvariantCategory.entries.size)
    }
}
