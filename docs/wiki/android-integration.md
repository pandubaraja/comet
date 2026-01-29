# Integrating Comet with KMM Android Project

This guide walks you through integrating the Comet coroutine telemetry library into a Kotlin Multiplatform Mobile (KMM) Android project.

## Prerequisites

- Kotlin Multiplatform project with Android target
- Kotlin 2.0+ and Gradle 8+
- `kotlinx-coroutines` dependency
- Android `minSdk 21` or higher

## 1. Installation

### Add dependency to shared module

In your **shared module's** `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.pandubaraja:comet:0.2.0")
        }
    }
}
```

### Add dependency to Android app module (optional)

If you want to use Comet directly in your Android app module:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.pandubaraja:comet:0.2.0")
}
```

## 2. Initialize Comet

Create a singleton `Comet` instance in your `Application` class so it's available app-wide.

```kotlin
// shared/src/commonMain/kotlin/CometProvider.kt
import io.pandu.Comet
import io.pandu.core.telemetry.exporters.CallbackCoroutineTelemetryExporter
import io.pandu.sampling.strategy.ProbabilisticSamplingStrategy

object CometProvider {
    val comet: Comet = Comet.create {
        samplingStrategy(ProbabilisticSamplingStrategy(1.0f)) // 100% in dev
        exporter(CallbackCoroutineTelemetryExporter(
            onEvent = { event -> println("[Comet] $event") }
        ))
        bufferSize(8192)
        includeCoroutineName(true)
    }

    fun start() {
        comet.start()
    }

    suspend fun shutdown() {
        comet.shutdown()
    }
}
```

```kotlin
// androidApp/src/main/kotlin/MyApplication.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CometProvider.start()
    }
}
```

Don't forget to register in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApplication"
    ... />
