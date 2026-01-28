# Integrating Comet with KMM iOS Project

This guide walks you through integrating the Comet coroutine telemetry library into the iOS side of a Kotlin Multiplatform Mobile (KMM) project.

## Prerequisites

- KMM project with iOS target (iosX64, iosArm64, iosSimulatorArm64)
- Kotlin 2.0+ and Gradle 8+
- Xcode 15+
- CocoaPods or SPM for framework distribution

## 1. Installation

### Add dependency to shared module

In your **shared module's** `build.gradle.kts`:

```kotlin
kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.pandubaraja:comet:0.1.0")
        }
    }
}
```

The Comet library is a KMP library, so it ships iOS targets out of the box. No additional iOS-specific dependency is needed.

## 2. Expose Comet from Shared Module

Create a shared provider that both Android and iOS can use:

```kotlin
// shared/src/commonMain/kotlin/CometProvider.kt
import io.pandu.Comet
import io.pandu.core.telemetry.exporters.CallbackCoroutineTelemetryExporter
import io.pandu.sampling.strategy.AlwaysSamplingStrategy

object CometProvider {
    val comet: Comet = Comet.create {
        samplingStrategy(AlwaysSamplingStrategy)
        exporter(CallbackCoroutineTelemetryExporter(
            onEvent = { event -> println("[Comet] $event") }
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

## 3. Initialize from Swift

Call `CometProvider.start()` from your iOS app entry point:

```swift
// iOSApp/Sources/App.swift
import SwiftUI
import Shared

