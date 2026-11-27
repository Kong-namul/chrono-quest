# 크로노 퀘스트 A2b-1: 실행용 앱과 홈 화면 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 안드로이드 앱 모듈 `app`을 만든다. 들어가는 것은 다음과 같다.
- 디자인 토큰에서 Compose 테마를 생성한다.
- 홈 화면: 티어, 대표 칭호, XP, 정리 필요, 오늘의 퀘스트, 출발·도착 버튼
- 확인 창: 안전 여부, 30분 이상 일찍 누름, 일정 시각 변경, 행동통제
- 정산 알림
- 화면은 갤럭시 Z 플립5 크기 이미지로 맥에서 확인한다(Roborazzi). 폰 설치는 USB가 준비되면 따로 한다.

**Architecture:** `android-core/app`(com.android.application)이 `core`를 의존한다. 층은 이렇게 나눈다.
- `AppContainer`: DB, 칭호 카탈로그, 엔진, `GameService`, `ScheduleService`, 시계, 알림기를 프로세스에 하나씩 만든다.
- `HomePresenter`: 화면 상태(`HomeUiState`)와 모든 동작을 담는다. 순수 코루틴이라 Robolectric으로 테스트한다.
- `HomeViewModel`: presenter를 `viewModelScope`에 연결만 한다.
- 화면: 상태만 받아 그리는 Compose 함수다. 디자인 토큰은 루트 `design-tokens.json` 하나가 원본이고, 스크립트가 `Tokens.kt`를 만든다.

**Tech Stack:** 기존 Gradle 8.14.3 / Kotlin 2.2.20 / AGP 8.13.2, Compose BOM `2025.10.01`(UI 1.9.4, Material3 1.4.0), `org.jetbrains.kotlin.plugin.compose` 2.2.20, activity-compose 1.11.0, lifecycle 2.9.4, core-ktx 1.17.0, coroutines 1.10.2, Robolectric 4.16.1, Roborazzi 1.51.0, kotlinx-serialization-json 1.9.0(테스트에서 JSON 읽기용), Python 3(토큰 생성기)

**스펙:** [`docs/superpowers/specs/2026-11-25-chrono-quest-design.md`](../specs/2026-11-25-chrono-quest-design.md) 6.2(기록 흐름·기록 가능 시간), 6.3(정산 알림), 7.2(네이티브 화면), 9(디자인 토큰)
**인계 메모:** [`docs/superpowers/notes/2026-11-25-a1-carryover.md`](../notes/2026-11-25-a1-carryover.md)의 "A2b가 꼭 반영할 것"

## Global Constraints

- 버전 고정: Compose BOM `2025.10.01`, compose 컴파일러 플러그인 `2.2.20`, activity-compose `1.11.0`, lifecycle-runtime-compose/lifecycle-viewmodel-compose `2.9.4`, core-ktx `1.17.0`, kotlinx-coroutines-android `1.10.2`, Roborazzi(플러그인·라이브러리) `1.51.0`, kotlinx-serialization-json `1.9.0`(testImplementation). 그 밖의 버전은 A2a와 같다.
- `app` 모듈: namespace와 applicationId는 `com.chronoquest.app`, `compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`, 바이트코드 17, `android:allowBackup="true"`
- 대상 기기는 갤럭시 Z 플립5다. 화면 사진 기준은 Robolectric qualifiers `w411dp-h1006dp-420dpi`(약 1080×2640 px, 22:9)다.
- 서비스는 `AppContainer`에서만 만든다. 화면과 presenter는 DAO를 직접 쓰지 않는다(쓰기는 전부 `GameService`·`ScheduleService`).
- 시계는 `SystemZoneClock`이다. 호출할 때마다 `ZoneId.systemDefault()`를 읽는다.
- 디자인 토큰(사용자 전역 규칙):
  - 색과 글자 크기는 `ChronoTokens`(생성 파일)에서만 가져온다. 하드코딩하지 않는다.
  - 글자 크기는 72/48/32/24/22/18/14 중에서만 쓴다.
  - 간격은 `ChronoTokens.Space`(Xs 4 / Sm 8 / Md 12 / Lg 16 / Xl 24), 모서리는 `ChronoTokens.Radius`(Sm 8 / Md 12 / Lg 16)만 쓴다(사용자 승인 2026-11-26). 엠블럼 크기, 바 높이, 테두리 두께 같은 부품 고유 크기는 그 화면 파일 안에 이름 붙은 상수로 둔다.
  - 제목은 단색(`Text1`)이다. 보라색은 쓰지 않는다.
  - 공통에 없는 토큰은 사용자 확인 없이 추가하지 않는다. 티어 8색은 Task 3에서 사용자가 고른 시안으로 등록한다.
- 화면 문구는 반말이다(앱이 사용자에게 말하는 톤, 스펙 6장 예시와 같다).
- 테스트:
  - presenter와 컨테이너는 Robolectric(`sdk=35`)
  - 토큰 일치 검사는 JUnit
  - 화면 사진은 Roborazzi(`@GraphicsMode(NATIVE)`)
  - 사진 파일은 `android-core/app/screenshots/*.png`에 기록하고 커밋한다. 갱신은 `./gradlew :app:recordRoborazziDebug`로 한다.
- 작업 브랜치는 `a2b1-app-home`이고 `main`에서 딴다. 커밋 메시지 끝에는 `Co-Authored-By` 트레일러를 붙인다. 커밋할 때 `.superpowers/`는 넣지 않는다.
- Gradle은 `/Users/sang/Desktop/chrono-oath/android-core`에서 실행한다. 새 라이브러리를 처음 받을 때만 온라인으로 하고(느린 네트워크라 최대 20분), 그다음부터는 `--offline`을 쓴다.

## 파일 구조

```
design-tokens.json                                  # 디자인 토큰 원본 (SSOT)
tools/gen-tokens/gen_tokens.py                      # JSON → Tokens.kt
android-core/
  settings.gradle.kts                               # :app 추가
  build.gradle.kts                                  # application, compose, roborazzi 플러그인 선언
  core/build.gradle.kts                             # room-runtime을 api로 (app이 ChronoDatabase 타입을 씀)
  app/
    build.gradle.kts
    screenshots/*.png                               # Roborazzi 기록 (커밋)
    src/main/AndroidManifest.xml
    src/main/res/values/strings.xml
    src/main/res/drawable/ic_stat_chrono.xml
    src/main/kotlin/com/chronoquest/app/
      ChronoApp.kt, AppContainer.kt, SystemZoneClock.kt, MainActivity.kt
      ui/theme/Tokens.kt (생성), ui/theme/Theme.kt
      ui/MainScaffold.kt                            # 하단 탭 4개
      home/HomeUiState.kt, home/HomePresenter.kt, home/HomeViewModel.kt
      home/HomeScreen.kt, home/HomeDialogs.kt
      notify/SettlementNotifier.kt
    src/test/resources/robolectric.properties
    src/test/kotlin/com/chronoquest/app/
      AppFixtures.kt, AppContainerTest.kt, TokensTest.kt, TierPaletteScreenshotTest.kt,
      home/HomePresenterTest.kt, home/HomeScreenshotTest.kt, notify/SettlementNotifierTest.kt
```

---

### Task 1: 앱 모듈 셋업, 컨테이너, 시계

**Files:**
- Modify: `android-core/settings.gradle.kts`, `android-core/build.gradle.kts`, `android-core/core/build.gradle.kts`
- Create: `android-core/app/build.gradle.kts`, `android-core/app/src/main/AndroidManifest.xml`, `android-core/app/src/main/res/values/strings.xml`
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/{ChronoApp,AppContainer,SystemZoneClock,MainActivity}.kt`
- Create: `android-core/app/src/test/resources/robolectric.properties`
- Test: `android-core/app/src/test/kotlin/com/chronoquest/app/AppContainerTest.kt`

**Interfaces:**
- Consumes: `com.chronoquest.core.data.ChronoDatabase.build(context)`, `GameService(db, clock, catalog, engine)`, `ScheduleService(db, clock)`, `TitleCatalog.load()`, `Engine(catalog)`
- Produces: `class SystemZoneClock : Clock`, `class AppContainer(context: Context)` with `clock`, `catalog`, `database`, `engine`, `gameService`, `scheduleService`; `class ChronoApp : Application { val container: AppContainer }`; `MainActivity`(Task 5에서 내용 교체)

- [ ] **Step 1: Gradle 설정**

`android-core/settings.gradle.kts`의 include를 바꾼다.

```kotlin
include(":rules", ":core", ":app")
```

`android-core/build.gradle.kts` 전체:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20" apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
    kotlin("android") version "2.2.20" apply false
    kotlin("plugin.compose") version "2.2.20" apply false
    id("com.android.library") version "8.13.2" apply false
    id("com.android.application") version "8.13.2" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.4" apply false
    id("androidx.room") version "2.8.3" apply false
    id("io.github.takahirom.roborazzi") version "1.51.0" apply false
}
```

`android-core/core/build.gradle.kts`에서 한 줄을 바꾼다. `app`이 `ChronoDatabase`(RoomDatabase 하위 타입)를 다루므로 Room 런타임을 노출한다.

변경 전:
```kotlin
    implementation("androidx.room:room-runtime:2.8.3")
```
변경 후:
```kotlin
    api("androidx.room:room-runtime:2.8.3")
```

`android-core/app/build.gradle.kts`:

```kotlin
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.chronoquest.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chronoquest.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.51.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.51.0")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
```

