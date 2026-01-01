# ☄️ Comet

**Coroutine Telemetry** - A lightweight, KMP-compatible observability library for Kotlin Coroutines.

> *"We are the astronomers, observing the trails of coroutines as they streak across your application."*

[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![KMP](https://img.shields.io/badge/KMP-Android%20%7C%20iOS%20%7C%20JVM-blueviolet)](https://kotlinlang.org/docs/multiplatform.html)

## ✨ Features

- 🔍 **Coroutine Lifecycle Tracking** - Start, suspend, resume, complete, fail, cancel events
- 📊 **Real-time Metrics** - P50/P90/P99 latencies, failure rates, active counts
- 🔗 **Distributed Tracing** - W3C Trace Context compatible trace propagation
- 🎯 **Flexible Sampling** - Probabilistic, rate-limited, and operation-based strategies
- 📤 **Pluggable Exporters** - In-memory, callback, composite, custom exporters
- 🍎 **KMP Support** - Android, iOS, JVM targets
- ⚡ **Low Overhead** - Lock-free data structures, configurable overhead modes

## 📦 Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.pandu.comet:comet-sampling:0.1.0")
}
```

## 🚀 Quick Start

### Just Add `.traced()` to Your Launches!

```kotlin
import io.pandu.Comet
import io.pandu.exporters.CallbackExporter
import kotlinx.coroutines.*

// 1. Create Comet instance (once, at app startup)
val comet = Comet.create {
    exporters(CallbackExporter(
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
scope.launch(comet.traced("api-request", mapOf(
    "user-id" to userId,
    "tenant" to tenantId
))) {
    // All children inherit this baggage
    launch { /* has user-id and tenant */ }
}
```

### Android ViewModel Example

```kotlin
class UserViewModel : ViewModel() {
    private val comet = Comet.create {
        exporters(CallbackExporter(
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
        exporters(CallbackExporter { event -> println(event) })
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

### Access Metrics (Observatory Dashboard)

```kotlin
val metrics = comet.metrics

println("Active coroutines: ${metrics.activeCoroutines}")
println("Total completed: ${metrics.totalCompleted}")
println("P99 latency: ${metrics.durationStats.p99}")
println("Failure rate: ${metrics.byOperation["api-call"]?.failureRate}")
```

## ⚙️ Configuration

### Sampling Strategies

```kotlin
import io.pandu.sampling.*

// Always sample (development only)
samplingStrategy(AlwaysSample)

// Probabilistic (sample X% of root traces)
samplingStrategy(ProbabilisticSampling(0.1f))

// Rate limited (max N samples per second)
samplingStrategy(RateLimitedSampling(maxPerSecond = 100))

// Operation-based (different rates per operation)
samplingStrategy(OperationBasedSampling(
    rules = listOf(
        OperationBasedSampling.OperationRule(Regex("payment-.*"), 1.0f),  // Always observe payments
        OperationBasedSampling.OperationRule(Regex("health-.*"), 0.01f),  // Rarely observe health checks
    ),
    defaultRate = 0.1f
))
```

### Exporters (Telescopes)

```kotlin
import io.pandu.exporters.*

// In-memory (testing)
val memExporter = InMemoryExporter()
exporters(memExporter)
// Later: memExporter.getEvents()

// Callback (logging, analytics, custom integrations)
exporters(CallbackExporter(
    onEvent = { event -> println(event) },
    onMetrics = { metrics -> println("Active: ${metrics.activeCoroutines}") }
))

// No-op (disable export)
exporters(NoOpExporter)

// Composite (multiple exporters)
exporters(CompositeExporter(listOf(
    InMemoryExporter(),
    CallbackExporter { event -> sendToBackend(event) }
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

## 🔗 Trace Context

### Automatic Child Spans ✨

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

Use `withSpan` from `io.pandu.tools` to trace blocks within a suspend function:

```kotlin
import io.pandu.tools.withSpan

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

**Baggage** is key-value data that flows through the entire trace:

```kotlin
// Set baggage at the root
scope.launch(comet.traced("api-request", mapOf(
    "user-id" to userId,
    "tenant-id" to tenantId
))) {
    // ALL children inherit baggage automatically!
    launch(CoroutineName("db-query")) {
        // ✅ Has user-id and tenant-id
        database.query()
    }

    launch(CoroutineName("cache-lookup")) {
        // ✅ Has user-id and tenant-id
        cache.get()
    }
}

// Or add baggage mid-trace (import io.pandu.tools.withBaggage)
scope.launch(comet.traced("process")) {
    withBaggage("order-id", orderId) {
        // Everything in here has order-id
        validateOrder()
        processPayment()
        sendConfirmation()
    }
}
```

**Common baggage uses:**
| Key | Purpose |
|-----|---------|
| `user-id` | Track all operations for a user |
| `request-id` | Correlate logs across services |
| `tenant-id` | Multi-tenant isolation |
| `session-id` | User session tracking |

### Opt-out of Automatic Child Spans

```kotlin
val comet = Comet.create {
    autoCreateChildSpans(false)  // Manual mode
}
```

### HTTP Propagation (Cross-Service Tracking)

```kotlin
import io.pandu.tools.currentTraceContext
import io.pandu.context.TraceContext

// Outgoing request - add headers
val headers = currentTraceContext()?.toHeaders() ?: emptyMap()
httpClient.get(url) {
    headers.forEach { (key, value) -> header(key, value) }
}

// Incoming request - extract context
val trace = TraceContext.fromHeaders(request.headers, "handle-request")
launch(trace ?: TraceContext.create("handle-request")) {
    processRequest(request)
}
```

## 📊 Metrics (Observatory Data)

### Available Metrics

```kotlin
interface TelemetryMetrics {
    val activeCoroutines: Long      // Currently in flight
    val totalStarted: Long          // Total launched
    val totalCompleted: Long        // Completed successfully
    val totalFailed: Long           // Failed with exception
    val totalCancelled: Long        // Cancelled
    val totalDropped: Long          // Events dropped (buffer full)

    val durationStats: DurationStats  // P50, P90, P99, min, max, mean
    val byDispatcher: Map<String, DispatcherMetrics>
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

## 🌍 Platform Support

| Platform | Status | Notes |
|----------|--------|-------|
| JVM | ✅ Full | Full stack traces, thread info |
| Android | ✅ Full | Full stack traces, thread info |
| iOS | ✅ Partial | Limited stack traces |

## 📺 Visualization (JVM Only)

The optional `comet-visualizer` module provides visualization tools for debugging and development.

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.pandu.comet:comet-sampling:0.1.0")
    implementation("io.pandu.comet:comet-visualizer:0.1.0")  // Optional
}
```

### Static HTML Export

Generate a static HTML file with your trace visualization:

```kotlin
import io.pandu.Comet
import io.pandu.comet.visualizer.HtmlExporter

val htmlExporter = HtmlExporter()
val comet = Comet.create {
    exporters(htmlExporter)
    trackSuspensions(true)
}
comet.start()

// Run your coroutines...
scope.launch(comet.traced("my-operation")) {
    // ...
}

// Generate HTML
comet.flush()
htmlExporter.writeHtml("trace-visualization.html")
comet.shutdown()
```

### Real-time Web UI

Stream traces to a live web dashboard:

```kotlin
import io.pandu.Comet
import io.pandu.comet.visualizer.RealtimeExporter
import io.pandu.comet.visualizer.TraceServer

val server = TraceServer(port = 8080)
server.start()

val exporters = RealtimeExporter { event ->
    server.sendEvent(event)
}

val comet = Comet.create {
    exporters(exporters)
    trackSuspensions(true)
}
comet.start()

// Open http://localhost:8080 in browser
// Run your coroutines - they appear in real-time!

// Cleanup
comet.shutdown()
server.stop()
```

**Features:**
- Tree view with parent-child relationships
- Gantt chart timeline visualization
- Dark/Light theme toggle
- Real-time event streaming via SSE
- Zoom and pan controls

### Running the Example

```bash
# Clone and run
git clone https://github.com/pandubaraja/comet-example.git
cd comet-example

# Run real-time visualizer (opens http://localhost:8080)
./gradlew realtime

# Or generate static HTML
./gradlew visualize
open trace-visualization.html
```

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Your Application                          │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ CoroutineScope + Comet                                  │ │
│  │                                                          │ │
│  │  launch(TraceContext) { ... }  ← Coroutine trails       │ │
│  └─────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   TelemetryInterceptor                       │
│         (The Telescope - Observes coroutine lifecycle)       │
└───────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     EventCollector                           │
│            (The Observatory - Collects observations)         │
└───────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       Exporters                              │
│     (Star Charts - Send data to external systems)            │
└─────────────────────────────────────────────────────────────┘
```

## 🧪 Testing

```kotlin
import io.pandu.Comet
import io.pandu.sampling.AlwaysSample
import io.pandu.events.CoroutineCompleted
import io.pandu.exporters.InMemoryExporter

@Test
fun `api call is traced`() = runTest {
    val exporters = InMemoryExporter()
    val comet = Comet.create {
        samplingStrategy(AlwaysSample)
        exporters(exporters)
        bufferSize(1024)
    }
    comet.start()

    launch(comet.traced("test-api-call")) {
        delay(10)  // Simulate work
    }

    advanceUntilIdle()
    comet.flush()

    val events = exporters.getEvents()
    val completed = events.filterIsInstance<CoroutineCompleted>()
    assertTrue(completed.isNotEmpty())
}
```

## 🎯 Best Practices

### Production Configuration

```kotlin
import io.pandu.Comet
import io.pandu.sampling.*
import io.pandu.exporters.CallbackExporter
import kotlin.time.Duration.Companion.seconds

val comet = Comet.create {
    // Sample strategically
    samplingStrategy(CompositeSampling(
        strategies = listOf(
            OperationBasedSampling(
                rules = listOf(
                    OperationBasedSampling.OperationRule(Regex("payment-.*"), 1.0f),
                    OperationBasedSampling.OperationRule(Regex("health-.*"), 0.0f),
                ),
                defaultRate = 0.1f
            ),
            RateLimitedSampling(maxPerSecond = 1000)
        ),
        mode = CompositeSampling.Mode.ANY
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

## 🤝 Contributing

Contributions are welcome! Please read our [Contributing Guide](CONTRIBUTING.md) first.

## 📜 License

```
Copyright 2025

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

## 🙏 Acknowledgments

- Inspired by [OpenTelemetry](https://opentelemetry.io/)
- Built with [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- Lock-free structures inspired by [JCTools](https://github.com/JCTools/JCTools)

---

<p align="center">
  <i>☄️ "Like astronomers tracking comets across the night sky, Comet helps you observe the trails of coroutines streaking through your application." ☄️</i>
</p>
