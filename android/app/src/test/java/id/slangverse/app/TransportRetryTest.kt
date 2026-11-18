package id.slangverse.app

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The bank retries transport failures only. A dropped stall is worth one more GET; a wrong URL or a
 * checksum mismatch would just cost the reader another wait, so those are not retried.
 */
class TransportRetryTest {

    @Test
    fun aStalledReadIsRetriedOnceAndSucceeds() {
        var calls = 0
        val text = withTransportRetry(pauseMillis = 0) {
            if (++calls == 1) throw SocketTimeoutException("timeout") else "catalog"
        }
        assertEquals("catalog", text)
        assertEquals(2, calls)
    }

    @Test
    fun theLastTransportErrorIsReportedOnceTheAttemptsRunOut() {
        var calls = 0
        val error = assertThrows(IOException::class.java) {
            withTransportRetry(pauseMillis = 0) {
                calls++
                throw SocketTimeoutException("timeout")
            }
        }
        assertEquals(2, calls)
        assertEquals("timeout", error.message)
    }

    @Test
    fun aBankDefectIsNotRetried() {
        var calls = 0
        val error = assertThrows(ApiException::class.java) {
            withTransportRetry(pauseMillis = 0) {
                calls++
                throw ApiException("Question bank not found (404). Check its URL and publication status")
            }
        }
        assertEquals(1, calls)
        assert(error.message!!.contains("404"))
    }

    @Test
    fun aSuccessfulReadIsNotRepeated() {
        var calls = 0
        assertEquals("pack", withTransportRetry(pauseMillis = 0) { calls++; "pack" })
        assertEquals(1, calls)
    }
}