`android-core/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".ChronoApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`android-core/app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">크로노 퀘스트</string>
</resources>
```

`android-core/app/src/test/resources/robolectric.properties`:

```properties
sdk=35
```

- [ ] **Step 2: 실패 테스트 작성**

`android-core/app/src/test/kotlin/com/chronoquest/app/AppContainerTest.kt`:

```kotlin
package com.chronoquest.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppContainerTest {
    @Test
    fun `서비스는 컨테이너 안에서 하나씩만 만든다`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        assertSame(container.gameService, container.gameService)
        assertSame(container.scheduleService, container.scheduleService)
        assertSame(container.catalog, container.catalog)
        assertSame(container.database, container.database)
    }

    @Test
    fun `앱은 컨테이너를 하나만 가진다`() {
        val app = ApplicationProvider.getApplicationContext<ChronoApp>()
        assertSame(app.container, app.container)
    }

    @Test
    fun `앱 시계는 기기 타임존이 바뀌면 따라간다`() {
        val original = TimeZone.getDefault()
        try {
            val clock = SystemZoneClock()
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
            assertEquals(ZoneId.of("Asia/Seoul"), clock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            assertEquals(ZoneId.of("Europe/London"), clock.zone)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
```

- [ ] **Step 3: 실패 확인 (첫 실행은 Compose·Roborazzi 다운로드로 오래 걸림)**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest`
Expected: FAIL, `Unresolved reference 'AppContainer'` / `'SystemZoneClock'` / `'ChronoApp'`

- [ ] **Step 4: 구현**

`android-core/app/src/main/kotlin/com/chronoquest/app/SystemZoneClock.kt`:

```kotlin
package com.chronoquest.app

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** 호출할 때마다 기기의 현재 타임존을 읽는 시계. 타임존이 바뀌어도 다시 만들 필요가 없다(인계 메모 A2b "시계"). */
class SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

    override fun instant(): Instant = Instant.now()
}
```

`android-core/app/src/main/kotlin/com/chronoquest/app/AppContainer.kt`:

```kotlin
package com.chronoquest.app

import android.content.Context
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.engine.Engine
import com.chronoquest.rules.titles.TitleCatalog
import java.time.Clock

/**
 * 앱 전체에서 하나씩만 쓰는 객체들(인계 메모 A2b "프로세스당 하나").
 * 화면, 알림 리시버, 워커는 모두 여기서 꺼내 쓴다.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = SystemZoneClock()
    val catalog: TitleCatalog by lazy { TitleCatalog.load() }
    val database: ChronoDatabase by lazy { ChronoDatabase.build(appContext) }
    val engine: Engine by lazy { Engine(catalog) }
    val gameService: GameService by lazy { GameService(database, clock, catalog, engine) }
    val scheduleService: ScheduleService by lazy { ScheduleService(database, clock) }
}
```

`android-core/app/src/main/kotlin/com/chronoquest/app/ChronoApp.kt`:

```kotlin
package com.chronoquest.app

import android.app.Application

class ChronoApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
```

`android-core/app/src/main/kotlin/com/chronoquest/app/MainActivity.kt` (Task 5에서 내용을 바꾼다):

```kotlin
package com.chronoquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("크로노 퀘스트") }
    }
}
```

- [ ] **Step 5: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest :app:assembleDebug :core:testDebugUnitTest --offline`
Expected: `BUILD SUCCESSFUL`. AppContainerTest 3개가 통과하고, core 79개도 그대로 통과한다.

- [ ] **Step 6: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/settings.gradle.kts android-core/build.gradle.kts android-core/core/build.gradle.kts android-core/app
git commit -m "feat(app): 앱 모듈, 컨테이너, 타임존을 따라가는 시계" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 디자인 토큰 생성기, 테마, 티어 색 시안 이미지

**Files:**
- Create: `design-tokens.json`, `tools/gen-tokens/gen_tokens.py`
- Create (generated): `android-core/app/src/main/kotlin/com/chronoquest/app/ui/theme/Tokens.kt`
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/ui/theme/Theme.kt`
- Create: `android-core/app/src/test/kotlin/com/chronoquest/app/AppFixtures.kt`
- Test: `android-core/app/src/test/kotlin/com/chronoquest/app/TokensTest.kt`
- Test: `android-core/app/src/test/kotlin/com/chronoquest/app/TierPaletteScreenshotTest.kt`
- Generated (commit): `android-core/app/screenshots/tier_palettes.png`

스펙 9장은 생성 파일 위치를 `android-core/core/.../Tokens.kt`로 적었지만, Compose는 `app` 모듈에만 있으므로 `app` 아래에 만든다(스펙도 같이 고친다). Unity용 `Tokens.uss` 출력은 Phase B에서 생성기에 추가한다.

**Interfaces:**
- Produces:
  - `object ChronoTokens { object Colors { Bg1, Bg2, Text1, Text2, Border, Blue500, Blue700, Blue50, Gold500, Gold700, Yellow400, Yellow50: Color; val byKey: Map<String, Color> }; object Tier { val all: Map<String, Color> }; object TypeScale { S72, S48, S32, S24, S22, S18, S14: TextUnit; val sizes: List<Int> }; object Space { Xs, Sm, Md, Lg, Xl: Dp; val all: Map<String, Dp> }; object Radius { Sm, Md, Lg: Dp; val all: Map<String, Dp> } }`
  - `@Composable fun ChronoTheme(content: @Composable () -> Unit)`, `val ChronoFont: FontFamily`
  - 테스트 픽스처: `FLIP5 = "w411dp-h1006dp-420dpi"`

- [ ] **Step 1: 토큰 원본과 생성기 작성**

`design-tokens.json` (색은 스펙 9장 표의 값, 간격·모서리는 사용자가 2026-11-26에 승인한 값, 티어는 Task 3에서 채운다):

```json
{
  "color": {
    "bg.1": "#FFFFFF",
    "bg.2": "#F7F8FC",
    "text.1": "#1A1C24",
    "text.2": "#6A6D7A",
    "border": "#E6E8F0",
    "blue.500": "#2F6FED",
    "blue.700": "#1F55C4",
    "blue.50": "#EAF1FE",
    "gold.500": "#E8B530",
    "gold.700": "#B8861B",
    "yellow.400": "#FFD54A",
    "yellow.50": "#FFF3C4"
  },
  "tier": {},
  "typeScale": [72, 48, 32, 24, 22, 18, 14],
  "space": { "xs": 4, "sm": 8, "md": 12, "lg": 16, "xl": 24 },
  "radius": { "sm": 8, "md": 12, "lg": 16 },
  "font": "Pretendard"
}
```

`tools/gen-tokens/gen_tokens.py`:

```python
#!/usr/bin/env python3
"""design-tokens.json -> android-core/app/.../ui/theme/Tokens.kt

사용법: python3 tools/gen-tokens/gen_tokens.py  (저장소 루트에서)
생성 파일은 직접 고치지 않는다. 원본(design-tokens.json)을 고치고 다시 실행한다.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "design-tokens.json"
OUT = ROOT / "android-core/app/src/main/kotlin/com/chronoquest/app/ui/theme/Tokens.kt"


def kotlin_name(key: str) -> str:
    return "".join(part[:1].upper() + part[1:].lower() for part in key.split("."))


def color_literal(hex_value: str) -> str:
    value = hex_value.lstrip("#").upper()
    if len(value) != 6:
        raise ValueError(f"색은 #RRGGBB 형식이어야 한다: {hex_value}")
    return f"Color(0xFF{value})"


def main() -> None:
    tokens = json.loads(SRC.read_text(encoding="utf-8"))
    lines = [
        "// 생성 파일: tools/gen-tokens/gen_tokens.py가 design-tokens.json에서 만든다. 직접 고치지 말 것.",
        "package com.chronoquest.app.ui.theme",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.unit.Dp",
        "import androidx.compose.ui.unit.dp",
        "import androidx.compose.ui.unit.sp",
        "",
        "object ChronoTokens {",
        "    object Colors {",
    ]
    for key, value in tokens["color"].items():
        lines.append(f"        val {kotlin_name(key)} = {color_literal(value)}")
    lines.append("")
    lines.append("        val byKey: Map<String, Color> = mapOf(")
    for key in tokens["color"]:
        lines.append(f'            "{key}" to {kotlin_name(key)},')
    lines.append("        )")
    lines.append("    }")
    lines.append("")
    lines.append("    object Tier {")
    for key, value in tokens["tier"].items():
        lines.append(f"        val {kotlin_name(key)} = {color_literal(value)}")
    if tokens["tier"]:
        lines.append("")
    lines.append("        val all: Map<String, Color> = mapOf(")
    for key in tokens["tier"]:
        lines.append(f'            "{key}" to {kotlin_name(key)},')
    lines.append("        )")
    lines.append("    }")
    lines.append("")
    lines.append("    object TypeScale {")
    for size in tokens["typeScale"]:
        lines.append(f"        val S{size} = {size}.sp")
    lines.append("")
    sizes = ", ".join(str(s) for s in tokens["typeScale"])
    lines.append(f"        val sizes: List<Int> = listOf({sizes})")
    lines.append("    }")
    for obj, key in (("Space", "space"), ("Radius", "radius")):
        lines.append("")
        lines.append(f"    object {obj} {{")
        for name, value in tokens[key].items():
            lines.append(f"        val {kotlin_name(name)} = {int(value)}.dp")
        lines.append("")
        lines.append("        val all: Map<String, Dp> = mapOf(")
        for name in tokens[key]:
            lines.append(f'            "{name}" to {kotlin_name(name)},')
        lines.append("        )")
        lines.append("    }")
    lines.append("}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
```

Run: `cd /Users/sang/Desktop/chrono-oath && python3 tools/gen-tokens/gen_tokens.py`
Expected: `wrote android-core/app/src/main/kotlin/com/chronoquest/app/ui/theme/Tokens.kt`

스펙 `docs/superpowers/specs/2026-11-25-chrono-quest-design.md` 9장의 생성 위치 한 줄을 고친다.

변경 전:
```markdown
- **생성**: `tools/gen-tokens`가 `unity/.../Tokens.uss`(UI Toolkit 변수)와 `android-core/core/.../Tokens.kt`(Compose)를 만든다. 생성 파일은 직접 수정하지 않는다.
```
변경 후:
```markdown
- **생성**: `tools/gen-tokens`가 `android-core/app/.../ui/theme/Tokens.kt`(Compose)를 만들고, Phase B에서 `unity/.../Tokens.uss`(UI Toolkit 변수)도 만든다. 생성 파일은 직접 수정하지 않는다.
```

- [ ] **Step 2: 실패 테스트 작성**

`android-core/app/src/test/kotlin/com/chronoquest/app/AppFixtures.kt` (이 태스크에서는 `FLIP5`만 쓰고, 나머지는 Task 4에서 쓴다):

```kotlin
package com.chronoquest.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.domain.QuestIds
import com.chronoquest.rules.model.QuestKind
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 갤럭시 Z 플립5 안쪽 화면(1080×2640 px, 약 425 ppi)에 맞춘 Robolectric qualifiers. */
const val FLIP5 = "w411dp-h1006dp-420dpi"

val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

fun d(date: String): LocalDate = LocalDate.parse(date)

fun dt(date: String, time: String): LocalDateTime = LocalDateTime.parse("${date}T$time")

/** 테스트에서 시각을 옮길 수 있는 시계. */
class MutableClock(var now: LocalDateTime) : Clock() {
    override fun getZone(): ZoneId = SEOUL

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now.atZone(SEOUL).toInstant()
}

fun questEntity(
    date: String,
    kind: QuestKind = QuestKind.WORK,
    due: String = "09:00",
    depart: String = "08:30",
    title: String = "출근",
): QuestEntity {
    val dueTime = LocalTime.parse(due)
    return QuestEntity(
        id = QuestIds.of(d(date), kind, dueTime),
        date = d(date),
        kind = kind,
        title = title,
        dueAt = d(date).atTime(dueTime),
        targetDepartAt = d(date).atTime(LocalTime.parse(depart)),
        source = QuestSource.MANUAL,
    )
}

fun inMemoryDb(): ChronoDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChronoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
```

`android-core/app/src/test/kotlin/com/chronoquest/app/TokensTest.kt`:

```kotlin
package com.chronoquest.app

import androidx.compose.ui.graphics.toArgb
import com.chronoquest.app.ui.theme.ChronoTokens
import java.io.File
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** 생성 파일(Tokens.kt)이 원본(design-tokens.json)과 어긋나지 않았는지 지킨다. */
class TokensTest {
    private val json = Json.parseToJsonElement(File("../../design-tokens.json").readText()).jsonObject

    private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    @Test
    fun `색 토큰이 원본과 같다`() {
        val expected = json.getValue("color").jsonObject.mapValues { it.value.jsonPrimitive.content.uppercase() }
        val actual = ChronoTokens.Colors.byKey.mapValues { hex(it.value.toArgb()) }
        assertEquals(expected, actual)
    }

    @Test
    fun `티어 색 토큰이 원본과 같다`() {
        val expected = json.getValue("tier").jsonObject.mapValues { it.value.jsonPrimitive.content.uppercase() }
        val actual = ChronoTokens.Tier.all.mapValues { hex(it.value.toArgb()) }
        assertEquals(expected, actual)
    }

    @Test
    fun `글자 크기는 공통 스케일만 쓴다`() {
        val expected = json.getValue("typeScale").jsonArray.map { it.jsonPrimitive.int }
        assertEquals(listOf(72, 48, 32, 24, 22, 18, 14), expected)
        assertEquals(expected, ChronoTokens.TypeScale.sizes)
    }

    @Test
    fun `간격과 모서리 토큰이 원본과 같다`() {
        fun numbers(key: String) = json.getValue(key).jsonObject.mapValues { it.value.jsonPrimitive.int }
        assertEquals(numbers("space"), ChronoTokens.Space.all.mapValues { it.value.value.toInt() })
        assertEquals(numbers("radius"), ChronoTokens.Radius.all.mapValues { it.value.value.toInt() })
    }
}
```

`android-core/app/src/test/kotlin/com/chronoquest/app/TierPaletteScreenshotTest.kt` (사용자가 고를 시안 두 개를 그린다. 아직 토큰이 아니므로 시안 값은 테스트 안에만 둔다):

```kotlin
package com.chronoquest.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.ui.theme.ChronoTheme
import com.chronoquest.app.ui.theme.ChronoTokens
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val TIERS = listOf("BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER", "LEGEND", "MYTHIC")

private val PALETTE_A = listOf("#B8733A", "#8E95A3", "#E8B530", "#2FA89C", "#3C8DDE", "#D64550", "#F07A1A", "#1F2A5A")
private val PALETTE_B = listOf("#B8733A", "#8E95A3", "#E8B530", "#2FA89C", "#3C8DDE", "#8A4FD8", "#D64550", "#F07A1A")

private fun color(hex: String) = Color(android.graphics.Color.parseColor(hex))

/** 시안 색 견본 크기(이 테스트 전용). */
private val SWATCH = 44.dp

@Composable
private fun PaletteColumn(title: String, palette: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
        TIERS.zip(palette).forEach { (tier, hex) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(SWATCH).background(color(hex), RoundedCornerShape(ChronoTokens.Radius.Md)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tier.take(1), style = MaterialTheme.typography.labelLarge, color = if (tier == "GOLD") ChronoTokens.Colors.Text1 else ChronoTokens.Colors.Bg1)
                }
                Text("  $tier", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text1)
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = FLIP5)
class TierPaletteScreenshotTest {
    @Test
    fun `티어 색 시안 두 가지`() {
        captureRoboImage("screenshots/tier_palettes.png") {
            ChronoTheme {
                Row(
                    modifier = Modifier.fillMaxSize().background(ChronoTokens.Colors.Bg1).padding(ChronoTokens.Space.Xl),
                    horizontalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Xl),
                ) {
                    PaletteColumn("A. 금속·보석", PALETTE_A)
                    PaletteColumn("B. 게임 관례형", PALETTE_B)
                }
            }
        }
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'ChronoTheme'` (Theme.kt 없음)

- [ ] **Step 4: 테마 구현**

`android-core/app/src/main/kotlin/com/chronoquest/app/ui/theme/Theme.kt`:

```kotlin
package com.chronoquest.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/** 앱 글꼴. Pretendard(Task 7)를 넣기 전까지는 기기 기본 글꼴이다. */
val ChronoFont: FontFamily = FontFamily.Default

private val LightColors = lightColorScheme(
    primary = ChronoTokens.Colors.Blue500,
    onPrimary = ChronoTokens.Colors.Bg1,
    primaryContainer = ChronoTokens.Colors.Blue50,
    onPrimaryContainer = ChronoTokens.Colors.Blue700,
    secondary = ChronoTokens.Colors.Gold500,
    onSecondary = ChronoTokens.Colors.Text1,
    secondaryContainer = ChronoTokens.Colors.Yellow50,
    onSecondaryContainer = ChronoTokens.Colors.Gold700,
    background = ChronoTokens.Colors.Bg1,
    onBackground = ChronoTokens.Colors.Text1,
    surface = ChronoTokens.Colors.Bg1,
    onSurface = ChronoTokens.Colors.Text1,
    surfaceVariant = ChronoTokens.Colors.Bg2,
    onSurfaceVariant = ChronoTokens.Colors.Text2,
    outline = ChronoTokens.Colors.Border,
    outlineVariant = ChronoTokens.Colors.Border,
)

private fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = ChronoFont, fontSize = size, fontWeight = weight, lineHeight = size * 1.4f)

/** 글자 크기는 공통 스케일(72/48/32/24/22/18/14)만 쓴다. */
private val ChronoTypography = Typography(
    displayLarge = style(ChronoTokens.TypeScale.S72, FontWeight.Bold),
    displayMedium = style(ChronoTokens.TypeScale.S48, FontWeight.Bold),
    displaySmall = style(ChronoTokens.TypeScale.S32, FontWeight.Bold),
    headlineLarge = style(ChronoTokens.TypeScale.S32, FontWeight.Bold),
    headlineMedium = style(ChronoTokens.TypeScale.S24, FontWeight.Bold),
    headlineSmall = style(ChronoTokens.TypeScale.S22, FontWeight.Bold),
    titleLarge = style(ChronoTokens.TypeScale.S22, FontWeight.Medium),
    titleMedium = style(ChronoTokens.TypeScale.S18, FontWeight.Medium),
    titleSmall = style(ChronoTokens.TypeScale.S14, FontWeight.Medium),
    bodyLarge = style(ChronoTokens.TypeScale.S18),
    bodyMedium = style(ChronoTokens.TypeScale.S14),
    bodySmall = style(ChronoTokens.TypeScale.S14),
    labelLarge = style(ChronoTokens.TypeScale.S14, FontWeight.Medium),
    labelMedium = style(ChronoTokens.TypeScale.S14),
    labelSmall = style(ChronoTokens.TypeScale.S14),
)

@Composable
fun ChronoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = ChronoTypography, content = content)
}
```

- [ ] **Step 5: 통과 확인과 시안 이미지 기록**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest --offline && ./gradlew :app:recordRoborazziDebug --offline && ls -la app/screenshots/`
Expected: `BUILD SUCCESSFUL`가 두 번 나오고 `tier_palettes.png`가 보인다. Robolectric 그래픽 모드를 처음 쓸 때 네이티브 런타임을 받으면 `--offline`이 실패할 수 있다. 그러면 온라인으로 한 번 실행한다.

- [ ] **Step 6: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add design-tokens.json tools/gen-tokens android-core/app docs/superpowers/specs/2026-11-25-chrono-quest-design.md
git commit -m "feat(app): 디자인 토큰 생성기와 테마, 티어 색 시안 이미지" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 사용자가 고른 티어 색 등록

**이 태스크는 컨트롤러가 먼저 `android-core/app/screenshots/tier_palettes.png`를 사용자에게 보여주고 A/B 중 하나를 고르게 한 뒤에 디스패치한다.** 구현자는 디스패치 메시지에 적힌 선택(A 또는 B)을 따른다.

**Files:**
- Modify: `design-tokens.json` (`tier` 채우기)
- Regenerate: `android-core/app/src/main/kotlin/com/chronoquest/app/ui/theme/Tokens.kt`
- Modify: `android-core/app/src/main/kotlin/com/chronoquest/app/ui/theme/Theme.kt` (`tierColor` 추가)
- Test: `android-core/app/src/test/kotlin/com/chronoquest/app/TierColorTest.kt`

**Interfaces:**
- Produces: `fun tierColor(tier: com.chronoquest.rules.model.Tier): Color`, `fun onTierColor(tier: Tier): Color` (배지 글자색)

- [ ] **Step 1: 실패 테스트 작성**

`android-core/app/src/test/kotlin/com/chronoquest/app/TierColorTest.kt`:

```kotlin
package com.chronoquest.app

import com.chronoquest.app.ui.theme.ChronoTokens
import com.chronoquest.app.ui.theme.onTierColor
import com.chronoquest.app.ui.theme.tierColor
import com.chronoquest.rules.model.Tier
import kotlin.test.assertEquals
import org.junit.Test

class TierColorTest {
    @Test
    fun `모든 티어에 색이 있다`() {
        assertEquals(Tier.entries.map { it.name }.toSet(), ChronoTokens.Tier.all.keys)
        Tier.entries.forEach { assertEquals(ChronoTokens.Tier.all.getValue(it.name), tierColor(it)) }
    }

    @Test
    fun `골드 배지 글자는 어두운 색이고 나머지는 흰색이다`() {
        assertEquals(ChronoTokens.Colors.Text1, onTierColor(Tier.GOLD))
        assertEquals(ChronoTokens.Colors.Bg1, onTierColor(Tier.BRONZE))
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'tierColor'`

- [ ] **Step 3: 고른 시안을 원본에 등록하고 다시 생성**

`design-tokens.json`의 `"tier": {}`를 고른 시안으로 바꾼다.

시안 A:
```json
  "tier": {
    "BRONZE": "#B8733A",
    "SILVER": "#8E95A3",
    "GOLD": "#E8B530",
    "PLATINUM": "#2FA89C",
    "DIAMOND": "#3C8DDE",
    "MASTER": "#D64550",
    "LEGEND": "#F07A1A",
    "MYTHIC": "#1F2A5A"
  },
```

시안 B:
```json
  "tier": {
    "BRONZE": "#B8733A",
    "SILVER": "#8E95A3",
    "GOLD": "#E8B530",
    "PLATINUM": "#2FA89C",
    "DIAMOND": "#3C8DDE",
    "MASTER": "#8A4FD8",
    "LEGEND": "#D64550",
    "MYTHIC": "#F07A1A"
  },
```

Run: `cd /Users/sang/Desktop/chrono-oath && python3 tools/gen-tokens/gen_tokens.py`

`Theme.kt` 끝에 추가한다(import에 `com.chronoquest.rules.model.Tier`, `androidx.compose.ui.graphics.Color` 추가).

```kotlin
/** 티어 엠블럼·배지 색. 토큰에만 있는 값을 쓴다. */
fun tierColor(tier: Tier): Color = ChronoTokens.Tier.all.getValue(tier.name)

/** 티어 배지 위 글자색. 밝은 골드만 어두운 글자를 쓴다. */
fun onTierColor(tier: Tier): Color =
    if (tier == Tier.GOLD) ChronoTokens.Colors.Text1 else ChronoTokens.Colors.Bg1
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest --offline`
Expected: `BUILD SUCCESSFUL` (TokensTest의 티어 검사와 TierColorTest가 통과)

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add design-tokens.json android-core/app
git commit -m "feat(app): 사용자가 고른 티어 8색을 디자인 토큰에 등록" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 홈 화면 상태와 동작 (HomePresenter)

**Files:**
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/notify/SettlementNotifier.kt` (인터페이스만; 안드로이드 구현은 Task 6)
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/home/HomeUiState.kt`
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/home/HomePresenter.kt`
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/home/HomeViewModel.kt`
- Modify: `android-core/app/src/main/kotlin/com/chronoquest/app/AppContainer.kt` (`notifier` 추가)
- Test: `android-core/app/src/test/kotlin/com/chronoquest/app/home/HomePresenterTest.kt`

**Interfaces:**
- Consumes: `GameService`(`today`, `state`, `needsCleanup`, `representativeTitle`, `unseenCelebrations`, `markSeen`, `depart`, `arrive`, `editRecord`, `resolveNoRecord`, `cancelQuest`, `answerSelfControl`), `ScheduleService`(`generateQuests`, `reschedule`), `ActionResult`, `ScheduleResult`, `RejectReason`, `ScheduleProblem`, `TodayQuest`, `Celebration`
- Produces:
  - `fun interface SettlementNotifier { fun notify(xpGained: Int, celebrations: List<Celebration>) }` + `SettlementNotifier.None`
  - `HomeUiState`, `QuestCard`, `CardStatus`, `CleanupCard`, `HomeDialog`(`AskSafety`, `ConfirmEarly`, `Reschedule`, `AskSelfControl`, `AddArrival`), `PendingAction`(`Depart`, `Arrive`)
  - `class HomePresenter(game, schedule, clock, notifier, compute: CoroutineDispatcher = Dispatchers.Default)` with `state: StateFlow<HomeUiState>` and the suspend/plain methods below
  - `class HomeViewModel(presenter): ViewModel` + `HomeViewModel.factory(container)`

- [ ] **Step 1: 실패 테스트 작성**

`android-core/app/src/test/kotlin/com/chronoquest/app/home/HomePresenterTest.kt`:

```kotlin
package com.chronoquest.app.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.MutableClock
import com.chronoquest.app.d
import com.chronoquest.app.dt
import com.chronoquest.app.inMemoryDb
import com.chronoquest.app.notify.SettlementNotifier
import com.chronoquest.app.questEntity
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.domain.Celebration
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.Tier
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePresenterTest {
    private lateinit var db: ChronoDatabase
    private lateinit var presenter: HomePresenter
    private val clock = MutableClock(dt("2026-11-30", "08:00"))
    private val notified = mutableListOf<Pair<Int, List<Celebration>>>()
    private val work = questEntity("2026-11-30")
    private val other = questEntity("2026-11-30", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        db.questDao().insertAll(listOf(other, work))
        val notifier = SettlementNotifier { xp, celebrations -> notified += xp to celebrations }
        presenter = HomePresenter(GameService(db, clock), ScheduleService(db, clock), clock, notifier, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val state get() = presenter.state.value

    private fun card(id: String): QuestCard = state.quests.single { it.id == id }

    @Test
    fun `새로고침하면 오늘 퀘스트와 티어와 대표 칭호를 보여준다`() = runTest {
        presenter.refresh()
        assertFalse(state.loading)
        assertEquals(listOf(work.id, other.id), state.quests.map { it.id })
        assertTrue(state.quests.all { it.status == CardStatus.WAITING && it.canDepart && it.canArrive })
        assertEquals("08:30", card(work.id).departLabel)
        assertEquals("09:00", card(work.id).dueLabel)
        assertEquals(Tier.BRONZE, state.tier)
        assertEquals("미각성자", state.representativeTitle)
        assertEquals(0, state.xp)
        assertEquals(100, state.nextTierXp)
        assertTrue(state.celebrations.isEmpty())
    }

    @Test
    fun `출발하면 행동통제를 묻고 정산 알림을 보낸다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        assertEquals(HomeDialog.AskSelfControl(d("2026-11-30")), state.dialog)
        assertEquals(CardStatus.DEPARTED, card(work.id).status)
        assertFalse(card(work.id).canDepart)
        assertTrue(card(work.id).canArrive)
        assertEquals(10, notified.single().first)
        assertTrue(state.celebrations.any { it.key == "title:41:1" })
    }

    @Test
    fun `행동통제에 답하면 창이 닫히고 XP가 오른다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        presenter.onSelfControlAnswered(d("2026-11-30"), true)
        assertNull(state.dialog)
        assertEquals(15, state.xp)
    }

    @Test
    fun `도착은 안전 여부를 먼저 묻는다`() = runTest {
        presenter.refresh()
        presenter.onArrive(work.id)
        assertEquals(HomeDialog.AskSafety(work.id), state.dialog)
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(work.id, true)
        assertNull(state.dialog)
        assertEquals(CardStatus.ON_TIME, card(work.id).status)
        assertFalse(card(work.id).canArrive)
        assertEquals(20, state.xp)
        assertEquals("운명의 첫걸음", state.representativeTitle)
    }

    @Test
    fun `30분 이상 이른 도착은 확인하고 그대로 기록할 수 있다`() = runTest {
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(other.id, true)
        val dialog = state.dialog as HomeDialog.ConfirmEarly
        assertEquals(other.id, dialog.questId)
        assertEquals(605L, dialog.minutesEarly)
        assertEquals(PendingAction.Arrive(true), dialog.action)
        presenter.onRecordAnyway(dialog)
        assertNull(state.dialog)
        assertEquals(CardStatus.EARLY, card(other.id).status)
    }

    @Test
    fun `일정을 고치면 새 시각으로 기록한다`() = runTest {
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(other.id, true)
        presenter.onEditScheduleRequested(state.dialog as HomeDialog.ConfirmEarly)
        val reschedule = state.dialog as HomeDialog.Reschedule
        assertEquals(LocalTime.parse("19:00"), reschedule.due)
        assertEquals(LocalTime.parse("18:30"), reschedule.depart)
        presenter.onRescheduleConfirmed(reschedule, LocalTime.parse("09:10"), LocalTime.parse("08:40"))
        assertNull(state.dialog)
        assertEquals(CardStatus.EARLY, card("2026-11-30-OTHER-09:10").status)
        assertTrue(state.quests.none { it.id == other.id })
    }

    @Test
    fun `정리 필요 일정을 지각 처리한다`() = runTest {
        clock.now = dt("2026-11-30", "12:00")
        presenter.refresh()
        assertEquals(listOf(work.id), state.cleanup.map { it.id })
        assertEquals("11/30 (월) 09:00", state.cleanup.single().whenLabel)
        presenter.onCleanupLate(work.id)
        assertTrue(state.cleanup.isEmpty())
        assertEquals(CardStatus.LATE, card(work.id).status)
    }

    @Test
    fun `정리 필요 일정에 도착 시각을 넣는다`() = runTest {
        clock.now = dt("2026-11-30", "12:00")
        presenter.refresh()
        presenter.onCleanupAddArrival(work.id)
        val dialog = state.dialog as HomeDialog.AddArrival
        presenter.onArrivalEntered(dialog, LocalTime.parse("08:58"), true)
        assertTrue(state.cleanup.isEmpty())
        assertEquals(CardStatus.ON_TIME, card(work.id).status)
    }

    @Test
    fun `정리 필요 일정을 취소한다`() = runTest {
        clock.now = dt("2026-11-30", "12:00")
        presenter.refresh()
        presenter.onCleanupCancel(work.id)
        assertTrue(state.cleanup.isEmpty())
        assertEquals(CardStatus.CANCELED, card(work.id).status)
    }

    @Test
    fun `거부되면 이유를 알려준다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        presenter.dismissDialog()
        presenter.onDepart(work.id)
        assertEquals("이미 기록했어.", state.message)
        presenter.dismissMessage()
        assertNull(state.message)
    }

    @Test
    fun `연출을 확인하면 다시 나오지 않는다`() = runTest {
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(work.id, true)
        assertEquals(listOf("title:1:1", "title:21:1"), state.celebrations.map { it.key })
        presenter.onCelebrationsSeen()
        assertTrue(state.celebrations.isEmpty())
        presenter.refresh()
        assertTrue(state.celebrations.isEmpty())
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'HomePresenter'` 등

- [ ] **Step 3: 구현**

`android-core/app/src/main/kotlin/com/chronoquest/app/notify/SettlementNotifier.kt`:

```kotlin
package com.chronoquest.app.notify

import com.chronoquest.core.domain.Celebration

/** 기록을 정산한 결과를 알린다(스펙 6.3 "+30 XP · 새 칭호: 최초의 각성"). */
fun interface SettlementNotifier {
    fun notify(xpGained: Int, celebrations: List<Celebration>)

    companion object {
        val None = SettlementNotifier { _, _ -> }
    }
}
```

`android-core/app/src/main/kotlin/com/chronoquest/app/home/HomeUiState.kt`:

```kotlin
package com.chronoquest.app.home

import com.chronoquest.core.domain.Celebration
import com.chronoquest.rules.model.Tier
import java.time.LocalDate
import java.time.LocalTime

enum class CardStatus { WAITING, DEPARTED, EARLY, ON_TIME, CLOSE, LATE, CANCELED }

data class QuestCard(
    val id: String,
    val title: String,
    val departLabel: String,
    val dueLabel: String,
    val status: CardStatus,
    val canDepart: Boolean,
    val canArrive: Boolean,
)

data class CleanupCard(
    val id: String,
    val title: String,
    val whenLabel: String,
    val date: LocalDate,
)

sealed interface PendingAction {
    data object Depart : PendingAction
    data class Arrive(val safe: Boolean) : PendingAction
}

sealed interface HomeDialog {
    data class AskSafety(val questId: String) : HomeDialog
    data class ConfirmEarly(val questId: String, val action: PendingAction, val minutesEarly: Long) : HomeDialog
    data class Reschedule(val questId: String, val action: PendingAction, val due: LocalTime, val depart: LocalTime) : HomeDialog
    data class AskSelfControl(val date: LocalDate) : HomeDialog
    data class AddArrival(val questId: String, val date: LocalDate) : HomeDialog
}

data class HomeUiState(
    val loading: Boolean = true,
    val tier: Tier = Tier.BRONZE,
    val representativeTitle: String = "미각성자",
    val xp: Int = 0,
    val nextTierXp: Int? = Tier.SILVER.minXp,
    val quests: List<QuestCard> = emptyList(),
    val cleanup: List<CleanupCard> = emptyList(),
    val celebrations: List<Celebration> = emptyList(),
    val dialog: HomeDialog? = null,
    val message: String? = null,
)
```

`android-core/app/src/main/kotlin/com/chronoquest/app/home/HomePresenter.kt`:

```kotlin
package com.chronoquest.app.home

import com.chronoquest.app.notify.SettlementNotifier
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.domain.RejectReason
import com.chronoquest.core.domain.ScheduleProblem
import com.chronoquest.core.domain.TodayQuest
import com.chronoquest.core.service.ActionResult
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleResult
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.QuestStatus
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * 홈 화면의 상태와 동작. 쓰기는 전부 GameService·ScheduleService를 거친다.
 * 엔진 계산이 있으므로 [compute] 디스패처(기본 Default)에서 돈다(인계 메모 A2b "스레드").
 */
class HomePresenter(
    private val game: GameService,
    private val schedule: ScheduleService,
    private val clock: Clock,
    private val notifier: SettlementNotifier,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 일정 생성 → 오늘 요약·정리 필요·대표 칭호·안 본 연출을 다시 읽는다. 앱 복귀와 1분 주기로 부른다. */
    suspend fun refresh() = withContext(compute) {
        schedule.generateQuests()
        val today = game.today()
        val gameState = game.state()
        val cleanup = game.needsCleanup()
        val title = game.representativeTitle()
        val celebrations = game.unseenCelebrations()
        _state.update {
            it.copy(
                loading = false,
                tier = gameState.tier,
                xp = gameState.xp,
                nextTierXp = gameState.nextTierXp,
                representativeTitle = title,
                quests = today.quests.map(::toCard),
                cleanup = cleanup.map(::toCleanup),
                celebrations = celebrations,
            )
        }
    }

    suspend fun onDepart(questId: String) = withContext(compute) {
        handle(questId, PendingAction.Depart) { game.depart(questId) }
    }

    /** 도착은 먼저 안전 여부를 묻는다(스펙 6.2). */
    fun onArrive(questId: String) {
        _state.update { it.copy(dialog = HomeDialog.AskSafety(questId)) }
    }

    suspend fun onSafetyAnswered(questId: String, safe: Boolean) = withContext(compute) {
        handle(questId, PendingAction.Arrive(safe)) { game.arrive(questId, safe) }
    }

    suspend fun onRecordAnyway(dialog: HomeDialog.ConfirmEarly) = withContext(compute) {
        handle(dialog.questId, dialog.action) { perform(dialog.questId, dialog.action, allowEarly = true) }
    }

    suspend fun onEditScheduleRequested(dialog: HomeDialog.ConfirmEarly) = withContext(compute) {
        val quest = game.today().quests.firstOrNull { it.quest.id == dialog.questId }?.quest
        if (quest == null) {
            _state.update { it.copy(dialog = null, message = NOT_FOUND_TEXT) }
        } else {
            _state.update {
                it.copy(
                    dialog = HomeDialog.Reschedule(
                        questId = quest.id,
                        action = dialog.action,
                        due = quest.dueAt.toLocalTime(),
                        depart = quest.targetDepartAt.toLocalTime(),
                    ),
                )
            }
        }
    }

    suspend fun onRescheduleConfirmed(dialog: HomeDialog.Reschedule, due: LocalTime, depart: LocalTime) =
        withContext(compute) {
            when (val result = schedule.reschedule(dialog.questId, due, depart)) {
                is ScheduleResult.Saved ->
                    handle(result.id, dialog.action) { perform(result.id, dialog.action, allowEarly = false) }
                is ScheduleResult.Rejected ->
                    _state.update { it.copy(dialog = null, message = problemText(result.problems)) }
            }
        }

    suspend fun onSelfControlAnswered(date: LocalDate, value: Boolean) = withContext(compute) {
        applyResult(game.answerSelfControl(date, value))
    }

    suspend fun onCleanupLate(questId: String) = withContext(compute) {
        applyResult(game.resolveNoRecord(questId))
    }

    suspend fun onCleanupCancel(questId: String) = withContext(compute) {
        applyResult(game.cancelQuest(questId))
    }

    fun onCleanupAddArrival(questId: String) {
        val card = _state.value.cleanup.firstOrNull { it.id == questId } ?: return
        _state.update { it.copy(dialog = HomeDialog.AddArrival(questId, card.date)) }
    }

    suspend fun onArrivalEntered(dialog: HomeDialog.AddArrival, time: LocalTime, safe: Boolean) =
        withContext(compute) {
            applyResult(game.editRecord(dialog.questId, null, dialog.date.atTime(time), safe))
        }

    suspend fun onCelebrationsSeen() = withContext(compute) {
        game.markSeen(_state.value.celebrations.map { it.key })
        refresh()
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun perform(questId: String, action: PendingAction, allowEarly: Boolean): ActionResult =
        when (action) {
            PendingAction.Depart -> game.depart(questId, allowEarly = allowEarly)
            is PendingAction.Arrive -> game.arrive(questId, action.safe, allowEarly = allowEarly)
        }

    /** 30분 이상 이르면 확인 창을 띄우고, 아니면 결과를 반영한다. */
    private suspend fun handle(questId: String, action: PendingAction, block: suspend () -> ActionResult) {
        when (val result = block()) {
            is ActionResult.ConfirmEarly -> _state.update {
                it.copy(dialog = HomeDialog.ConfirmEarly(questId, action, result.minutesEarly))
            }
            else -> applyResult(result)
        }
    }

    private suspend fun applyResult(result: ActionResult) {
        when (result) {
            is ActionResult.Done -> {
                if (result.xpGained != 0 || result.newCelebrations.isNotEmpty()) {
                    notifier.notify(result.xpGained, result.newCelebrations)
                }
                val next = if (result.askSelfControl) HomeDialog.AskSelfControl(LocalDate.now(clock)) else null
                _state.update { it.copy(dialog = next) }
            }
            is ActionResult.Rejected -> _state.update { it.copy(dialog = null, message = rejectText(result.reason)) }
            is ActionResult.ConfirmEarly -> _state.update { it.copy(dialog = null) }
            ActionResult.NotFound -> _state.update { it.copy(dialog = null, message = NOT_FOUND_TEXT) }
        }
        refresh()
    }

    private fun toCard(item: TodayQuest): QuestCard {
        val status = when {
            item.quest.status == QuestStatus.CANCELED -> CardStatus.CANCELED
            item.outcome?.verdict == ArrivalVerdict.EARLY -> CardStatus.EARLY
            item.outcome?.verdict == ArrivalVerdict.ON_TIME -> CardStatus.ON_TIME
            item.outcome?.verdict == ArrivalVerdict.CLOSE -> CardStatus.CLOSE
            item.outcome?.verdict == ArrivalVerdict.LATE -> CardStatus.LATE
            item.record?.departedAt != null -> CardStatus.DEPARTED
            else -> CardStatus.WAITING
        }
        return QuestCard(
            id = item.quest.id,
            title = item.quest.title,
            departLabel = item.quest.targetDepartAt.format(TIME),
            dueLabel = item.quest.dueAt.format(TIME),
            status = status,
            canDepart = status == CardStatus.WAITING,
            canArrive = status == CardStatus.WAITING || status == CardStatus.DEPARTED,
        )
    }

    private fun toCleanup(quest: QuestEntity) = CleanupCard(
        id = quest.id,
        title = quest.title,
        whenLabel = quest.dueAt.format(CLEANUP_WHEN),
        date = quest.date,
    )

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val CLEANUP_WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E) HH:mm", Locale.KOREAN)
        const val NOT_FOUND_TEXT = "일정을 찾을 수 없어."

        fun rejectText(reason: RejectReason): String = when (reason) {
            RejectReason.QUEST_CANCELED -> "취소한 일정이야."
            RejectReason.FUTURE_TIME -> "아직 오지 않은 시각은 기록할 수 없어."
            RejectReason.ALREADY_RECORDED -> "이미 기록했어."
            RejectReason.ARRIVAL_BEFORE_DEPARTURE -> "도착이 출발보다 빠를 수는 없어."
            RejectReason.SAFE_WITHOUT_ARRIVAL -> "도착 시각 없이 안전 여부만 넣을 수는 없어."
            RejectReason.SAFE_REQUIRED -> "안전하게 왔는지 알려줘."
            RejectReason.WRONG_DAY -> "그 일정 날짜에만 기록할 수 있어."
            RejectReason.NOT_DUE_YET -> "아직 예정 시각이 안 됐어."
        }

        fun problemText(problems: List<ScheduleProblem>): String = when (problems.firstOrNull()) {
            ScheduleProblem.DEPART_AFTER_DUE -> "출발 시각이 도착 시각보다 늦어."
            ScheduleProblem.DUPLICATE -> "같은 시각에 같은 종류 일정이 이미 있어."
            ScheduleProblem.HAS_RECORD -> "이미 기록이 있는 일정은 시각을 바꿀 수 없어."
            ScheduleProblem.NOT_FOUND -> NOT_FOUND_TEXT
            ScheduleProblem.BLANK_TITLE -> "일정 이름이 비어 있어."
            ScheduleProblem.BEFORE_SEASON -> "시즌 시작 전 날짜야."
            ScheduleProblem.NO_DAYS -> "요일을 하나 이상 골라줘."
            null -> "일정을 바꾸지 못했어."
        }
    }
}
```

`android-core/app/src/main/kotlin/com/chronoquest/app/home/HomeViewModel.kt`:

```kotlin
package com.chronoquest.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronoquest.app.AppContainer
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** presenter를 viewModelScope에 연결만 한다. 로직은 HomePresenter에 있다. */
class HomeViewModel(private val presenter: HomePresenter) : ViewModel() {
    val state: StateFlow<HomeUiState> = presenter.state

    fun refresh() = launch { presenter.refresh() }
    fun onDepart(questId: String) = launch { presenter.onDepart(questId) }
    fun onArrive(questId: String) = presenter.onArrive(questId)
    fun onSafetyAnswered(questId: String, safe: Boolean) = launch { presenter.onSafetyAnswered(questId, safe) }
    fun onRecordAnyway(dialog: HomeDialog.ConfirmEarly) = launch { presenter.onRecordAnyway(dialog) }
    fun onEditSchedule(dialog: HomeDialog.ConfirmEarly) = launch { presenter.onEditScheduleRequested(dialog) }
    fun onRescheduleConfirmed(dialog: HomeDialog.Reschedule, due: LocalTime, depart: LocalTime) =
        launch { presenter.onRescheduleConfirmed(dialog, due, depart) }
    fun onSelfControlAnswered(date: LocalDate, value: Boolean) = launch { presenter.onSelfControlAnswered(date, value) }
    fun onCleanupLate(questId: String) = launch { presenter.onCleanupLate(questId) }
    fun onCleanupCancel(questId: String) = launch { presenter.onCleanupCancel(questId) }
    fun onCleanupAddArrival(questId: String) = presenter.onCleanupAddArrival(questId)
    fun onArrivalEntered(dialog: HomeDialog.AddArrival, time: LocalTime, safe: Boolean) =
        launch { presenter.onArrivalEntered(dialog, time, safe) }
    fun onCelebrationsSeen() = launch { presenter.onCelebrationsSeen() }
    fun dismissDialog() = presenter.dismissDialog()
    fun dismissMessage() = presenter.dismissMessage()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    HomePresenter(container.gameService, container.scheduleService, container.clock, container.notifier),
                )
            }
        }
    }
}
```

`AppContainer.kt`에 import `com.chronoquest.app.notify.SettlementNotifier`를 추가하고, `scheduleService` 줄 아래에 추가한다(Task 6에서 안드로이드 구현으로 바꾼다).

```kotlin
    val notifier: SettlementNotifier by lazy { SettlementNotifier.None }
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest --offline`
Expected: `BUILD SUCCESSFUL` (HomePresenterTest 11개 포함)

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/app
git commit -m "feat(app): 홈 화면 상태와 기록 동작 (HomePresenter)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 홈 화면 UI, 확인 창, 하단 탭, 화면 사진

**Files:**
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/home/HomeScreen.kt`
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/home/HomeDialogs.kt`
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/ui/MainScaffold.kt`
- Modify: `android-core/app/src/main/kotlin/com/chronoquest/app/MainActivity.kt`
- Test: `android-core/app/src/test/kotlin/com/chronoquest/app/home/HomeScreenshotTest.kt`
- Generated (commit): `android-core/app/screenshots/home_*.png` (5장)

**Interfaces:**
- Consumes: Task 4 `HomeUiState` 등, `HomeViewModel`; Task 2·3 `ChronoTheme`, `ChronoTokens`, `tierColor`, `onTierColor`
- Produces: `data class HomeActions(...)`, `@Composable fun HomeScreen(state, actions, modifier)`, `@Composable fun HomeDialogHost(dialog, actions)`, `enum class MainTab`, `@Composable fun ChronoScaffold(selectedTab, onTabSelected, content)`, `@Composable fun ChronoMainScreen(viewModel: HomeViewModel)`

- [ ] **Step 1: 실패 테스트 작성**

`android-core/app/src/test/kotlin/com/chronoquest/app/home/HomeScreenshotTest.kt`:

```kotlin
package com.chronoquest.app.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.FLIP5
import com.chronoquest.app.d
import com.chronoquest.app.ui.ChronoScaffold
import com.chronoquest.app.ui.MainTab
import com.chronoquest.app.ui.theme.ChronoTheme
import com.chronoquest.core.domain.Celebration
import com.chronoquest.core.domain.CelebrationKind
import com.chronoquest.rules.model.Tier
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object Samples {
    val firstDay = HomeUiState(
        loading = false,
        tier = Tier.BRONZE,
        representativeTitle = "미각성자",
        xp = 0,
        nextTierXp = 100,
        quests = listOf(
            QuestCard("w", "출근", "08:30", "09:00", CardStatus.WAITING, canDepart = true, canArrive = true),
            QuestCard("o", "보드게임", "13:20", "14:00", CardStatus.WAITING, canDepart = true, canArrive = true),
        ),
    )

    val inProgress = firstDay.copy(
        representativeTitle = "운명의 첫걸음",
        xp = 50,
        quests = listOf(
            QuestCard("w", "출근", "08:30", "09:00", CardStatus.ON_TIME, canDepart = false, canArrive = false),
            QuestCard("o", "보드게임", "13:20", "14:00", CardStatus.DEPARTED, canDepart = false, canArrive = true),
        ),
    )

    val cleanupAndCelebration = inProgress.copy(
        tier = Tier.SILVER,
        xp = 120,
        nextTierXp = 300,
        cleanup = listOf(CleanupCard("c", "출근", "11/27 (금) 09:00", d("2026-11-27"))),
        celebrations = listOf(
            Celebration("title:1:1", CelebrationKind.TITLE, titleId = 1, detail = "최초의 각성"),
            Celebration("tier:SILVER", CelebrationKind.TIER_UP, detail = "시간의 수련자"),
        ),
    )

    val confirmEarly = inProgress.copy(dialog = HomeDialog.ConfirmEarly("o", PendingAction.Arrive(true), 45))

    val reschedule = inProgress.copy(
        dialog = HomeDialog.Reschedule("o", PendingAction.Arrive(true), LocalTime.of(14, 0), LocalTime.of(13, 20)),
    )
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = FLIP5)
class HomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: HomeUiState) {
        compose.setContent {
            ChronoTheme {
                ChronoScaffold(selectedTab = MainTab.HOME, onTabSelected = {}) {
                    HomeScreen(state = state, actions = HomeActions())
                }
            }
        }
        captureScreenRoboImage("screenshots/$name.png")
    }

    @Test
    fun `첫날`() = shoot("home_first_day", Samples.firstDay)

    @Test
    fun `진행 중`() = shoot("home_in_progress", Samples.inProgress)

    @Test
    fun `정리 필요와 새 칭호`() = shoot("home_cleanup_celebration", Samples.cleanupAndCelebration)

    @Test
    fun `30분 이상 이른 기록 확인 창`() = shoot("home_confirm_early", Samples.confirmEarly)

    @Test
    fun `일정 시각 변경 창`() = shoot("home_reschedule", Samples.reschedule)
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'HomeScreen'` / `'ChronoScaffold'` / `'HomeActions'`

- [ ] **Step 3: 화면 구현**

`android-core/app/src/main/kotlin/com/chronoquest/app/home/HomeScreen.kt`:

```kotlin
package com.chronoquest.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chronoquest.app.ui.theme.ChronoTokens
import com.chronoquest.app.ui.theme.onTierColor
import com.chronoquest.app.ui.theme.tierColor
import com.chronoquest.core.domain.Celebration
import com.chronoquest.core.domain.CelebrationKind
import java.time.LocalDate
import java.time.LocalTime

/** 홈 화면 부품 고유 크기. 간격·모서리는 ChronoTokens.Space·Radius를 쓴다. */
private val EMBLEM_SIZE = 56.dp
private val XP_BAR_HEIGHT = 8.dp
private val BORDER_WIDTH = 1.dp

/** 홈 화면이 위로 올리는 동작들. 기본값은 아무것도 안 해서 화면 사진·미리보기에 그대로 쓴다. */
data class HomeActions(
    val onDepart: (String) -> Unit = {},
    val onArrive: (String) -> Unit = {},
    val onSafetyAnswered: (String, Boolean) -> Unit = { _, _ -> },
    val onRecordAnyway: (HomeDialog.ConfirmEarly) -> Unit = {},
    val onEditSchedule: (HomeDialog.ConfirmEarly) -> Unit = {},
    val onRescheduleConfirmed: (HomeDialog.Reschedule, LocalTime, LocalTime) -> Unit = { _, _, _ -> },
    val onSelfControlAnswered: (LocalDate, Boolean) -> Unit = { _, _ -> },
    val onCleanupLate: (String) -> Unit = {},
    val onCleanupCancel: (String) -> Unit = {},
    val onCleanupAddArrival: (String) -> Unit = {},
    val onArrivalEntered: (HomeDialog.AddArrival, LocalTime, Boolean) -> Unit = { _, _, _ -> },
    val onDismissDialog: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onCelebrationsSeen: () -> Unit = {},
)

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(ChronoTokens.Colors.Bg1),
        contentPadding = PaddingValues(horizontal = ChronoTokens.Space.Lg, vertical = ChronoTokens.Space.Xl),
        verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Md),
    ) {
        item { Header(state) }
        if (state.celebrations.isNotEmpty()) {
            item { CelebrationCard(state.celebrations, actions.onCelebrationsSeen) }
        }
        if (state.cleanup.isNotEmpty()) {
            item { SectionTitle("정리 필요") }
            items(state.cleanup, key = { "cleanup-${it.id}" }) { CleanupRow(it, actions) }
        }
        item { SectionTitle("오늘의 퀘스트") }
        if (state.quests.isEmpty()) {
            item {
                Text(
                    "오늘은 일정이 없어.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChronoTokens.Colors.Text2,
                )
            }
        } else {
            items(state.quests, key = { "quest-${it.id}" }) { QuestRow(it, actions) }
        }
    }
    HomeDialogHost(state.dialog, actions)
    state.message?.let { MessageDialog(it, actions.onDismissMessage) }
}

@Composable
private fun Header(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Md)) {
        Text("SEASON 01", style = MaterialTheme.typography.labelLarge, color = ChronoTokens.Colors.Text2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(EMBLEM_SIZE).background(tierColor(state.tier), RoundedCornerShape(ChronoTokens.Radius.Lg)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.tier.name.take(1),
                    style = MaterialTheme.typography.headlineMedium,
                    color = onTierColor(state.tier),
                )
            }
            Spacer(Modifier.width(ChronoTokens.Space.Md))
            Column {
                Text(state.tier.name, style = MaterialTheme.typography.labelLarge, color = ChronoTokens.Colors.Text2)
                Text(state.representativeTitle, style = MaterialTheme.typography.titleLarge, color = ChronoTokens.Colors.Text1)
            }
        }
        val next = state.nextTierXp
        val progress = if (next == null) 1f else (state.xp.toFloat() / next).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(XP_BAR_HEIGHT),
            color = ChronoTokens.Colors.Gold500,
            trackColor = ChronoTokens.Colors.Yellow50,
            drawStopIndicator = {},
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${state.xp} XP", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
            if (next != null) {
                Text("다음 티어 $next XP", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = ChronoTokens.Space.Sm),
        style = MaterialTheme.typography.titleMedium,
        color = ChronoTokens.Colors.Text1,
    )
}

@Composable
private fun QuestRow(card: QuestCard, actions: HomeActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BORDER_WIDTH, ChronoTokens.Colors.Border, RoundedCornerShape(ChronoTokens.Radius.Lg))
            .padding(ChronoTokens.Space.Lg),
        verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
                Text(
                    "출발 ${card.departLabel} · 도착 ${card.dueLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChronoTokens.Colors.Text2,
                )
            }
            StatusChip(card.status)
        }
        if (card.canDepart || card.canArrive) {
            Row(horizontalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm)) {
                if (card.canDepart) Button(onClick = { actions.onDepart(card.id) }) { Text("출발") }
                if (card.canArrive) OutlinedButton(onClick = { actions.onArrive(card.id) }) { Text("도착") }
            }
        }
    }
}

