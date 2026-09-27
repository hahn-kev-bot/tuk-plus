package app.hahn.tukplus.core.logging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RedactorTest {
    private val redactor = Redactor("test-salt")

    @Test
    fun `hash is stable and depends on the salt`() {
        assertEquals(redactor.hash("user-1"), redactor.hash("user-1"))
        assertNotEquals(redactor.hash("user-1"), redactor.hash("user-2"))
        assertNotEquals(redactor.hash("user-1"), Redactor("other").hash("user-1"))
        assertTrue(redactor.hash("user-1").matches(Regex("#[0-9a-f]{10}")))
    }

    @Test
    fun `phone, email and account are masked`() {
        assertEquals("+66 8x xxx 5678", redactor.maskPhone("+66812345678"))
        assertEquals("xxx 5678", redactor.maskPhone("0812345678"))
        assertEquals("***", redactor.maskPhone("123"))
        assertEquals("s***@example.com", redactor.maskEmail("somchai@example.com"))
        assertEquals("****7890", redactor.maskAccount("123-4-567890"))
    }

    @Test
    fun `url ids and personal query values are redacted`() {
        val id = "26ba9921-6f9a-11eb-a3cb-96d83c7bed1c"
        val out = redactor.redactUrl("transactions?user_id=$id&type=ongoing")
        assertEquals("transactions?user_id=${redactor.hash(id)}&type=ongoing", out)
        assertEquals("users/${redactor.hash(id)}", redactor.redactUrl("users/$id"))
        assertEquals("user_addresses?phone_number=+66 8x xxx 5678", redactor.redactUrl("user_addresses?phone_number=+66812345678"))
        assertEquals("magic_login/abc?code=[removed]", redactor.redactUrl("magic_login/abc?code=1234"))
        assertEquals("workflows/2d12?ts=1", redactor.redactUrl("workflows/2d12?ts=1"))
    }

    @Test
    fun `json bodies are redacted by key`() {
        val body = """
            {"created_by":"u1","uuid":"d1","data":{"business":{"id":"shop-1","phone_number":"+66828800199"},
             "order":{"order_value":380}},"user":{"id":"u1","name":"Kim","email":"kim@example.com","notification_token":"tok"},
             "bank_account":{"account_number":"1234567890","bank_name":"KBank"}}
        """.trimIndent()
        val out = Json.parseToJsonElement(redactor.redactBody(body)).jsonObject
        assertEquals(redactor.hash("u1"), out["created_by"]!!.jsonPrimitive.content)
        assertEquals(redactor.hash("d1"), out["uuid"]!!.jsonPrimitive.content)
        val user = out["user"]!!.jsonObject
        assertEquals(redactor.hash("u1"), user["id"]!!.jsonPrimitive.content)
        assertEquals("k***@example.com", user["email"]!!.jsonPrimitive.content)
        assertEquals("[removed]", user["notification_token"]!!.jsonPrimitive.content)
        // Shop ids stay, so that bugs can be found. The shop phone is masked like all phones.
        val business = out["data"]!!.jsonObject["business"]!!.jsonObject
        assertEquals("shop-1", business["id"]!!.jsonPrimitive.content)
        assertFalse(business["phone_number"]!!.jsonPrimitive.content.contains("880"))
        assertEquals("****7890", out["bank_account"]!!.jsonObject["account_number"]!!.jsonPrimitive.content)
        assertEquals("380", out["data"]!!.jsonObject["order"]!!.jsonObject["order_value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `plain text bodies are kept but cut`() {
        assertEquals("sql: no rows in result set", redactor.redactBody("sql: no rows in result set\n"))
        assertEquals(10, redactor.redactBody("x".repeat(50), maxChars = 10).length)
    }
}
