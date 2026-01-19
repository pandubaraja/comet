package io.pandu.comet.config

import io.pandu.config.CometConfig
import io.pandu.core.telemetry.exporters.CoroutineTelemetryExporter
import io.pandu.core.telemetry.metrics.CoroutineMetrics
import io.pandu.core.telemetry.types.CoroutineTelemetry
import io.pandu.sampling.strategy.AlwaysSamplingStrategy
import io.pandu.sampling.strategy.NeverSamplingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CometConfigTest {

    // Test exporter implementation for testing
    private class TestExporter : CoroutineTelemetryExporter {
        override suspend fun export(event: CoroutineTelemetry) {}
        override suspend fun exportMetrics(metrics: CoroutineMetrics) {}
        override suspend fun flush() {}
        override suspend fun shutdown() {}
        override val name: String = "TestExporter"
    }

    // =====================================================================
    // Default Values Tests
    // =====================================================================

    @Test
    fun `default builder creates config with AlwaysSamplingStrategy`() {
        val config = CometConfig.Builder().build()
        assertSame(AlwaysSamplingStrategy, config.samplingStrategy)
    }

    @Test
    fun `default builder creates config with empty exporters list`() {
        val config = CometConfig.Builder().build()
        assertTrue(config.exporters.isEmpty())
    }

    @Test
    fun `default builder creates config with includeStackTrace false`() {
        val config = CometConfig.Builder().build()
        assertFalse(config.includeStackTrace)
    }

    @Test
    fun `default builder creates config with includeCoroutineName true`() {
        val config = CometConfig.Builder().build()
        assertTrue(config.includeCoroutineName)
    }

    @Test
    fun `default builder creates config with maxStackTraceDepth 20`() {
        val config = CometConfig.Builder().build()
        assertEquals(20, config.maxStackTraceDepth)
    }

    @Test
    fun `default builder creates config with bufferSize 8192`() {
        val config = CometConfig.Builder().build()
        assertEquals(8192, config.bufferSize)
    }

    @Test
    fun `default builder creates config with flushInterval 10 seconds`() {
        val config = CometConfig.Builder().build()
        assertEquals(10.seconds, config.flushInterval)
    }

    // =====================================================================
    // Buffer Size Validation Tests
    // =====================================================================

    @Test
    fun `bufferSize must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            CometConfig.Builder().bufferSize(0).build()
        }
    }

    @Test
    fun `bufferSize must be positive - negative value`() {
        assertFailsWith<IllegalArgumentException> {
            CometConfig.Builder().bufferSize(-1).build()
        }
    }

    @Test
    fun `bufferSize must be power of 2 - fails for 1023`() {
        assertFailsWith<IllegalArgumentException> {
            CometConfig.Builder().bufferSize(1023).build()
        }
    }

    @Test
    fun `bufferSize must be power of 2 - fails for 1025`() {
        assertFailsWith<IllegalArgumentException> {
            CometConfig.Builder().bufferSize(1025).build()
        }
    }

    @Test
    fun `bufferSize must be power of 2 - fails for 3`() {
        assertFailsWith<IllegalArgumentException> {
            CometConfig.Builder().bufferSize(3).build()
        }
    }

    @Test
    fun `bufferSize accepts power of 2 - 1`() {
        val config = CometConfig.Builder().bufferSize(1).build()
        assertEquals(1, config.bufferSize)
    }

    @Test
    fun `bufferSize accepts power of 2 - 2`() {
        val config = CometConfig.Builder().bufferSize(2).build()
        assertEquals(2, config.bufferSize)
    }

    @Test
    fun `bufferSize accepts power of 2 - 1024`() {
        val config = CometConfig.Builder().bufferSize(1024).build()
        assertEquals(1024, config.bufferSize)
    }

    @Test
    fun `bufferSize accepts power of 2 - 4096`() {
        val config = CometConfig.Builder().bufferSize(4096).build()
        assertEquals(4096, config.bufferSize)
    }

    @Test
    fun `bufferSize accepts power of 2 - 16384`() {
        val config = CometConfig.Builder().bufferSize(16384).build()
        assertEquals(16384, config.bufferSize)
    }

    // =====================================================================
    // Max Stack Trace Depth Validation Tests
    // =====================================================================

    @Test
    fun `maxStackTraceDepth must be positive - fails for 0`() {
        assertFailsWith<IllegalArgumentException> {
            CometConfig.Builder().maxStackTraceDepth(0).build()
        }
    }

    @Test
    fun `maxStackTraceDepth must be positive - fails for negative`() {
        assertFailsWith<IllegalArgumentException> {
            CometConfig.Builder().maxStackTraceDepth(-1).build()
        }
    }

    @Test
    fun `maxStackTraceDepth accepts positive value - 1`() {
        val config = CometConfig.Builder().maxStackTraceDepth(1).build()
        assertEquals(1, config.maxStackTraceDepth)
    }

    @Test
    fun `maxStackTraceDepth accepts positive value - 100`() {
        val config = CometConfig.Builder().maxStackTraceDepth(100).build()
        assertEquals(100, config.maxStackTraceDepth)
    }

    // =====================================================================
    // Flush Interval Validation Tests
    // =====================================================================

    @Test
    fun `flushInterval must be positive - fails for zero`() {
        assertFailsWith<IllegalArgumentException> {
            CometConfig.Builder().flushInterval(0.seconds).build()
        }
    }

    @Test
    fun `flushInterval must be positive - fails for negative`() {
        assertFailsWith<IllegalArgumentException> {
            CometConfig.Builder().flushInterval((-1).seconds).build()
        }
    }

    @Test
    fun `flushInterval accepts positive value`() {
        val config = CometConfig.Builder().flushInterval(5.seconds).build()
        assertEquals(5.seconds, config.flushInterval)
    }

    @Test
    fun `flushInterval accepts milliseconds`() {
        val config = CometConfig.Builder().flushInterval(500.milliseconds).build()
        assertEquals(500.milliseconds, config.flushInterval)
    }

    // =====================================================================
    // Builder Chaining Tests
    // =====================================================================

    @Test
    fun `builder methods return builder for chaining`() {
        val config = CometConfig.Builder()
            .samplingStrategy(NeverSamplingStrategy)
            .includeStackTrace(true)
            .includeCoroutineName(false)
            .maxStackTraceDepth(50)
            .bufferSize(2048)
            .flushInterval(5.seconds)
            .build()

        assertSame(NeverSamplingStrategy, config.samplingStrategy)
        assertTrue(config.includeStackTrace)
        assertFalse(config.includeCoroutineName)
        assertEquals(50, config.maxStackTraceDepth)
        assertEquals(2048, config.bufferSize)
        assertEquals(5.seconds, config.flushInterval)
    }

    // =====================================================================
    // Exporter Tests
    // =====================================================================

    @Test
    fun `single exporter can be added`() {
        val exporter = TestExporter()
        val config = CometConfig.Builder()
            .exporter(exporter)
            .build()

        assertEquals(1, config.exporters.size)
        assertSame(exporter, config.exporters[0])
    }

    @Test
    fun `multiple exporters can be added one by one`() {
        val exporter1 = TestExporter()
        val exporter2 = TestExporter()
        val config = CometConfig.Builder()
            .exporter(exporter1)
            .exporter(exporter2)
            .build()

        assertEquals(2, config.exporters.size)
    }

    @Test
    fun `multiple exporters can be added as list`() {
        val exporter1 = TestExporter()
        val exporter2 = TestExporter()
        val config = CometConfig.Builder()
            .exporters(listOf(exporter1, exporter2))
            .build()

        assertEquals(2, config.exporters.size)
    }

    @Test
    fun `exporter and exporters can be combined`() {
        val exporter1 = TestExporter()
        val exporter2 = TestExporter()
        val exporter3 = TestExporter()
        val config = CometConfig.Builder()
            .exporter(exporter1)
            .exporters(listOf(exporter2, exporter3))
            .build()

        assertEquals(3, config.exporters.size)
    }

    // =====================================================================
    // Error Handler Tests
    // =====================================================================

    @Test
    fun `error handler can be set`() {
        var captured: Throwable? = null
        val config = CometConfig.Builder()
            .errorHandler { captured = it }
            .build()

        val exception = RuntimeException("test")
        config.errorHandler(exception)

        assertSame(exception, captured)
    }

    @Test
    fun `default error handler does not throw`() {
        val config = CometConfig.Builder().build()
        // Should not throw
        config.errorHandler(RuntimeException("test"))
    }
}