@Composable
private fun StatusChip(status: CardStatus) {
    val (label, background, foreground) = when (status) {
        CardStatus.WAITING -> Triple("대기", ChronoTokens.Colors.Bg2, ChronoTokens.Colors.Text2)
        CardStatus.DEPARTED -> Triple("이동 중", ChronoTokens.Colors.Blue50, ChronoTokens.Colors.Blue700)
        CardStatus.EARLY -> Triple("조기", ChronoTokens.Colors.Blue50, ChronoTokens.Colors.Blue700)
        CardStatus.ON_TIME -> Triple("정시", ChronoTokens.Colors.Blue50, ChronoTokens.Colors.Blue700)
        CardStatus.CLOSE -> Triple("아슬아슬", ChronoTokens.Colors.Yellow50, ChronoTokens.Colors.Gold700)
        CardStatus.LATE -> Triple("지각", ChronoTokens.Colors.Bg2, ChronoTokens.Colors.Text1)
        CardStatus.CANCELED -> Triple("취소", ChronoTokens.Colors.Bg2, ChronoTokens.Colors.Text2)
    }
    Chip(label, background, foreground)
}

@Composable
private fun Chip(label: String, background: Color, foreground: Color) {
    Text(
        label,
        modifier = Modifier.background(background, RoundedCornerShape(ChronoTokens.Radius.Sm)).padding(horizontal = ChronoTokens.Space.Md, vertical = ChronoTokens.Space.Xs),
        style = MaterialTheme.typography.labelLarge,
        color = foreground,
    )
}