@main
struct MyApp: App {
    init() {
        CometProvider.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

## 4. Tracing in Shared ViewModels

Create traced coroutines in your shared KMM ViewModel or presenter layer:

```kotlin
// shared/src/commonMain/kotlin/feature/UserViewModel.kt
import io.pandu.Comet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class UserViewModel(
    private val userRepo: UserRepository,
    private val prefsRepo: PrefsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val comet = CometProvider.comet

    private val _state = MutableStateFlow<UserState?>(null)
    val state: StateFlow<UserState?> = _state

    fun loadUser(id: String) {
        scope.launch(comet.traced("load-user")) {
            val user = async(CoroutineName("fetch-user")) {
                userRepo.getUser(id)
            }
            val prefs = async(CoroutineName("fetch-prefs")) {
                prefsRepo.getPrefs(id)
            }
            _state.value = UserState(user.await(), prefs.await())
        }
    }

    fun clear() {
        scope.cancel()
    }
}
```

### Consuming from SwiftUI

```swift
// iOSApp/Sources/UserScreen.swift
import SwiftUI
import Shared

struct UserScreen: View {
    @StateObject private var viewModel = UserViewModelWrapper()

    var body: some View {
        VStack {
            if let state = viewModel.state {
                Text(state.user.name)
            } else {
                ProgressView()
            }
        }
        .onAppear { viewModel.loadUser(id: "123") }
    }
}

class UserViewModelWrapper: ObservableObject {
    private let vm = UserViewModel(
        userRepo: UserRepositoryImpl(),
        prefsRepo: PrefsRepositoryImpl()
    )

    @Published var state: UserState? = nil

    func loadUser(id: String) {
        vm.loadUser(id: id)
        // Observe state flow using SKIE, KMP-NativeCoroutines, or manual collection
    }
}
```

## 5. Tracing in Repositories

Use `withSpan` to trace suspend function blocks in shared code:

```kotlin
// shared/src/commonMain/kotlin/data/OrderRepository.kt
import io.pandu.core.tools.withSpan

class OrderRepository(private val api: OrderApi) {

    suspend fun processOrder(orderId: String): Order = withSpan("process-order") {
        val order = withSpan("fetch-order") {
            api.getOrder(orderId)
        }
        withSpan("validate-order") {
            OrderValidator.validate(order)
        }
        order
    }
}
```

This works identically on iOS and Android since it's in `commonMain`.

## 6. Sampling Strategies

```kotlin
import io.pandu.sampling.strategy.*

// Development: trace everything
samplingStrategy(AlwaysSamplingStrategy)

// Production: sample 5%
samplingStrategy(ProbabilisticSamplingStrategy(0.05f))

// Rate-limited: max 50 traces per second
samplingStrategy(RateLimitedSamplingStrategy(maxPerSecond = 50))

// Per-operation
samplingStrategy(OperationBasedSamplingStrategy(
    rules = listOf(
        OperationBasedSamplingStrategy.OperationRule(
            Regex("checkout-.*"), 1.0f
        ),
    ),
    defaultRate = 0.1f
))
```

## 7. Real-Time Visualization (Debug Builds)

Since iOS simulators and devices share the Mac's network, you can run the visualizer server as a separate JVM process on your Mac.

### Option A: Run TraceServer on Mac (Recommended)

1. Run the comet-sample's `RealtimeVisualizer` on your Mac:

```bash
cd comet-sample
./gradlew run
```

This starts a `TraceServer` on `http://localhost:8080`.

2. Configure your shared module to export to the Mac server:

```kotlin
// shared/src/iosMain/kotlin/CometDebugProvider.kt
import io.pandu.Comet
import io.pandu.core.telemetry.exporters.CallbackCoroutineTelemetryExporter

object CometDebugProvider {
    val comet: Comet = Comet.create {
        exporter(CallbackCoroutineTelemetryExporter(
            onEvent = { event ->
                // Send to Mac's TraceServer via HTTP
                // Use your preferred HTTP client (Ktor, URLSession)
                sendToVisualizer("http://localhost:8080/events", event.toString())
            }
        ))
        includeStackTrace(true)
        bufferSize(8192)
    }
}
```

3. Open `http://localhost:8080` in your Mac's browser.

### Option B: Embedded server in the iOS app

If using Ktor for iOS, you can embed a server directly in the app. The simulator shares `localhost` with your Mac, so you can browse directly.

For physical devices, use your Mac's local IP (e.g., `http://192.168.1.x:8080`).

## 8. Production Configuration

### Build-variant-aware setup

```kotlin
// shared/src/commonMain/kotlin/CometProvider.kt
import io.pandu.sampling.strategy.*

object CometProvider {
    fun create(isDebug: Boolean): Comet = Comet.create {
        if (isDebug) {
            samplingStrategy(AlwaysSamplingStrategy)
            includeStackTrace(true)
            exporter(CallbackCoroutineTelemetryExporter(
                onEvent = { println("[Comet] $it") }
            ))
        } else {
            samplingStrategy(ProbabilisticSamplingStrategy(0.05f))
            includeStackTrace(false)
        }
        bufferSize(8192)
    }
}
```

Pass the debug flag from each platform:

```swift
// iOS
CometProvider.shared.create(isDebug: true)  // or use #if DEBUG
```

```kotlin
// Android
CometProvider.create(isDebug = BuildConfig.DEBUG)
```

## iOS-Specific Limitations

| Feature | iOS | Android/JVM |
|---------|-----|-------------|
| Stack traces | Limited | Full |
| Thread names | Limited | Full |
| Coroutine tracing | Full | Full |
| Sampling strategies | Full | Full |
| withSpan | Full | Full |

Stack trace capture on iOS is limited due to Kotlin/Native constraints. The `includeStackTrace(true)` option still works but may produce less detailed frames compared to JVM/Android.

## Full Example

```kotlin
// shared/src/commonMain/kotlin/App.kt
object App {
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

// shared/src/commonMain/kotlin/feature/HomePresenter.kt
class HomePresenter(private val feedRepo: FeedRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun refresh() {
        scope.launch(App.comet.traced("refresh-home")) {
            val feed = async(CoroutineName("fetch-feed")) { feedRepo.getFeed() }
            // Result flows to UI via StateFlow
        }
    }

    fun destroy() { scope.cancel() }
}
```

```swift
// Swift side
struct HomeScreen: View {
    let presenter = HomePresenter(feedRepo: FeedRepositoryImpl())

    var body: some Scene {
        // ...
    }

    func onAppear() {
        presenter.refresh()
    }
}
```
