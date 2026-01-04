# Comet

**Coroutine Telemetry** - A lightweight, KMP-compatible observability library for Kotlin Coroutines.

[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![KMP](https://img.shields.io/badge/KMP-Android%20%7C%20iOS%20%7C%20JVM-blueviolet)](https://kotlinlang.org/docs/multiplatform.html)

## Features

- **Coroutine Lifecycle Tracking** - Start, suspend, resume, complete, fail, cancel events
- **Real-time Metrics** - P50/P90/P99 latencies, failure rates, active counts
- **Distributed Tracing** - W3C Trace Context compatible trace propagation
- **Flexible Sampling** - Probabilistic, rate-limited, and operation-based strategies
- **Pluggable Exporters** - Callback-based and composite exporters
- **KMP Support** - Android, iOS, JVM targets
- **Low Overhead** - Lock-free data structures, configurable overhead modes

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.pandu.comet:comet-core:0.1.0")
}
```

## Quick Start

### Just Add `.traced()` to Your Launches

```kotlin
import io.pandu.Comet
import io.pandu.core.telemetry.exporters.CallbackCoroutineTelemetryExporter
import kotlinx.coroutines.*

// 1. Create Comet instance (once, at app startup)
val comet = Comet.create {
    exporter(CallbackCoroutineTelemetryExporter(
        onEvent = { event -> println("[Comet] $event") },
        onMetrics = { metrics -> println("[Metrics] Active: ${metrics.activeCoroutines}") }
    ))
    trackSuspensions(true)
    bufferSize(8192)  // Must be a power of 2
}
comet.start()

// 2. Add comet.traced("name") to ANY launch - that's it!
scope.launch(comet.traced("my-operation")) {
    // This and ALL child coroutines are automatically traced!
    launch(CoroutineName("child-task")) { /* auto traced with name */ }
    async(CoroutineName("async-task")) { /* auto traced with name */ }
}
```

### One-Line Change to Existing Code

```kotlin
// BEFORE: No tracing
viewModelScope.launch {
    val user = userRepo.get(id)
}

// AFTER: Just add comet.traced() - done!
viewModelScope.launch(comet.traced("load-user")) {
    val user = userRepo.get(id)  // Now traced!
}
```

### With Baggage (Propagates to All Children)

```kotlin
import io.pandu.core.CoroutineTraceContext

scope.launch(comet.traced("api-request") + CoroutineTraceContext.Key.create(
    "api-request",
    baggage = mapOf("user-id" to userId, "tenant" to tenantId)
)) {
    // All children inherit this baggage
    launch { /* has user-id and tenant */ }
}
```

### Android ViewModel Example

```kotlin
class UserViewModel : ViewModel() {
    private val comet = Comet.create {
        exporter(CallbackCoroutineTelemetryExporter(
            onEvent = { event -> Log.d("Comet", event.toString()) }
        ))
        bufferSize(8192)
    }.also { it.start() }

    fun loadUser(id: String) {
        // Just add comet.traced() - works with viewModelScope!
        viewModelScope.launch(comet.traced("load-user")) {
            // Use CoroutineName for meaningful span names
            val user = async(CoroutineName("fetch-user")) { userRepo.get(id) }
            val prefs = async(CoroutineName("fetch-prefs")) { prefsRepo.get(id) }
            _state.value = UserState(user.await(), prefs.await())
        }
    }

    // Access metrics anytime
    fun getStats() = comet.metrics

    override fun onCleared() {
        runBlocking { comet.shutdown() }
    }
}
```

### iOS/KMP Example

```kotlin
class UserPresenter(private val scope: CoroutineScope) {
    private val comet = Comet.create {
        exporter(CallbackCoroutineTelemetryExporter { event -> println(event) })
        bufferSize(8192)
    }.also { it.start() }

    fun loadUser(id: String) {
        scope.launch(comet.traced("load-user")) {
            // Use CoroutineName for meaningful child span names
            val user = async(CoroutineName("fetch-user")) { api.getUser(id) }
            val settings = async(CoroutineName("fetch-settings")) { api.getSettings(id) }
            updateUI(user.await(), settings.await())
        }
    }
}
```

### Access Metrics

```kotlin
val metrics = comet.metrics

println("Active coroutines: ${metrics.activeCoroutines}")
println("Total completed: ${metrics.totalCompleted}")
println("P99 latency: ${metrics.durationStats.p99}")
println("Failure rate: ${metrics.byOperation["api-call"]?.failureRate}")
```

## Configuration

### Sampling Strategies

```kotlin
import io.pandu.sampling.strategy.*

// Always sample (development only)
samplingStrategy(AlwaysSamplingStrategy)

// Never sample (disable telemetry)
samplingStrategy(NeverSamplingStrategy)

// Probabilistic (sample X% of root traces)
samplingStrategy(ProbabilisticSamplingStrategy(0.1f))

// Rate limited (max N samples per second)
samplingStrategy(RateLimitedSamplingStrategy(maxPerSecond = 100))

// Operation-based (different rates per operation)
samplingStrategy(OperationBasedSamplingStrategy(
    rules = listOf(
        OperationBasedSamplingStrategy.OperationRule(Regex("payment-.*"), 1.0f),  // Always observe payments
        OperationBasedSamplingStrategy.OperationRule(Regex("health-.*"), 0.01f),  // Rarely observe health checks
    ),
    defaultRate = 0.1f
))

// Composite (combine multiple strategies)
samplingStrategy(CompositeSamplingStrategy(
    strategies = listOf(
        OperationBasedSamplingStrategy(...),
        RateLimitedSamplingStrategy(maxPerSecond = 1000)
    ),
    mode = CompositeSamplingStrategy.Mode.ANY
))
```

### Exporters

```kotlin
import io.pandu.core.telemetry.exporters.*

// Callback (logging, analytics, custom integrations)
exporter(CallbackCoroutineTelemetryExporter(
    onEvent = { event -> println(event) },
    onMetrics = { metrics -> println("Active: ${metrics.activeCoroutines}") }
))

// Composite (multiple exporters)
exporter(CompositeCoroutineTelemetryExporter(listOf(
    CallbackCoroutineTelemetryExporter { event -> logToConsole(event) },
    CallbackCoroutineTelemetryExporter { event -> sendToBackend(event) }
)))
```

### Performance Tuning

```kotlin
val comet = Comet.create {
    // Low overhead mode for production
    lowOverheadMode(true)

    // Or fine-tune individually
    trackSuspensions(false)      // Skip suspend/resume events
    includeStackTrace(false)     // No stack traces
    includeCoroutineName(true)   // Keep names for debugging

    // Buffer settings (must be power of 2)
    bufferSize(65536)            // Larger buffer for high throughput
    flushInterval(30.seconds)    // Less frequent flushes
}
```

## Trace Context

### Automatic Child Spans

Comet **automatically creates child spans** for nested coroutines:

```kotlin
scope.launch(comet.traced("parent-operation")) {
    // Auto child span named "coroutine"
    launch { }

    // Auto child span named "fetch-user" (uses CoroutineName!)
    launch(CoroutineName("fetch-user")) { }

    // Auto child span named "save-data"
    async(CoroutineName("save-data")) { }
}
```

### Naming Child Coroutines

Use Kotlin's standard `CoroutineName` - Comet picks it up automatically:

```kotlin
scope.launch(comet.traced("api-request")) {
    // These get meaningful span names!
    launch(CoroutineName("fetch-user")) { api.getUser(id) }
    launch(CoroutineName("fetch-settings")) { api.getSettings(id) }
    async(CoroutineName("audit-log")) { audit.log(request) }
}
```

### `withSpan` for Suspend Functions

Use `withSpan` to trace blocks within a suspend function:

```kotlin
import io.pandu.core.tools.withSpan

suspend fun processOrder(orderId: String) = withSpan("process-order") {
    val order = withSpan("fetch-order") {
        orderRepo.get(orderId)
    }

    withSpan("validate") {
        validator.validate(order)
    }

    withSpan("save") {
        orderRepo.save(order)
    }
}
```

### Baggage - Metadata That Propagates

Use `withBaggage` to add metadata that flows through the entire trace:

```kotlin
import io.pandu.core.tools.withBaggage

scope.launch(comet.traced("process")) {
    withBaggage("order-id", orderId) {
        // Everything in here has order-id
        validateOrder()
        processPayment()
        sendConfirmation()
    }
}
```

### Opt-out of Automatic Child Spans

```kotlin
val comet = Comet.create {
    autoCreateChildSpans(false)  // Manual mode
}
```

### HTTP Propagation (Cross-Service Tracking)

```kotlin
import io.pandu.core.CoroutineTraceContext
import kotlinx.coroutines.currentCoroutineContext

// Outgoing request - add headers
suspend fun makeRequest(url: String) {
    val traceContext = currentCoroutineContext()[CoroutineTraceContext.Key]
    val headers = traceContext?.toHeaders() ?: emptyMap()
    httpClient.get(url) {
        headers.forEach { (key, value) -> header(key, value) }
    }
}

// Incoming request - extract context
fun handleRequest(request: Request) {
    val trace = CoroutineTraceContext.Key.fromHeaders(request.headers, "handle-request")
    launch(trace ?: CoroutineTraceContext.Key.create("handle-request")) {
        processRequest(request)
    }
}
```

## Metrics

### Available Metrics

```kotlin
interface CoroutineMetrics {
    val activeCoroutines: Long      // Currently in flight
    val totalStarted: Long          // Total launched
    val totalCompleted: Long        // Completed successfully
    val totalFailed: Long           // Failed with exception
    val totalCancelled: Long        // Cancelled
    val totalDropped: Long          // Events dropped (buffer full)

    val durationStats: DurationStats  // P50, P90, P99, min, max, mean
    val byDispatcher: Map<String, CoroutineDispatcherMetrics>
    val byOperation: Map<String, OperationMetrics>
}
```

### Per-Operation Metrics

```kotlin
val opMetrics = comet.metrics.byOperation["api-call"]

opMetrics?.let {
    println("Total calls: ${it.totalCount}")
    println("Failures: ${it.failureCount}")
    println("Failure rate: ${it.failureRate}")
    println("P99 latency: ${it.durationStats.p99}")
    println("Avg suspensions: ${it.avgSuspensionsPerCall}")
}
```

## Platform Support

| Platform | Status | Notes |
|----------|--------|-------|
| JVM | Full | Full stack traces, thread info |
| Android | Full | Full stack traces, thread info |
| iOS | Partial | Limited stack traces |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Your Application                          │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ CoroutineScope + Comet                                  │ │
│  │                                                          │ │
│  │  launch(comet.traced("op")) { ... }                     │ │
│  └─────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   TelemetryInterceptor                       │
│              (Observes coroutine lifecycle)                  │
└───────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   TelemetryCollector                         │
│              (Collects and buffers events)                   │
└───────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       Exporters                              │
│           (Send data to external systems)                    │
└─────────────────────────────────────────────────────────────┘
```

## Testing

```kotlin
import io.pandu.Comet
import io.pandu.sampling.strategy.AlwaysSamplingStrategy
import io.pandu.core.telemetry.types.CoroutineCompleted
import io.pandu.core.telemetry.exporters.CallbackCoroutineTelemetryExporter

@Test
fun `api call is traced`() = runTest {
    val events = mutableListOf<CoroutineTelemetry>()
    val comet = Comet.create {
        samplingStrategy(AlwaysSamplingStrategy)
        exporter(CallbackCoroutineTelemetryExporter { event ->
            events.add(event)
        })
        bufferSize(1024)
    }
    comet.start()

    launch(comet.traced("test-api-call")) {
        delay(10)  // Simulate work
    }

    advanceUntilIdle()
    comet.flush()

    val completed = events.filterIsInstance<CoroutineCompleted>()
    assertTrue(completed.isNotEmpty())
}
```

## Best Practices

### Production Configuration

```kotlin
import io.pandu.Comet
import io.pandu.sampling.strategy.*
import io.pandu.core.telemetry.exporters.CallbackCoroutineTelemetryExporter
import kotlin.time.Duration.Companion.seconds

val comet = Comet.create {
    // Sample strategically
    samplingStrategy(CompositeSamplingStrategy(
        strategies = listOf(
            OperationBasedSamplingStrategy(
                rules = listOf(
                    OperationBasedSamplingStrategy.OperationRule(Regex("payment-.*"), 1.0f),
                    OperationBasedSamplingStrategy.OperationRule(Regex("health-.*"), 0.0f),
                ),
                defaultRate = 0.1f
            ),
            RateLimitedSamplingStrategy(maxPerSecond = 1000)
        ),
        mode = CompositeSamplingStrategy.Mode.ANY
    ))

    // Minimize overhead
    lowOverheadMode(true)

    // Appropriate buffer for load (must be power of 2)
    bufferSize(131072)
    flushInterval(10.seconds)

    // Handle errors gracefully
    onError { e ->
        println("Comet error: ${e.message}")
    }
}
```

## License

```
Copyright 2025

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

## Acknowledgments

- Inspired by [OpenTelemetry](https://opentelemetry.io/)
- Built with [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- Lock-free structures inspired by [JCTools](https://github.com/JCTools/JCTools)