@Composable
private fun CleanupRow(card: CleanupCard, actions: HomeActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChronoTokens.Colors.Yellow50, RoundedCornerShape(ChronoTokens.Radius.Lg))
            .padding(ChronoTokens.Space.Lg),
    ) {
        Text("${card.whenLabel} ${card.title}", style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
        Text("도착 기록이 없어. 어떻게 정리할까?", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Gold700)
        Row {
            TextButton(onClick = { actions.onCleanupAddArrival(card.id) }) { Text("기록 추가") }
            TextButton(onClick = { actions.onCleanupLate(card.id) }) { Text("지각 처리") }
            TextButton(onClick = { actions.onCleanupCancel(card.id) }) { Text("취소") }
        }
    }
}

@Composable
private fun CelebrationCard(celebrations: List<Celebration>, onSeen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChronoTokens.Colors.Yellow50, RoundedCornerShape(ChronoTokens.Radius.Lg))
            .padding(ChronoTokens.Space.Lg),
        verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Xs),
    ) {
        Text("새로 얻은 것", style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
        celebrations.forEach {
            Text(celebrationText(it), style = MaterialTheme.typography.bodyLarge, color = ChronoTokens.Colors.Text1)
        }
        TextButton(onClick = onSeen) { Text("확인") }
    }
}