```

## 3. Tracing in ViewModels

Add `comet.traced("operation-name")` to any `viewModelScope.launch` call. All child coroutines are automatically traced.

```kotlin
import io.pandu.Comet
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class UserViewModel(
    private val userRepo: UserRepository,
    private val prefsRepo: PrefsRepository
) : ViewModel() {

    private val comet = CometProvider.comet

    fun loadUser(id: String) {
        viewModelScope.launch(comet.traced("load-user")) {
            // Child coroutines get auto-traced with CoroutineName
            val user = async(CoroutineName("fetch-user")) {
                userRepo.getUser(id)
            }
            val prefs = async(CoroutineName("fetch-prefs")) {
                prefsRepo.getPrefs(id)
            }
            _state.value = UserState(user.await(), prefs.await())
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Optional: flush pending events
        viewModelScope.launch {
            comet.flush()
        }
    }
}
```

## 4. Tracing in Repositories and Use Cases

Use `withSpan` to trace blocks within suspend functions:

```kotlin
import io.pandu.core.tools.withSpan

class OrderRepository(private val api: OrderApi, private val db: OrderDao) {

    suspend fun processOrder(orderId: String): Order = withSpan("process-order") {
        val order = withSpan("fetch-order") {
            api.getOrder(orderId)
        }

        withSpan("validate-order") {
            OrderValidator.validate(order)
        }

        withSpan("save-order") {
            db.insert(order)
        }

        order
    }
}
```

## 5. Sampling Strategies for Production

Choose the right strategy based on your needs:

```kotlin
import io.pandu.sampling.strategy.*

// Development: trace everything
samplingStrategy(AlwaysSamplingStrategy)

// Production: sample 10% of traces
samplingStrategy(ProbabilisticSamplingStrategy(0.1f))

// High-throughput: max 100 traces per second
samplingStrategy(RateLimitedSamplingStrategy(maxPerSecond = 100))

// Per-operation: critical paths get higher sampling
samplingStrategy(OperationBasedSamplingStrategy(
    rules = listOf(
        OperationBasedSamplingStrategy.OperationRule(
            Regex("payment-.*"), 1.0f       // Always trace payments
        ),
        OperationBasedSamplingStrategy.OperationRule(
            Regex("health-.*"), 0.01f       // Rarely trace health checks
        ),
    ),
    defaultRate = 0.1f                      // 10% for everything else
))

// Combine strategies
samplingStrategy(CompositeSamplingStrategy(
    strategies = listOf(
        OperationBasedSamplingStrategy(rules = listOf(...), defaultRate = 0.1f),
        RateLimitedSamplingStrategy(maxPerSecond = 1000)
    ),
    mode = CompositeSamplingStrategy.Mode.ANY
))
```

## 6. Real-Time Visualization (Debug Builds)

You can visualize coroutine traces in real-time using the **comet-visualizer** web UI from your laptop browser while the app runs on an emulator.

### Architecture

```
┌──────────────────┐         SSE          ┌─────────────────┐
│  Android App     │ ──── events ────────→│  Laptop Browser  │
│  (Emulator)      │                      │  localhost:8080   │
│                  │                      │  comet-visualizer │
│  TraceServer:8080│ ←── http request ────│                   │
└──────────────────┘                      └─────────────────┘
        ↑
        │  adb forward tcp:8080 tcp:8080
        │
   ┌────┴────┐
   │  ADB    │
   └─────────┘
```

### Step 1: Add the visualizer dependency (debug only)

The comet-sample project includes a `TraceServer` you can embed in your app. For debug builds, set up a simple Ktor/embedded server or use the sample's `TraceServer`.

```kotlin
// In your debug Application class or debug-only initializer
import io.pandu.Comet
import io.pandu.core.telemetry.exporters.VisualizerJsonExporter

object CometDebugProvider {
    private val server = TraceServer(port = 8080)

    val comet: Comet = Comet.create {
        exporter(VisualizerJsonExporter(onEvent = { json ->
            server.sendEvent(json)
        }))
        includeStackTrace(true)  // Shows source file and line numbers
        bufferSize(8192)
    }

    fun start() {
        server.start()
        comet.start()
    }
}
```

### Step 2: Forward the port from emulator to laptop

Run this in your terminal:

```bash
adb forward tcp:8080 tcp:8080
```

This maps the emulator's port 8080 to your laptop's port 8080.

### Step 3: Open the visualizer

Open your laptop browser and navigate to:

```
http://localhost:8080
```

You'll see the comet-visualizer UI with:
- **Tree View** — hierarchical coroutine parent-child relationships
- **Gantt Chart** — timeline visualization with zoom (Ctrl+scroll)
- **Source Location** — click nodes to see file and line number
- **Stats** — running, completed, failed, cancelled counts

### Note on physical devices

If testing on a physical device instead of an emulator, use `adb forward` the same way — it works for both emulators and USB-connected devices:

```bash
adb forward tcp:8080 tcp:8080
```

Then browse `http://localhost:8080` on your laptop.

## 7. Production Configuration

### Build-variant-aware setup

```kotlin
// shared/src/commonMain/kotlin/CometProvider.kt
object CometProvider {
    fun create(isDebug: Boolean): Comet = Comet.create {
        if (isDebug) {
            samplingStrategy(AlwaysSamplingStrategy)
            includeStackTrace(true)
            exporter(CallbackCoroutineTelemetryExporter(
                onEvent = { event -> println("[Comet] $event") }
            ))
        } else {
            samplingStrategy(ProbabilisticSamplingStrategy(0.05f)) // 5% in prod
            includeStackTrace(false) // Reduce overhead
            // Add your production exporter here
        }
        bufferSize(8192)
    }
}
```

```kotlin
// androidApp/src/main/kotlin/MyApplication.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val comet = CometProvider.create(isDebug = BuildConfig.DEBUG)
        comet.start()
    }
}
```

### Best practices

| Setting | Debug | Production |
|---------|-------|------------|
| `samplingStrategy` | `AlwaysSamplingStrategy` | `ProbabilisticSamplingStrategy(0.05f)` |
| `includeStackTrace` | `true` | `false` |
| `bufferSize` | `8192` | `4096` |
| `flushInterval` | `1.seconds` | `10.seconds` (default) |

### Lifecycle management

```kotlin
class MyApplication : Application() {
    private lateinit var comet: Comet

    override fun onCreate() {
        super.onCreate()
        comet = CometProvider.create(BuildConfig.DEBUG)
        comet.start()

        // Flush on app backgrounded
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    // App going to background - flush pending events
                    CoroutineScope(Dispatchers.IO).launch {
                        comet.flush()
                    }
                }
            }
        )
    }
}
```

## 8. Full Example

Here's a complete minimal setup:

```kotlin
// shared/src/commonMain/kotlin/di/CometModule.kt
object CometModule {
    val comet: Comet by lazy {
        Comet.create {
            samplingStrategy(AlwaysSamplingStrategy)
            exporter(CallbackCoroutineTelemetryExporter(
                onEvent = { println("[Comet] $it") }
            ))
            includeCoroutineName(true)
            bufferSize(8192)
        }.also { it.start() }
    }
}

// shared/src/commonMain/kotlin/feature/HomeViewModel.kt
class HomeViewModel : ViewModel() {
    private val comet = CometModule.comet

    fun refresh() {
        viewModelScope.launch(comet.traced("refresh-home")) {
            val feed = async(CoroutineName("fetch-feed")) { feedRepo.getFeed() }
            val user = async(CoroutineName("fetch-user")) { userRepo.getCurrent() }
            _state.value = HomeState(feed.await(), user.await())
        }
    }
}
```

## Accessing Metrics

You can read real-time metrics at any point:

```kotlin
val metrics = CometModule.comet.metrics
println("Active: ${metrics.activeCoroutines}")
println("Total started: ${metrics.totalStarted}")
println("Total completed: ${metrics.totalCompleted}")
println("Total failed: ${metrics.totalFailed}")
```
