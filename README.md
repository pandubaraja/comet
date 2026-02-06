# Comet
<div align="center">
  <img src="docs/images/comet.png" alt="Coroutine Telemetry" width="150" />
</div>

**Coroutine Telemetry** a Kotlin Multiplatform library for observing structured concurrency in Kotlin Coroutines. It enables tracing and visualization of coroutine execution by exposing coroutine hierarchies, lifecycles, suspension points, execution timing, and failure propagation across platforms, enabling deep analysis of coroutine behavior.

[![Kotlin](https://img.shields.io/badge/kotlin-2.2.20-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![KMP](https://img.shields.io/badge/KMP-Android%20%7C%20iOS%20%7C%20JVM-blueviolet)](https://kotlinlang.org/docs/multiplatform.html)

## Features

- **Coroutine Lifecycle Real-Time Tracking** - Start, suspend, resume, complete, fail, cancel events
- **Real-time Metrics** - P50/P90/P99 latencies, failure rates, active counts
- **Flexible Sampling** - Probabilistic, rate-limited, and operation-based strategies
- **Pluggable Exporters** - Callback-based and composite exporters
- **KMP Support** - Android, iOS, JVM targets

## Preview
https://github.com/user-attachments/assets/9d9fb746-a588-4b4c-b1ab-81018c221a96

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.pandubaraja:comet:0.3.0")
}
```

## Quick Start

### Just Add `comet.traced()` to Your Launches

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
        viewModelScope.launch { comet.shutdown() }
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

### Using `withContext` with Comet

When switching dispatchers with `withContext`, use `.traced()` to preserve tracing:

```kotlin
scope.launch(comet.traced("operation")) {
    // Use .traced() to keep tracing when switching dispatchers
    withContext(Dispatchers.IO.traced()) {
        val data = async(CoroutineName("fetch")) { api.getData() }
        data.await()
    }
}
```

> **Why?** `withContext` replaces the coroutine interceptor. `.traced()` re-wraps
> the new dispatcher with Comet's telemetry. Without it, child coroutines
> inside `withContext` won't be traced.

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

### Source Location Tracking

Enable `includeStackTrace(true)` to capture where coroutines are created:

```kotlin
val comet = Comet.create {
    includeStackTrace(true)  // Enables source file and line number capture
}
```

When enabled, `CoroutineStarted` events include `creationStackTrace` with the call site information. This is useful for debugging and visualization tools like [comet-visualizer](https://github.com/pandubaraja/comet-visualizer).

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

## Real-Time Visualization

Use [comet-visualizer](https://github.com/pandubaraja/comet-visualizer) for real-time trace visualization in your browser:

```kotlin
dependencies {
    implementation("io.github.pandubaraja:comet:0.3.0")
    implementation("io.github.pandubaraja:comet-visualizer:0.3.0")
}
```

```kotlin
import io.pandu.Comet
import io.pandu.comet.visualizer.TraceServer
import io.pandu.core.telemetry.exporters.VisualizerJsonExporter

fun main() = runBlocking {
    // Start the visualizer server
    val server = TraceServer(port = 8080)
    server.start()

    // Configure Comet with the visualizer exporter
    val comet = Comet.create {
        exporter(VisualizerJsonExporter(server::sendEvent))
        includeStackTrace(true)  // Enables source file/line display
    }
    comet.start()

    // Your traced coroutines
    launch(comet.traced("my-operation")) {
        launch(CoroutineName("child-task")) {
            delay(100)
        }
    }

    // Open http://localhost:8080 in your browser
}
```

> **Note:** If running on an Android emulator or device, forward the port first:
> ```bash
> adb forward tcp:8080 tcp:8080
> ```
> Then open `http://localhost:8080` in your browser.

The visualizer provides:
- **Tree View**: Hierarchical display of coroutine parent-child relationships
- **Gantt Chart**: Timeline visualization with zoom support
- **Performance Tab**: Per-operation latency breakdown
- **Source Location**: Click nodes to see file and line number

## Demo App

See [comet-demo](https://github.com/pandubaraja/comet-demo) for a full KMP sample app (Android + iOS) demonstrating Comet and comet-visualizer integration with real API calls and various coroutine patterns.

## Platform Support

| Platform | Status | Notes |
|----------|--------|-------|
| JVM | Full | Full stack traces, thread info |
| Android | Full | Full stack traces, thread info |
| iOS | Partial | Limited stack traces |

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