/** 연출 한 줄 문구. */
fun celebrationText(c: Celebration): String = when (c.kind) {
    CelebrationKind.TITLE -> "새 칭호: ${c.detail}"
    CelebrationKind.PERIOD_BONUS -> "기간 보너스 +${c.xp} XP"
    CelebrationKind.PET_EVOLVED -> "펫 진화: ${c.detail}"
    CelebrationKind.BOSS_RESULT -> "주간 보스 결과: ${c.detail}"
    CelebrationKind.TIER_UP -> "티어 승급: ${c.key.removePrefix("tier:")}"
}
```

`android-core/app/src/main/kotlin/com/chronoquest/app/home/HomeDialogs.kt`:

```kotlin
package com.chronoquest.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import com.chronoquest.app.ui.theme.ChronoTokens
import java.time.LocalTime

@Composable
fun HomeDialogHost(dialog: HomeDialog?, actions: HomeActions) {
    when (dialog) {
        null -> Unit
        is HomeDialog.AskSafety -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text("안전하게 왔어?") },
            text = { Text("신호를 지키고 뛰지 않고 왔으면 안전이야.") },
            confirmButton = { TextButton(onClick = { actions.onSafetyAnswered(dialog.questId, true) }) { Text("안전하게 옴") } },
            dismissButton = { TextButton(onClick = { actions.onSafetyAnswered(dialog.questId, false) }) { Text("위험했어") } },
        )
        is HomeDialog.ConfirmEarly -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text("예정보다 ${dialog.minutesEarly}분 일러") },
            text = { Text("일정 시각이 바뀌었으면 일정을 고치고 기록해. 아니면 그대로 기록할 수 있어.") },
            confirmButton = { TextButton(onClick = { actions.onEditSchedule(dialog) }) { Text("일정 수정") } },
            dismissButton = { TextButton(onClick = { actions.onRecordAnyway(dialog) }) { Text("그대로 기록") } },
        )
        is HomeDialog.Reschedule -> RescheduleDialog(dialog, actions)
        is HomeDialog.AskSelfControl -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text("행동통제") },
            text = { Text("출발 전에 필수 준비 말고 딴짓 안 했지?") },
            confirmButton = { TextButton(onClick = { actions.onSelfControlAnswered(dialog.date, true) }) { Text("응") } },
            dismissButton = { TextButton(onClick = { actions.onSelfControlAnswered(dialog.date, false) }) { Text("했어") } },
        )
        is HomeDialog.AddArrival -> AddArrivalDialog(dialog, actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleDialog(dialog: HomeDialog.Reschedule, actions: HomeActions) {
    val due = rememberTimePickerState(dialog.due.hour, dialog.due.minute, is24Hour = true)
    val depart = rememberTimePickerState(dialog.depart.hour, dialog.depart.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text("일정 시각 바꾸기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Md)) {
                Text("도착 예정", style = MaterialTheme.typography.labelLarge, color = ChronoTokens.Colors.Text2)
                TimeInput(state = due)
                Text("목표 출발", style = MaterialTheme.typography.labelLarge, color = ChronoTokens.Colors.Text2)
                TimeInput(state = depart)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                actions.onRescheduleConfirmed(dialog, LocalTime.of(due.hour, due.minute), LocalTime.of(depart.hour, depart.minute))
            }) { Text("저장하고 기록") }
        },
        dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text("닫기") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddArrivalDialog(dialog: HomeDialog.AddArrival, actions: HomeActions) {
    val time = rememberTimePickerState(9, 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text("도착 시각 넣기") },
        text = { TimeInput(state = time) },
        confirmButton = {
            TextButton(onClick = { actions.onArrivalEntered(dialog, LocalTime.of(time.hour, time.minute), true) }) {
                Text("안전하게 도착")
            }
        },
        dismissButton = {
            TextButton(onClick = { actions.onArrivalEntered(dialog, LocalTime.of(time.hour, time.minute), false) }) {
                Text("위험했어")
            }
        },
    )
}

