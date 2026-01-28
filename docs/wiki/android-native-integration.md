# Integrating Comet with Android Project (Non-KMM)

This guide walks you through integrating the Comet coroutine telemetry library into a standard Android project (not Kotlin Multiplatform).

## Prerequisites

- Android project with Kotlin
- `minSdk 21` or higher
- `kotlinx-coroutines-android` dependency

## 1. Installation

Add the dependency to your app module's `build.gradle.kts`:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.pandubaraja:comet:0.1.0")

    // Required: coroutines (you likely already have this)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
```

## 2. Initialize Comet

Create a `CometProvider` singleton and start it from your `Application` class:

```kotlin
// app/src/main/kotlin/CometProvider.kt
import android.util.Log
import io.pandu.Comet
import io.pandu.core.telemetry.exporters.CallbackCoroutineTelemetryExporter
import io.pandu.sampling.strategy.AlwaysSamplingStrategy

object CometProvider {
    val comet: Comet = Comet.create {
        samplingStrategy(AlwaysSamplingStrategy)
        exporter(CallbackCoroutineTelemetryExporter(
            onEvent = { event -> Log.d("Comet", event.toString()) }
        ))
        includeCoroutineName(true)
        bufferSize(8192)
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
// app/src/main/kotlin/MyApplication.kt
import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CometProvider.start()
    }
}
```

Register in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApplication"
    ... />
```

## 3. Tracing in ViewModels

Add `comet.traced("operation")` to your `viewModelScope.launch` calls:

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        viewModelScope.launch { comet.flush() }
    }
}
```

## 4. Tracing in Repositories

Use `withSpan` to trace blocks within suspend functions:

```kotlin
import io.pandu.core.tools.withSpan

class OrderRepository(
    private val api: OrderApi,
    private val db: OrderDao
) {
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

## 5. Tracing in Fragments and Activities

```kotlin
class UserFragment : Fragment() {

    private val comet = CometProvider.comet

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch(comet.traced("load-screen")) {
            val data = async(CoroutineName("fetch-data")) {
                repository.fetchData()
            }
            binding.textView.text = data.await().toString()
        }
    }
}
```

## 6. Tracing WorkManager Workers

```kotlin
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val comet = CometProvider.comet

    override suspend fun doWork(): Result = withSpan("sync-worker") {
        try {
            withSpan("upload-pending") {
                syncRepo.uploadPending()
            }
            withSpan("download-updates") {
                syncRepo.downloadUpdates()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

## 7. Sampling Strategies

Choose the right strategy for your environment:

```kotlin
import io.pandu.sampling.strategy.*

// Development: trace everything
samplingStrategy(AlwaysSamplingStrategy)

// Production: sample 10%
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
            Regex("analytics-.*"), 0.01f    // Rarely trace analytics
        ),
    ),
    defaultRate = 0.1f
))
```

## 8. Real-Time Visualization (Debug Builds)

Visualize coroutine traces in real-time on your laptop browser while the app runs on an emulator or device.

### Setup

```kotlin
// Only in debug builds
import io.pandu.core.telemetry.exporters.VisualizerJsonExporter

class DebugApplication : MyApplication() {
    override fun onCreate() {
        super.onCreate()

        // Start embedded TraceServer
        val server = TraceServer(port = 8080)
        server.start()

        comet = Comet.create {
            exporter(VisualizerJsonExporter(onEvent = { json ->
                server.sendEvent(json)
            }))
            includeStackTrace(true)
            bufferSize(8192)
        }
        comet.start()
    }
}
```

### Port forwarding (emulator or USB device)

```bash
adb forward tcp:8080 tcp:8080
```

### Open the visualizer

Browse to `http://localhost:8080` on your laptop. You'll see:

- **Tree View** — coroutine parent-child hierarchy
- **Gantt Chart** — timeline with zoom (Ctrl+scroll)
- **Source Location** — file and line number per coroutine
- **Stats** — running, completed, failed, cancelled counts

## 9. Production Configuration

### Build-variant-aware setup

```kotlin
// app/src/main/kotlin/CometProvider.kt
object CometProvider {
    fun create(isDebug: Boolean): Comet = Comet.create {
        if (isDebug) {
            samplingStrategy(AlwaysSamplingStrategy)
            includeStackTrace(true)
            exporter(CallbackCoroutineTelemetryExporter(
                onEvent = { Log.d("Comet", it.toString()) }
            ))
        } else {
            samplingStrategy(ProbabilisticSamplingStrategy(0.05f))
            includeStackTrace(false)
            // Add your production exporter here
        }
        bufferSize(8192)
        onError { e -> Log.e("Comet", "Telemetry error", e) }
    }
}
```

```kotlin
// app/src/main/kotlin/MyApplication.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val comet = CometProvider.create(isDebug = BuildConfig.DEBUG)
        comet.start()
    }
}
```

### Lifecycle management

```kotlin
class MyApplication : Application() {
    private lateinit var comet: Comet

    override fun onCreate() {
        super.onCreate()
        comet = CometProvider.create(BuildConfig.DEBUG)
        comet.start()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    // App backgrounded - flush pending events
                    CoroutineScope(Dispatchers.IO).launch {
                        comet.flush()
                    }
                }
            }
        )
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

## 10. Accessing Metrics

Read real-time metrics anywhere in your app:

```kotlin
val metrics = CometProvider.comet.metrics
Log.d("Comet", """
    Active: ${metrics.activeCoroutines}
    Total started: ${metrics.totalStarted}
    Total completed: ${metrics.totalCompleted}
    Total failed: ${metrics.totalFailed}
""".trimIndent())
```

## Full Example

```kotlin
// CometProvider.kt
object CometProvider {
    val comet: Comet = Comet.create {
        samplingStrategy(AlwaysSamplingStrategy)
        exporter(CallbackCoroutineTelemetryExporter(
            onEvent = { Log.d("Comet", it.toString()) }
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

// MyApplication.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CometProvider.start()
    }
}

// HomeViewModel.kt
class HomeViewModel : ViewModel() {
    private val comet = CometProvider.comet

    fun refresh() {
        viewModelScope.launch(comet.traced("refresh-home")) {
            val feed = async(CoroutineName("fetch-feed")) { feedRepo.getFeed() }
            val user = async(CoroutineName("fetch-user")) { userRepo.getCurrent() }
            _state.value = HomeState(feed.await(), user.await())
        }
    }
}

// DataRepository.kt
class DataRepository(private val api: Api) {
    suspend fun fetchData(): Data = withSpan("fetch-data") {
        val raw = withSpan("api-call") { api.getData() }
        withSpan("transform") { transform(raw) }
    }
}
```
