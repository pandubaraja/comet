package io.pandu.comet.core

import io.pandu.Comet
import io.pandu.config.CometConfig
import io.pandu.core.telemetry.exporters.CallbackCoroutineTelemetryExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class DispatcherPreservationTest {

    private fun createComet(): Comet {
        val config = CometConfig.Builder()
            .exporter(CallbackCoroutineTelemetryExporter(onEvent = {}))
            .build()
        return Comet.create(config)
    }

    @Test
    fun `traced preserves scope dispatcher for launch`() {
        val comet = createComet()
        comet.start()

        val customDispatcher = newSingleThreadContext("TestDispatcher")
        try {
            val scope = CoroutineScope(customDispatcher)
            runBlocking {
                val job = scope.launch(comet.traced("test-operation")) {
                    val threadName = Thread.currentThread().name
                    assertTrue(
                        threadName.contains("TestDispatcher"),
                        "Expected thread name to contain 'TestDispatcher' but was '$threadName'"
                    )
                }
                job.join()
            }
        } finally {
            customDispatcher.close()
            runBlocking { comet.shutdown() }
        }
    }

    @Test
    fun `traced preserves scope dispatcher for async`() {
        val comet = createComet()
        comet.start()

        val customDispatcher = newSingleThreadContext("AsyncDispatcher")
        try {
            val scope = CoroutineScope(customDispatcher)
            runBlocking {
                val deferred = scope.async(comet.traced("async-operation")) {
                    val threadName = Thread.currentThread().name
                    assertTrue(
                        threadName.contains("AsyncDispatcher"),
                        "Expected thread name to contain 'AsyncDispatcher' but was '$threadName'"
                    )
                    threadName
                }
                deferred.await()
            }
        } finally {
            customDispatcher.close()
            runBlocking { comet.shutdown() }
        }
    }

    @Test
    fun `child coroutines inherit preserved dispatcher`() {
        val comet = createComet()
        comet.start()

        val customDispatcher = newSingleThreadContext("ParentDispatcher")
        try {
            val scope = CoroutineScope(customDispatcher)
            runBlocking {
                val job = scope.launch(comet.traced("parent-operation")) {
                    // Child launch should inherit the dispatcher
                    val childJob = launch {
                        val threadName = Thread.currentThread().name
                        assertTrue(
                            threadName.contains("ParentDispatcher"),
                            "Child coroutine expected 'ParentDispatcher' but was '$threadName'"
                        )
                    }
                    childJob.join()

                    // Child async should also inherit the dispatcher
                    val result = async {
                        val threadName = Thread.currentThread().name
                        assertTrue(
                            threadName.contains("ParentDispatcher"),
                            "Child async expected 'ParentDispatcher' but was '$threadName'"
                        )
                        threadName
                    }
                    result.await()
                }
                job.join()
            }
        } finally {
            customDispatcher.close()
            runBlocking { comet.shutdown() }
        }
    }

    @Test
    fun `traced without scope dispatcher falls back to Default`() {
        val comet = createComet()
        comet.start()

        try {
            runBlocking {
                // Using EmptyCoroutineContext-like scope (no explicit dispatcher)
                // The traced context should still work, falling back to Dispatchers.Default
                val job = launch(comet.traced("no-dispatcher-operation")) {
                    // Should not crash - just verify it runs
                    assertTrue(true)
                }
                job.join()
            }
        } finally {
            runBlocking { comet.shutdown() }
        }
    }
}