@Composable
fun MessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
    )
}
```

`android-core/app/src/main/kotlin/com/chronoquest/app/ui/MainScaffold.kt`:

```kotlin
package com.chronoquest.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.chronoquest.app.ui.theme.ChronoTokens

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Filled.Home),
    SCHEDULE("일정", Icons.Filled.DateRange),
    CODEX("칭호", Icons.Filled.Star),
    LOG("기록", Icons.Filled.Edit),
}

@Composable
fun ChronoScaffold(selectedTab: MainTab, onTabSelected: (MainTab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = ChronoTokens.Colors.Bg1,
        bottomBar = {
            NavigationBar(containerColor = ChronoTokens.Colors.Bg1) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

/** A2b-2에서 만들 화면 자리. */
@Composable
fun ComingSoon(tab: MainTab) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("${tab.label} 화면은 다음 단계에서 만들어.", style = MaterialTheme.typography.bodyLarge, color = ChronoTokens.Colors.Text2)
    }
}
```

`android-core/app/src/main/kotlin/com/chronoquest/app/MainActivity.kt` 전체:

```kotlin
package com.chronoquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chronoquest.app.home.HomeActions
import com.chronoquest.app.home.HomeScreen
import com.chronoquest.app.home.HomeViewModel
import com.chronoquest.app.ui.ChronoScaffold
import com.chronoquest.app.ui.ComingSoon
import com.chronoquest.app.ui.MainTab
import com.chronoquest.app.ui.theme.ChronoTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ChronoApp).container
        setContent {
            ChronoTheme {
                val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                ChronoMainScreen(homeViewModel)
            }
        }
    }
}

/** 앱이 보이는 동안 1분마다 새로고침한다(시간이 지나서 생기는 보류·보너스를 반영, 인계 메모 A2b). */
@Composable
fun ChronoMainScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refresh()
                delay(60_000)
            }
        }
    }
    ChronoScaffold(selectedTab = tab, onTabSelected = { tab = it }) {
        when (tab) {
            MainTab.HOME -> HomeScreen(state = state, actions = homeActions(viewModel))
            else -> ComingSoon(tab)
        }
    }
}

private fun homeActions(vm: HomeViewModel) = HomeActions(
    onDepart = vm::onDepart,
    onArrive = vm::onArrive,
    onSafetyAnswered = vm::onSafetyAnswered,
    onRecordAnyway = vm::onRecordAnyway,
    onEditSchedule = vm::onEditSchedule,
    onRescheduleConfirmed = vm::onRescheduleConfirmed,
    onSelfControlAnswered = vm::onSelfControlAnswered,
    onCleanupLate = vm::onCleanupLate,
    onCleanupCancel = vm::onCleanupCancel,
    onCleanupAddArrival = vm::onCleanupAddArrival,
    onArrivalEntered = vm::onArrivalEntered,
    onDismissDialog = vm::dismissDialog,
    onDismissMessage = vm::dismissMessage,
    onCelebrationsSeen = vm::onCelebrationsSeen,
)
```

`HomeViewModel`의 메서드가 `Unit`이 아닌 값을 돌려주면 위 메서드 참조가 맞지 않는다. 그러니 `HomeViewModel`의 `= launch { ... }` 형태 함수 반환형은 `Unit`이어야 한다. `private fun launch(...)`가 `Unit`을 돌려주므로 맞다. `onArrive`, `onCleanupAddArrival`, `dismissDialog`, `dismissMessage`도 presenter의 `Unit` 함수를 그대로 돌려준다.

- [ ] **Step 4: 통과 확인과 화면 사진 기록**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest :app:assembleDebug --offline && ./gradlew :app:recordRoborazziDebug --offline && ls app/screenshots/`
Expected: `BUILD SUCCESSFUL`. `home_first_day.png`, `home_in_progress.png`, `home_cleanup_celebration.png`, `home_confirm_early.png`, `home_reschedule.png`, `tier_palettes.png`가 보인다. 확인 창 두 장에는 창이 화면 위에 떠 있어야 한다. 창이 안 보이면 BLOCKED로 보고한다.

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/app
git commit -m "feat(app): 홈 화면, 확인 창, 하단 탭과 플립5 화면 사진" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

**컨트롤러 확인:** 이 태스크가 끝나면 화면 사진 5장을 사용자에게 보여주고 디자인 의견을 받는다. 수정 요청은 이 계획 밖의 후속 작업으로 처리한다.

---

### Task 6: 정산 알림과 알림 권한

**Files:**
- Create: `android-core/app/src/main/res/drawable/ic_stat_chrono.xml`
- Modify: `android-core/app/src/main/kotlin/com/chronoquest/app/notify/SettlementNotifier.kt` (안드로이드 구현 추가)
- Modify: `android-core/app/src/main/kotlin/com/chronoquest/app/AppContainer.kt` (`notifier` 교체)
- Modify: `android-core/app/src/main/AndroidManifest.xml` (`POST_NOTIFICATIONS`)
- Modify: `android-core/app/src/main/kotlin/com/chronoquest/app/MainActivity.kt` (권한 요청)
- Test: `android-core/app/src/test/kotlin/com/chronoquest/app/notify/SettlementNotifierTest.kt`

**Interfaces:**
- Consumes: Task 4 `SettlementNotifier`, `celebrationText`(Task 5, `com.chronoquest.app.home`)
- Produces: `class AndroidSettlementNotifier(context: Context) : SettlementNotifier`, 채널 ID `settlement`

- [ ] **Step 1: 실패 테스트 작성**

`android-core/app/src/test/kotlin/com/chronoquest/app/notify/SettlementNotifierTest.kt`:

```kotlin
package com.chronoquest.app.notify

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.domain.Celebration
import com.chronoquest.core.domain.CelebrationKind
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SettlementNotifierTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun grant() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `XP와 새 칭호를 한 알림으로 보낸다`() {
        AndroidSettlementNotifier(context).notify(
            30,
            listOf(
                Celebration("title:1:1", CelebrationKind.TITLE, titleId = 1, detail = "최초의 각성"),
                Celebration("title:21:1", CelebrationKind.TITLE, titleId = 21, detail = "운명의 첫걸음"),
            ),
        )
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals("+30 XP", notification.extras.getString(NotificationCompat.EXTRA_TITLE))
        val text = notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString()
        assertTrue("새 칭호: 최초의 각성" in text)
        assertTrue("새 칭호: 운명의 첫걸음" in text)
        assertEquals(AndroidSettlementNotifier.CHANNEL_ID, notification.channelId)
    }

    @Test
    fun `XP 변화도 연출도 없으면 알리지 않는다`() {
        AndroidSettlementNotifier(context).notify(0, emptyList())
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `XP가 줄면 줄었다고 알린다`() {
        AndroidSettlementNotifier(context).notify(-20, emptyList())
        assertEquals("-20 XP", shadowOf(manager).allNotifications.single().extras.getString(NotificationCompat.EXTRA_TITLE))
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'AndroidSettlementNotifier'`

- [ ] **Step 3: 구현**

`android-core/app/src/main/res/drawable/ic_stat_chrono.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2A10,10 0,1 0,22 12,10 10,0 0,0 12,2ZM12,20A8,8 0,1 1,20 12,8 8,0 0,1 12,20ZM12.5,7H11v6l5.2,3.1 0.8,-1.3 -4.5,-2.6Z" />
</vector>
```

`SettlementNotifier.kt` 전체:

```kotlin
package com.chronoquest.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chronoquest.app.R
import com.chronoquest.app.home.celebrationText
import com.chronoquest.core.domain.Celebration

/** 기록을 정산한 결과를 알린다(스펙 6.3 "+30 XP · 새 칭호: 최초의 각성"). */
fun interface SettlementNotifier {
    fun notify(xpGained: Int, celebrations: List<Celebration>)

    companion object {
        val None = SettlementNotifier { _, _ -> }
    }
}

/** 알림 채널 "정산"으로 한 건씩 보낸다. 권한이 없거나 알림이 꺼져 있으면 조용히 넘어간다. */
class AndroidSettlementNotifier(context: Context) : SettlementNotifier {
    private val appContext = context.applicationContext
    private var nextId = 1000

    init {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "정산 알림", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    override fun notify(xpGained: Int, celebrations: List<Celebration>) {
        if (xpGained == 0 && celebrations.isEmpty()) return
        if (!canPost()) return
        val title = if (xpGained >= 0) "+$xpGained XP" else "$xpGained XP"
        val text = if (celebrations.isEmpty()) "기록을 정산했어." else celebrations.joinToString(" · ") { celebrationText(it) }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chrono)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(appContext).notify(nextId++, notification)
    }

    private fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_ID = "settlement"
    }
}
```

`AppContainer.kt`의 `notifier` 줄을 바꾼다(import `com.chronoquest.app.notify.AndroidSettlementNotifier` 추가).

변경 전:
```kotlin
    val notifier: SettlementNotifier by lazy { SettlementNotifier.None }
```
변경 후:
```kotlin
    val notifier: SettlementNotifier by lazy { AndroidSettlementNotifier(appContext) }
```

`AndroidManifest.xml`의 `<application` 위에 추가한다.

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`MainActivity.kt`의 `ChronoMainScreen` 안, `val lifecycleOwner = ...` 바로 위에 알림 권한 요청을 추가한다. import로 `android.Manifest`, `android.content.pm.PackageManager`, `android.os.Build`, `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, `androidx.compose.ui.platform.LocalContext`, `androidx.core.content.ContextCompat`를 추가한다.

```kotlin
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`
Expected: `BUILD SUCCESSFUL` (SettlementNotifierTest 3개 포함, 기존 테스트 그대로)

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/app
git commit -m "feat(app): 정산 알림과 알림 권한 요청" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Pretendard 글꼴 (다운로드 승인 필요)

**이 태스크는 컨트롤러가 사용자에게 다운로드를 승인받은 뒤에만 진행한다.** 받을 파일은 Pretendard v1.3.9 정적 TTF 3종(Regular, Medium, Bold)과 OFL 라이선스 파일이다. 출처는 공식 GitHub(`orioncactus/pretendard`)를 jsDelivr로 받는다. 크기는 실행 시점에 `curl -sI`로 확인해서 사용자에게 알린다. 거절하면 이 태스크는 건너뛰고 기기 기본 글꼴을 유지한다.

**Files:**
- Create: `android-core/app/src/main/res/font/pretendard_regular.ttf`, `pretendard_medium.ttf`, `pretendard_bold.ttf`
- Create: `ASSETS.md` (외부 에셋 출처·라이선스 목록, 스펙 8장)
- Create: `android-core/app/licenses/Pretendard-OFL.txt`
- Modify: `android-core/app/src/main/kotlin/com/chronoquest/app/ui/theme/Theme.kt` (`ChronoFont`)
- Regenerate: `android-core/app/screenshots/*.png`

- [ ] **Step 1: 파일 받기 (컨트롤러, 승인 후)**

```bash
cd /Users/sang/Desktop/chrono-oath
BASE=https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9
mkdir -p android-core/app/src/main/res/font android-core/app/licenses
curl -fSL -o android-core/app/src/main/res/font/pretendard_regular.ttf "$BASE/packages/pretendard/dist/public/static/alternative/Pretendard-Regular.ttf"
curl -fSL -o android-core/app/src/main/res/font/pretendard_medium.ttf "$BASE/packages/pretendard/dist/public/static/alternative/Pretendard-Medium.ttf"
curl -fSL -o android-core/app/src/main/res/font/pretendard_bold.ttf "$BASE/packages/pretendard/dist/public/static/alternative/Pretendard-Bold.ttf"
curl -fSL -o android-core/app/licenses/Pretendard-OFL.txt "$BASE/LICENSE"
file android-core/app/src/main/res/font/*.ttf
```

Expected: 세 파일 모두 `TrueType Font data`. 경로가 없으면(404) 같은 저장소의 `packages/pretendard/dist/public/static/` 아래 TTF 위치를 확인해서 다시 받는다.

- [ ] **Step 2: 글꼴 연결**

`Theme.kt`의 `ChronoFont` 정의를 바꾼다. import로 `androidx.compose.ui.text.font.Font`, `com.chronoquest.app.R`를 추가한다.

변경 전:
```kotlin
/** 앱 글꼴. Pretendard(Task 7)를 넣기 전까지는 기기 기본 글꼴이다. */
val ChronoFont: FontFamily = FontFamily.Default
```
변경 후:
```kotlin
/** 앱 글꼴 Pretendard (OFL, ASSETS.md). */
val ChronoFont: FontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)
```

`ASSETS.md`:

```markdown
# 외부 에셋 목록

| 에셋 | 출처 | 라이선스 | 사용처 |
|---|---|---|---|
| Pretendard v1.3.9 (Regular, Medium, Bold TTF) | https://github.com/orioncactus/pretendard | SIL Open Font License 1.1 (`android-core/app/licenses/Pretendard-OFL.txt`) | 앱 전체 글꼴 (`android-core/app/src/main/res/font/`) |
```

- [ ] **Step 3: 확인과 화면 사진 다시 기록**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :app:testDebugUnitTest :app:assembleDebug --offline && ./gradlew :app:recordRoborazziDebug --offline`
Expected: `BUILD SUCCESSFUL`. 화면 사진의 글꼴이 Pretendard로 바뀐다.

- [ ] **Step 4: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add ASSETS.md android-core/app
git commit -m "feat(app): Pretendard 글꼴 적용 (OFL)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: CI, README, 인계 메모, 디버그 APK

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `android-core/README.md`
- Modify: `docs/superpowers/notes/2026-11-25-a1-carryover.md`

- [ ] **Step 1: CI에 앱 테스트와 빌드 추가**

`.github/workflows/ci.yml`의 마지막 단계를 바꾼다.

변경 전:
```yaml
      - name: Rules and core unit tests
        working-directory: android-core
        run: ./gradlew :rules:test :core:testDebugUnitTest
```
변경 후:
```yaml
      - name: Unit tests and debug build
        working-directory: android-core
        run: ./gradlew :rules:test :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

- [ ] **Step 2: README에 앱 모듈 추가**

`android-core/README.md`에서 다음 세 곳을 고친다.

(1) 모듈 표의 `core` 행 아래에 한 줄을 추가한다.

```markdown
| `app` | 실행용 앱. Compose 홈 화면, 확인 창, 정산 알림. 디자인 토큰은 루트 `design-tokens.json`에서 `tools/gen-tokens/gen_tokens.py`로 생성 |
```

(2) "## 테스트"의 명령 블록을 바꾼다.

````markdown
```bash
cd android-core
./gradlew :rules:test :core:testDebugUnitTest :app:testDebugUnitTest
```
````

(3) 파일 끝에 다음 절을 추가한다.

````markdown
## 화면 사진 (갤럭시 Z 플립5 크기)

```bash
cd android-core
./gradlew :app:recordRoborazziDebug   # app/screenshots/*.png 갱신
```

## 디자인 토큰

`design-tokens.json`을 고친 뒤 저장소 루트에서 `python3 tools/gen-tokens/gen_tokens.py`를 실행한다. `Tokens.kt`는 직접 고치지 않는다.

## 폰에 설치 (USB 디버깅)

```bash
cd android-core
./gradlew :app:assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
````

- [ ] **Step 3: 인계 메모 갱신**

`docs/superpowers/notes/2026-11-25-a1-carryover.md`의 `### A2b가 꼭 반영할 것 (A2a 최종 리뷰, 2026-11-25)` 제목 바로 아래에 추가한다.

```markdown
> **A2b-1에서 처리함 (2026-11-26)**
> - 프로세스당 하나: `AppContainer`
> - 시계: `SystemZoneClock`
> - 쓰기 없이 바뀌는 상태: 앱이 보이는 동안 1분마다, 그리고 복귀할 때 새로고침
> - 스레드: `HomePresenter`가 `Dispatchers.Default`에서 실행
> - 확인 창: 30분 이상 이름, 일정 수정, 안전, 행동통제
> - 음수 XP 알림, 기록 없음 정리 뒤 [출발] 숨김
> - `allowBackup`
>
> **A2b-2로 남은 것**
> - 일정·칭호창(NEW 배지)·설정(삼성 배터리 최적화 예외 안내 포함) 화면
> - 매일 00:05 워커(일정 생성 + 안 본 연출 알림)
> - 연출 "본 것" 시점 확정
> - 취소·휴가 되돌리기 정책
> - 폰 설치(USB 준비되면)
> - 갤럭시 Z 플립5 커버 화면 위젯 아이디어
```

- [ ] **Step 4: 전체 테스트와 APK 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --offline && ls -la app/build/outputs/apk/debug/`
Expected: `BUILD SUCCESSFUL`, `app-debug.apk`가 있다. 테스트 결과는 클래스별로 보고한다. rules 72, core 79, app은 AppContainer 3 + Tokens 4 + TierPaletteScreenshot 1 + TierColor 2 + HomePresenter 11 + HomeScreenshot 5 + SettlementNotifier 3 = 29이다.

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add .github/workflows/ci.yml android-core/README.md docs/superpowers/notes/2026-11-25-a1-carryover.md
git commit -m "ci: 앱 테스트와 디버그 빌드 추가, README와 인계 메모 갱신" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 완료 기준

- `./gradlew :rules:test :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`가 로컬과 GitHub Actions 양쪽에서 통과한다.
- `android-core/app/screenshots/`에 플립5 크기 화면 사진 6장(티어 시안 1 + 홈 5)이 있다. 사용자가 사진으로 디자인을 확인했다.
- 티어 8색이 사용자 선택대로 `design-tokens.json`에 등록돼 있다.
- `app-debug.apk`가 만들어진다. 폰 설치는 USB가 준비되면 README의 명령으로 한다.
- 다음: A2b-2 계획(일정·칭호창·설정 화면, 매일 워커, 폰 설치).
