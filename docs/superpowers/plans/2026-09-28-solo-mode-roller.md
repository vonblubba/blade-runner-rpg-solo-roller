# Blade Runner Solo Mode Table Roller — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a sideloadable Android app that lets the user roll on any of the 22 rollable tables from `Blade Runner - Solo Mode.pdf`, organized by section, with a session roll-history log.

**Architecture:** Kotlin + Jetpack Compose, single-activity, no network/persistence. Table data is a hand-transcribed JSON asset (`app/src/main/assets/tables.json`) loaded once at startup into in-memory data classes. A pure `Roller` object resolves random or fixed die values against a table's row ranges. UI is two Compose screens (section list, table roll) with in-memory session history.

**Tech Stack:** Kotlin 2.4.20, Jetpack Compose (BOM 2026.09.00), AGP 9.4.1, Gradle 9.8.0, kotlinx-serialization-json 1.11.0, JUnit4 for local unit tests. `minSdk 26`, `compileSdk`/`targetSdk 36`, `buildToolsVersion 36.0.0`.

## Global Constraints

- Personal use only — no Play Store packaging, no app signing beyond the debug key, minimal/no launcher art.
- No network permissions, no runtime permissions, no persistence beyond the running process (roll history resets on app restart).
- Package/namespace/applicationId: `com.oscarriva.solomoderoller` (used consistently in every file below — do not vary it).
- Table content must cover exactly the 22 tables cataloged in `docs/superpowers/specs/2026-09-28-solo-mode-roller-design.md`, using the JSON schema defined there (`sections[].tables[].rows[].{ranges,values}`).
- The dev machine starts with no JDK, no Android SDK, no Gradle, no project files — only `adb` (via `android-tools`) is present. Task 1 must leave the machine able to run `./gradlew` from the project root without further manual setup.

---

### Task 1: Development environment setup

**Files:** None — this task only installs system packages and the Android SDK command-line tools outside the repo, and appends environment variables to `~/.zshrc`.

**Interfaces:**
- Consumes: nothing.
- Produces: a working `java`, `sdkmanager`, and `adb` on `PATH`; `$ANDROID_HOME` pointing at an SDK install with `platform-tools`, `platforms;android-36`, and `build-tools;36.0.0` installed; `$JAVA_HOME` set to a JDK 21 install. Every later task's Gradle commands depend on these being present in a fresh shell (the Bash tool re-sources the user's shell profile on each call, so `~/.zshrc` exports are the persistence mechanism across tool calls — do not rely on `export` alone surviving between steps).

- [ ] **Step 1: Install JDK 17 and JDK 21, and a bootstrap copy of Gradle**

```bash
sudo pacman -S --needed --noconfirm jdk17-openjdk jdk21-openjdk gradle
```

Expected: pacman reports the packages installed (or "up to date" if already present). JDK 21 is the primary JDK this plan builds with; JDK 17 is installed alongside as a same-machine fallback in case AGP 9.4.1 rejects JDK 21 (see Task 2, Step 8's troubleshooting note). The `gradle` package is only ever invoked once, in Task 2, to generate this project's Gradle wrapper — after that, all builds use `./gradlew`.

- [ ] **Step 2: Persist JAVA_HOME, ANDROID_HOME, and PATH in the shell profile**

```bash
cat >> ~/.zshrc <<'EOF'

# Android dev environment for blade_runner_rpg solo-mode-roller
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME="$HOME/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF
```

- [ ] **Step 3: Verify the exports persist into a fresh shell**

```bash
echo "JAVA_HOME=$JAVA_HOME ANDROID_HOME=$ANDROID_HOME"
java -version
```

Expected: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/vonblubba/Android/sdk`, and `java -version` prints `openjdk version "21...`. If either is empty/wrong, the profile edit in Step 2 didn't take — check `~/.zshrc` was actually appended to (not `~/.bashrc`) and that the login shell is zsh (it is, per the environment: `Shell: zsh`).

- [ ] **Step 4: Download and install the Android SDK command-line tools**

```bash
mkdir -p "$ANDROID_HOME/cmdline-tools"
curl -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip"
unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extracted
mv /tmp/cmdline-tools-extracted/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-extracted
```

Expected: `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager` exists. (This is Android SDK Command-line Tools revision 23.0, the current stable release at time of writing — verified directly against Google's `repository2-3.xml` manifest. If `sdkmanager --version` in the next step fails to run, check `https://developer.android.com/studio#command-line-tools-only` for a current download link and substitute it here.)

- [ ] **Step 5: Accept SDK licenses and install platform-tools, the platform, and build-tools**

```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

Expected: no errors; final output lists the packages as installed.

- [ ] **Step 6: Verify the installed packages**

```bash
sdkmanager --list_installed
```

Expected: output includes lines for `platform-tools`, `platforms;android-36`, and `build-tools;36.0.0`.

No commit for this task — nothing in the repo changed.

---

### Task 2: Gradle project scaffold with a Hello World Compose build

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/oscarriva/solomoderoller/MainActivity.kt`
- Generate (via command, not hand-written): `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`

**Interfaces:**
- Consumes: `java`, `sdkmanager`, `gradle` (bootstrap only) from Task 1's `PATH`/`ANDROID_HOME`.
- Produces: a buildable Gradle project (`./gradlew assembleDebug` succeeds) with the `com.oscarriva.solomoderoller` package/namespace and the exact dependency set every later task's Kotlin files rely on (`kotlinx-serialization-json`, Compose BOM + `ui`/`material3`/`material-icons-core`/`activity-compose`, `core-ktx`, JUnit4).

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "SoloModeRoller"
include(":app")
```

- [ ] **Step 2: Create the root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
```

- [ ] **Step 3: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 4: Create `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.oscarriva.solomoderoller"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.oscarriva.solomoderoller"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [ ] **Step 5: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="false"
        android:label="Solo Mode Roller"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Material.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

No launcher icon is declared — the system uses its default icon, which is fine for a sideloaded personal-use app.

- [ ] **Step 6: Create a Hello World `MainActivity.kt`**

```kotlin
package com.oscarriva.solomoderoller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("Solo Mode Roller")
                }
            }
        }
    }
}
```

(Task 7 replaces this file's contents with the full navigation-wired version — this step just proves the toolchain builds a real Compose app end to end.)

- [ ] **Step 7: Generate the Gradle wrapper**

```bash
gradle wrapper --gradle-version 9.8.0
```

Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`. From this point on, use `./gradlew`, not the system `gradle`.

- [ ] **Step 8: Build the debug APK**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`, and `app/build/outputs/apk/debug/app-debug.apk` exists.

**Troubleshooting:** if the build fails with an error like `Unsupported class file major version` or `... was compiled with a Java compiler that is not supported`, JDK 21 is too new for this AGP/Gradle combination on this machine. Switch to the JDK 17 fallback installed in Task 1: edit the `export JAVA_HOME=...` line appended to `~/.zshrc` in Task 1 Step 2 to `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`, open a fresh shell, re-run `java -version` to confirm it now reports 17, and retry `./gradlew assembleDebug`.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts \
  app/src/main/AndroidManifest.xml app/src/main/java/com/oscarriva/solomoderoller/MainActivity.kt \
  gradlew gradlew.bat gradle/
git commit -m "Scaffold Android Gradle project with Hello World Compose screen"
```

---

### Task 3: Table data model and JSON parsing

**Files:**
- Create: `app/src/main/java/com/oscarriva/solomoderoller/data/RollTable.kt`
- Create: `app/src/main/java/com/oscarriva/solomoderoller/data/TableRepository.kt`
- Test: `app/src/test/java/com/oscarriva/solomoderoller/data/TableRepositoryTest.kt`

**Interfaces:**
- Consumes: `kotlinx.serialization.json.Json` (from Task 2's dependencies).
- Produces: `DieRange(min: Int, max: Int)`, `TableRow(ranges: List<DieRange>, values: List<String>)`, `RollTable(id: String, title: String, dice: List<String>, columns: List<String>, rows: List<TableRow>)`, `TableSection(name: String, tables: List<RollTable>)`, `TablesFile(sections: List<TableSection>)` — all `@Serializable`. `TableRepository.parseTables(jsonText: String): TablesFile` and `TableRepository.loadFromAssets(context: Context): TablesFile`. Tasks 4, 5, 6, and 7 all depend on these exact names and fields.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/oscarriva/solomoderoller/data/TableRepositoryTest.kt`:

```kotlin
package com.oscarriva.solomoderoller.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TableRepositoryTest {
    private val sampleJson = """
        {
          "sections": [
            {
              "name": "Test Section",
              "tables": [
                {
                  "id": "sample",
                  "title": "Sample Table",
                  "dice": ["d6"],
                  "columns": ["Result"],
                  "rows": [
                    {"ranges": [{"min":1,"max":3}], "values": ["Low"]},
                    {"ranges": [{"min":4,"max":6}], "values": ["High"]}
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesSectionsTablesAndRows() {
        val parsed = TableRepository.parseTables(sampleJson)

        assertEquals(1, parsed.sections.size)
        assertEquals("Test Section", parsed.sections[0].name)

        val table = parsed.sections[0].tables[0]
        assertEquals("sample", table.id)
        assertEquals("Sample Table", table.title)
        assertEquals(listOf("d6"), table.dice)
        assertEquals(listOf("Result"), table.columns)
        assertEquals(2, table.rows.size)
        assertEquals("Low", table.rows[0].values[0])
        assertEquals(1, table.rows[0].ranges[0].min)
        assertEquals(3, table.rows[0].ranges[0].max)
        assertEquals("High", table.rows[1].values[0])
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.oscarriva.solomoderoller.data.TableRepositoryTest"
```

Expected: compilation failure — `TableRepository` and `RollTable`/etc. don't exist yet.

- [ ] **Step 3: Create the data model**

Create `app/src/main/java/com/oscarriva/solomoderoller/data/RollTable.kt`:

```kotlin
package com.oscarriva.solomoderoller.data

import kotlinx.serialization.Serializable

@Serializable
data class DieRange(val min: Int, val max: Int)

@Serializable
data class TableRow(val ranges: List<DieRange>, val values: List<String>)

@Serializable
data class RollTable(
    val id: String,
    val title: String,
    val dice: List<String>,
    val columns: List<String>,
    val rows: List<TableRow>
)

@Serializable
data class TableSection(val name: String, val tables: List<RollTable>)

@Serializable
data class TablesFile(val sections: List<TableSection>)
```

- [ ] **Step 4: Create the repository**

Create `app/src/main/java/com/oscarriva/solomoderoller/data/TableRepository.kt`:

```kotlin
package com.oscarriva.solomoderoller.data

import android.content.Context
import kotlinx.serialization.json.Json

object TableRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseTables(jsonText: String): TablesFile = json.decodeFromString(jsonText)

    fun loadFromAssets(context: Context): TablesFile {
        val text = context.assets.open("tables.json").bufferedReader().use { it.readText() }
        return parseTables(text)
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.oscarriva.solomoderoller.data.TableRepositoryTest"
```

Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/oscarriva/solomoderoller/data/RollTable.kt \
  app/src/main/java/com/oscarriva/solomoderoller/data/TableRepository.kt \
  app/src/test/java/com/oscarriva/solomoderoller/data/TableRepositoryTest.kt
git commit -m "Add roll table data model and JSON parsing"
```

---

### Task 4: Roller logic

**Files:**
- Create: `app/src/main/java/com/oscarriva/solomoderoller/logic/Roller.kt`
- Test: `app/src/test/java/com/oscarriva/solomoderoller/logic/RollerTest.kt`

**Interfaces:**
- Consumes: `RollTable`, `TableRow`, `DieRange` from Task 3 (`com.oscarriva.solomoderoller.data`).
- Produces: `RollResult(dieValues: List<Int>, row: TableRow)`, `Roller.rollDice(table: RollTable, random: Random = Random.Default): List<Int>`, `Roller.resolveRow(table: RollTable, dieValues: List<Int>): TableRow`, `Roller.roll(table: RollTable, random: Random = Random.Default): RollResult`. Task 7's `MainActivity.kt` calls `Roller.roll(...)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/oscarriva/solomoderoller/logic/RollerTest.kt`:

```kotlin
package com.oscarriva.solomoderoller.logic

import com.oscarriva.solomoderoller.data.DieRange
import com.oscarriva.solomoderoller.data.RollTable
import com.oscarriva.solomoderoller.data.TableRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollerTest {
    private val table = RollTable(
        id = "sample",
        title = "Sample",
        dice = listOf("d6", "d12"),
        columns = listOf("Result"),
        rows = listOf(
            TableRow(ranges = listOf(DieRange(1, 2), DieRange(1, 12)), values = listOf("Bucket A")),
            TableRow(ranges = listOf(DieRange(3, 4), DieRange(1, 12)), values = listOf("Bucket B")),
            TableRow(ranges = listOf(DieRange(5, 6), DieRange(1, 12)), values = listOf("Bucket C"))
        )
    )

    @Test
    fun resolveRowFindsMatchingRowForGivenDieValues() {
        assertEquals("Bucket A", Roller.resolveRow(table, listOf(1, 7)).values[0])
        assertEquals("Bucket B", Roller.resolveRow(table, listOf(4, 1)).values[0])
        assertEquals("Bucket C", Roller.resolveRow(table, listOf(6, 12)).values[0])
    }

    @Test
    fun rollDiceProducesValuesWithinEachDiesRange() {
        repeat(200) {
            val values = Roller.rollDice(table)
            assertTrue(values[0] in 1..6)
            assertTrue(values[1] in 1..12)
        }
    }

    @Test
    fun rollCombinesRollDiceAndResolveRow() {
        val result = Roller.roll(table)
        assertEquals(result.row, Roller.resolveRow(table, result.dieValues))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.oscarriva.solomoderoller.logic.RollerTest"
```

Expected: compilation failure — `Roller` doesn't exist yet.

- [ ] **Step 3: Implement the roller**

Create `app/src/main/java/com/oscarriva/solomoderoller/logic/Roller.kt`:

```kotlin
package com.oscarriva.solomoderoller.logic

import com.oscarriva.solomoderoller.data.RollTable
import com.oscarriva.solomoderoller.data.TableRow
import kotlin.random.Random

data class RollResult(val dieValues: List<Int>, val row: TableRow)

object Roller {
    fun rollDice(table: RollTable, random: Random = Random.Default): List<Int> {
        return table.dice.map { dieSpec ->
            val sides = dieSpec.removePrefix("d").toInt()
            random.nextInt(1, sides + 1)
        }
    }

    fun resolveRow(table: RollTable, dieValues: List<Int>): TableRow {
        return table.rows.first { row ->
            row.ranges.indices.all { i -> dieValues[i] in row.ranges[i].min..row.ranges[i].max }
        }
    }

    fun roll(table: RollTable, random: Random = Random.Default): RollResult {
        val dieValues = rollDice(table, random)
        return RollResult(dieValues, resolveRow(table, dieValues))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.oscarriva.solomoderoller.logic.RollerTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/oscarriva/solomoderoller/logic/Roller.kt \
  app/src/test/java/com/oscarriva/solomoderoller/logic/RollerTest.kt
git commit -m "Add dice roller and row-resolution logic"
```

---

### Task 5: Full table data (all 22 tables) with coverage validation

**Files:**
- Create: `app/src/main/assets/tables.json`
- Test: `app/src/test/java/com/oscarriva/solomoderoller/data/TablesJsonCoverageTest.kt`

**Interfaces:**
- Consumes: `TableRepository.parseTables` (Task 3).
- Produces: the bundled `tables.json` asset that `TableRepository.loadFromAssets` (Task 3) and `MainActivity` (Task 7) read at runtime. No new Kotlin symbols.

This is the full transcription of every rollable table in `Blade Runner - Solo Mode.pdf`, per the catalog in `docs/superpowers/specs/2026-09-28-solo-mode-roller-design.md`.

- [ ] **Step 1: Write the failing coverage test**

Create `app/src/test/java/com/oscarriva/solomoderoller/data/TablesJsonCoverageTest.kt`:

```kotlin
package com.oscarriva.solomoderoller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TablesJsonCoverageTest {
    @Test
    fun everyDieValueComboMatchesExactlyOneRow() {
        val jsonText = File("src/main/assets/tables.json").readText()
        val tablesFile = TableRepository.parseTables(jsonText)

        assertTrue("Expected at least one section", tablesFile.sections.isNotEmpty())

        for (section in tablesFile.sections) {
            for (table in section.tables) {
                for (row in table.rows) {
                    assertEquals(
                        "Table '${table.title}' has a row with ranges.size != dice.size",
                        table.dice.size,
                        row.ranges.size
                    )
                    assertEquals(
                        "Table '${table.title}' has a row with values.size != columns.size",
                        table.columns.size,
                        row.values.size
                    )
                }

                val sidesPerDie = table.dice.map { it.removePrefix("d").toInt() }
                for (combo in cartesianProduct(sidesPerDie)) {
                    val matches = table.rows.count { row ->
                        row.ranges.indices.all { i -> combo[i] in row.ranges[i].min..row.ranges[i].max }
                    }
                    assertEquals(
                        "Table '${table.title}' die-value combo $combo should match exactly one row, matched $matches",
                        1,
                        matches
                    )
                }
            }
        }
    }

    private fun cartesianProduct(sidesPerDie: List<Int>): List<List<Int>> {
        return sidesPerDie.fold(listOf(listOf())) { acc, sides ->
            acc.flatMap { prefix -> (1..sides).map { value -> prefix + value } }
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.oscarriva.solomoderoller.data.TablesJsonCoverageTest"
```

Expected: fails — `src/main/assets/tables.json` doesn't exist yet.

- [ ] **Step 3: Create `app/src/main/assets/tables.json`**

```json
{
  "sections": [
    {
      "name": "Character Creation",
      "tables": [
        {
          "id": "blade_runner_origin",
          "title": "Blade Runner Origin",
          "dice": ["d12"],
          "columns": ["Result"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["In the wake of a traumatic incident, your dreams invade your waking life. You're not sure what's real anymore."]},
            {"ranges": [{"min":2,"max":2}], "values": ["Your vice has gotten the better of you before. To others, you're a liability."]},
            {"ranges": [{"min":3,"max":3}], "values": ["Your methods are unorthodox and reckless, but you always get results."]},
            {"ranges": [{"min":4,"max":4}], "values": ["You had a partner, but the case went bad. They lost their life, and you lost a friend."]},
            {"ranges": [{"min":5,"max":5}], "values": ["You reported a crooked cop. Others don't trust you."]},
            {"ranges": [{"min":6,"max":6}], "values": ["You've got a knack for finding trouble, and a reputation for collateral damage."]},
            {"ranges": [{"min":7,"max":7}], "values": ["An ongoing investigation into recent actions makes you the department pariah."]},
            {"ranges": [{"min":8,"max":8}], "values": ["After a traumatic childhood, you struggled all your life to make connections."]},
            {"ranges": [{"min":9,"max":9}], "values": ["You returned from off-world. Earth has changed, and so have you."]},
            {"ranges": [{"min":10,"max":10}], "values": ["A desperate gunfight left civilians bleeding in the street. You took the blame."]},
            {"ranges": [{"min":11,"max":11}], "values": ["Amid a sensational case, you drew too much media attention."]},
            {"ranges": [{"min":12,"max":12}], "values": ["You're part of the old guard, and your methods seem dated to young Blade Runners."]}
          ]
        }
      ]
    },
    {
      "name": "Investigation Basics",
      "tables": [
        {
          "id": "scene_categories",
          "title": "Scene Categories",
          "dice": ["d12"],
          "columns": ["Result", "Detail", "Skill Roll Examples"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Confront", "Overcome a potentially violent confrontation", "MANIPULATION to talk down a target, MOBILITY or DRIVING to close distance or block escape, HAND-TO-HAND COMBAT to subdue or restrain; FIREARMS if lethal force becomes necessary"]},
            {"ranges": [{"min":2,"max":2}], "values": ["Canvass", "Survey an area or community", "CONNECTIONS to find witnesses or hear the word on the street, OBSERVATION to look for anything out of the ordinary, INSIGHT to read the general mood"]},
            {"ranges": [{"min":3,"max":3}], "values": ["Consult", "Seek expert insight or outside perspective", "CONNECTIONS to leverage an informant or LAPD asset, MANIPULATION to gain cooperation, TECH or MEDICAL AID to verify or interpret findings"]},
            {"ranges": [{"min":4,"max":4}], "values": ["Examine", "Analyze evidence closely for details", "OBSERVATION to study subtle evidence, TECH to interpret data or digital traces, MEDICAL AID to examine forensic clues"]},
            {"ranges": [{"min":5,"max":5}], "values": ["Infiltrate", "Enter a location covertly to gather information", "CONNECTIONS to gain inside cooperation or false credentials, MANIPULATION to trick your way inside, STEALTH to move unseen, TECH to bypass security or access systems, OBSERVATION to spot important details"]},
            {"ranges": [{"min":6,"max":6}], "values": ["Pursue", "Chase a person or vehicle", "MOBILITY or DRIVING to maintain pursuit, OBSERVATION to track movement through chaotic environments, HAND-TO-HAND combat or DRIVING when ending the chase by force"]},
            {"ranges": [{"min":7,"max":7}], "values": ["Question", "Interview or interrogate a person", "CONNECTIONS to request a meeting, INSIGHT to read a subject, MANIPULATION to interrogate a suspect or uncooperative witness, TECH to operate a V-K machine or perform a baseline test"]},
            {"ranges": [{"min":8,"max":8}], "values": ["Research", "Gather data or correlate findings", "TECH to access databases, decrypt files, or analyze data, MEDICAL AID to review forensic records, EMPATHY to gain insight into recorded behaviors or testimonies"]},
            {"ranges": [{"min":9,"max":9}], "values": ["Search", "Inspect a location for clues", "OBSERVATION to locate physical evidence, TECH to access or operate specialized gear, FORCE to bypass containers or barriers"]},
            {"ranges": [{"min":10,"max":10}], "values": ["Surveil", "Observe a location or person over time", "OBSERVATION to spot details or catch sight of suspects, STEALTH to remain hidden, STAMINA to endure long hours or harsh environments"]},
            {"ranges": [{"min":11,"max":11}], "values": ["Survive", "Overcome a physical danger or harsh environment", "FORCE to overcome physical obstacles, STAMINA to endure environmental dangers and injuries, MOBILITY or DRIVING to escape danger, MEDICAL AID to patch up or lend help"]},
            {"ranges": [{"min":12,"max":12}], "values": ["Trail", "Follow a person or vehicle", "STEALTH to remain discreet, OBSERVATION to keep sight of the target, MOBILITY or DRIVING to overcome obstacles"]}
          ]
        },
        {
          "id": "scene_check",
          "title": "Scene Check",
          "dice": ["d8"],
          "columns": ["Result", "Detail", "Examples"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Complicated", "On top of one or more skill rolls, overcoming the situation may require some combination of extra time, different approaches, more resources, additional risk, or rolling with a disadvantage", "A witness is unexpectedly hostile or attempts to flee the scene; you are blocked from examining a crime scene by corporate agents; the data you seek is confidential, and requires in-person access at the LAPD Mainframe"]},
            {"ranges": [{"min":2,"max":5}], "values": ["Challenging", "Requires one or more skill rolls to proceed", "The witness is reluctant to answer your questions, or their answers are evasive; the crime scene is a mess, and requires a careful and lengthy search; finding the data you seek can be done remotely, but requires a TECH roll"]},
            {"ranges": [{"min":6,"max":7}], "values": ["Routine", "Probably does not require a skill roll", "The witness is cooperative; there are obvious clues at the crime scene; the data you seek is easily retrieved"]},
            {"ranges": [{"min":8,"max":8}], "values": ["Favorable", "Probably does not require a skill roll – if one is required, it is made with an advantage", "The witness has unexpected insight; a clue at the crime scene provides a promising break in the case; the data you seek connects to some other aspect of the case in a surprising way"]}
          ]
        },
        {
          "id": "question_check",
          "title": "Question Check",
          "dice": ["d10"],
          "columns": ["Result"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Extreme no"]},
            {"ranges": [{"min":2,"max":5}], "values": ["No"]},
            {"ranges": [{"min":6,"max":9}], "values": ["Yes"]},
            {"ranges": [{"min":10,"max":10}], "values": ["Extreme yes"]}
          ]
        },
        {
          "id": "critical_success",
          "title": "Critical Success",
          "dice": ["d8"],
          "columns": ["Result", "Bonus Effect"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Observant: Notice something unexpected or reveal an opportunity.", "If you act on this discovery, gain an advantage on your next skill roll in this scene."]},
            {"ranges": [{"min":2,"max":2}], "values": ["Confident: Commit to your current course of action.", "Recover 1 point of lost Resolve."]},
            {"ranges": [{"min":3,"max":3}], "values": ["Unnoticed: Perform the action with quiet or subtlety.", "If making a STEALTH roll during this scene, gain an advantage."]},
            {"ranges": [{"min":4,"max":4}], "values": ["Intimidating: Cause an NPC to falter or hesitate.", "The affected NPC gets a disadvantage on their next skill roll in this scene."]},
            {"ranges": [{"min":5,"max":5}], "values": ["Impressive: Make an impression on an NPC within the scene.", "One time within this scene, gain an advantage if you roll MANIPULATION against the affected NPC."]},
            {"ranges": [{"min":6,"max":6}], "values": ["Helpful: An NPC benefits from your action.", "The affected NPC gains an advantage on their next skill roll in this scene."]},
            {"ranges": [{"min":7,"max":7}], "values": ["Quick: Perform the action faster than expected.", "Building on your momentum, gain an advantage on your next skill roll during this scene."]},
            {"ranges": [{"min":8,"max":8}], "values": ["Lucky: Catch a break.", "Gain an advantage on any skill rolls in this scene."]}
          ]
        },
        {
          "id": "npc_skill_level",
          "title": "NPC Skill Level",
          "dice": ["d8"],
          "columns": ["NPC Proficiency", "Skill Roll"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Unskilled (D/D)", "D6 / D6"]},
            {"ranges": [{"min":2,"max":5}], "values": ["Competent (C/C)", "D8 / D8"]},
            {"ranges": [{"min":6,"max":7}], "values": ["Experienced (B/B)", "D10 / D10"]},
            {"ranges": [{"min":8,"max":8}], "values": ["Expert (A/A)", "D12 / D12"]}
          ]
        }
      ]
    },
    {
      "name": "Combat & Chases",
      "tables": [
        {
          "id": "npc_tactics",
          "title": "NPC Tactics",
          "dice": ["d8"],
          "columns": ["Result", "Behavior"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Reckless", "Closes the distance, throws caution to the wind, deals as much damage as possible"]},
            {"ranges": [{"min":2,"max":4}], "values": ["Strategic", "Moves decisively, picks high-value targets, surrounds and flanks"]},
            {"ranges": [{"min":5,"max":7}], "values": ["Careful", "Hangs back, sticks to cover, coordinates with allies, takes the shot when it counts"]},
            {"ranges": [{"min":8,"max":8}], "values": ["Cowardly", "Stays hidden, flees if given the chance, lashes out when cornered"]}
          ]
        },
        {
          "id": "npc_chase_maneuvers",
          "title": "NPC Chase Maneuvers",
          "dice": ["d8"],
          "columns": ["Pursuer Maneuver", "Prey Maneuver"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Stand and shoot", "Stand and shoot"]},
            {"ranges": [{"min":2,"max":5}], "values": ["Pursue", "Flee"]},
            {"ranges": [{"min":6,"max":7}], "values": ["Cut off", "Block or hide"]},
            {"ranges": [{"min":8,"max":8}], "values": ["Stand and shoot", "Stand and shoot"]}
          ]
        }
      ]
    },
    {
      "name": "Case File Tools",
      "tables": [
        {
          "id": "cipher_table",
          "title": "Cipher Table",
          "dice": ["d6", "d12"],
          "columns": ["Method", "Focus"],
          "rows": [
            {"ranges": [{"min":1,"max":2},{"min":1,"max":1}], "values": ["Abandon", "Authority"]},
            {"ranges": [{"min":1,"max":2},{"min":2,"max":2}], "values": ["Aid", "Connection"]},
            {"ranges": [{"min":1,"max":2},{"min":3,"max":3}], "values": ["Attack", "Corruption"]},
            {"ranges": [{"min":1,"max":2},{"min":4,"max":4}], "values": ["Betray", "Crime"]},
            {"ranges": [{"min":1,"max":2},{"min":5,"max":5}], "values": ["Bribe", "Death"]},
            {"ranges": [{"min":1,"max":2},{"min":6,"max":6}], "values": ["Capture", "Dream"]},
            {"ranges": [{"min":1,"max":2},{"min":7,"max":7}], "values": ["Change", "Duty"]},
            {"ranges": [{"min":1,"max":2},{"min":8,"max":8}], "values": ["Chase", "Fear"]},
            {"ranges": [{"min":1,"max":2},{"min":9,"max":9}], "values": ["Command", "Freedom"]},
            {"ranges": [{"min":1,"max":2},{"min":10,"max":10}], "values": ["Conceal", "Greed"]},
            {"ranges": [{"min":1,"max":2},{"min":11,"max":11}], "values": ["Conspire", "Guilt"]},
            {"ranges": [{"min":1,"max":2},{"min":12,"max":12}], "values": ["Control", "Hate"]},
            {"ranges": [{"min":3,"max":4},{"min":1,"max":1}], "values": ["Create", "Hope"]},
            {"ranges": [{"min":3,"max":4},{"min":2,"max":2}], "values": ["Deceive", "Identity"]},
            {"ranges": [{"min":3,"max":4},{"min":3,"max":3}], "values": ["Defy", "Justice"]},
            {"ranges": [{"min":3,"max":4},{"min":4,"max":4}], "values": ["Demand", "Law"]},
            {"ranges": [{"min":3,"max":4},{"min":5,"max":5}], "values": ["Destroy", "Life"]},
            {"ranges": [{"min":3,"max":4},{"min":6,"max":6}], "values": ["Discover", "Location"]},
            {"ranges": [{"min":3,"max":4},{"min":7,"max":7}], "values": ["Endure", "Loss"]},
            {"ranges": [{"min":3,"max":4},{"min":8,"max":8}], "values": ["Escape", "Love"]},
            {"ranges": [{"min":3,"max":4},{"min":9,"max":9}], "values": ["Fight", "Loyalty"]},
            {"ranges": [{"min":3,"max":4},{"min":10,"max":10}], "values": ["Flee", "Memory"]},
            {"ranges": [{"min":3,"max":4},{"min":11,"max":11}], "values": ["Hunt", "Obsession"]},
            {"ranges": [{"min":3,"max":4},{"min":12,"max":12}], "values": ["Infiltrate", "Passion"]},
            {"ranges": [{"min":5,"max":6},{"min":1,"max":1}], "values": ["Investigate", "Power"]},
            {"ranges": [{"min":5,"max":6},{"min":2,"max":2}], "values": ["Manipulate", "Rebellion"]},
            {"ranges": [{"min":5,"max":6},{"min":3,"max":3}], "values": ["Persuade", "Secret"]},
            {"ranges": [{"min":5,"max":6},{"min":4,"max":4}], "values": ["Preserve", "Surveillance"]},
            {"ranges": [{"min":5,"max":6},{"min":5,"max":5}], "values": ["Protect", "Technology"]},
            {"ranges": [{"min":5,"max":6},{"min":6,"max":6}], "values": ["Resist", "Temptation"]},
            {"ranges": [{"min":5,"max":6},{"min":7,"max":7}], "values": ["Reveal", "Time"]},
            {"ranges": [{"min":5,"max":6},{"min":8,"max":8}], "values": ["Sabotage", "Trust"]},
            {"ranges": [{"min":5,"max":6},{"min":9,"max":9}], "values": ["Sacrifice", "Truth"]},
            {"ranges": [{"min":5,"max":6},{"min":10,"max":10}], "values": ["Search", "Vice"]},
            {"ranges": [{"min":5,"max":6},{"min":11,"max":11}], "values": ["Seduce", "Victim"]},
            {"ranges": [{"min":5,"max":6},{"min":12,"max":12}], "values": ["Threaten", "Violence"]}
          ]
        }
      ]
    },
    {
      "name": "Case Briefing",
      "tables": [
        {
          "id": "case_briefing_assignment",
          "title": "Table 1: Assignment",
          "dice": ["d6", "d10"],
          "columns": ["Result"],
          "rows": [
            {"ranges": [{"min":1,"max":3},{"min":1,"max":1}], "values": ["Assault"]},
            {"ranges": [{"min":1,"max":3},{"min":2,"max":2}], "values": ["Black Market"]},
            {"ranges": [{"min":1,"max":3},{"min":3,"max":3}], "values": ["Blackmail"]},
            {"ranges": [{"min":1,"max":3},{"min":4,"max":4}], "values": ["Corporate Assassination"]},
            {"ranges": [{"min":1,"max":3},{"min":5,"max":5}], "values": ["Corporate Espionage"]},
            {"ranges": [{"min":1,"max":3},{"min":6,"max":6}], "values": ["Corporate Fraud"]},
            {"ranges": [{"min":1,"max":3},{"min":7,"max":7}], "values": ["Extortion"]},
            {"ranges": [{"min":1,"max":3},{"min":8,"max":8}], "values": ["Kidnapping"]},
            {"ranges": [{"min":1,"max":3},{"min":9,"max":9}], "values": ["Missing Person"]},
            {"ranges": [{"min":1,"max":3},{"min":10,"max":10}], "values": ["Murder"]},
            {"ranges": [{"min":4,"max":6},{"min":1,"max":1}], "values": ["Mysterious Death"]},
            {"ranges": [{"min":4,"max":6},{"min":2,"max":2}], "values": ["Police Corruption"]},
            {"ranges": [{"min":4,"max":6},{"min":3,"max":3}], "values": ["Political scandal"]},
            {"ranges": [{"min":4,"max":6},{"min":4,"max":4}], "values": ["Rebellion / Extremism"]},
            {"ranges": [{"min":4,"max":6},{"min":5,"max":5}], "values": ["Retirement Order"]},
            {"ranges": [{"min":4,"max":6},{"min":6,"max":6}], "values": ["Robbery"]},
            {"ranges": [{"min":4,"max":6},{"min":7,"max":7}], "values": ["Sabotage"]},
            {"ranges": [{"min":4,"max":6},{"min":8,"max":8}], "values": ["Smuggling / Trafficking"]},
            {"ranges": [{"min":4,"max":6},{"min":9,"max":9}], "values": ["Terrorism"]},
            {"ranges": [{"min":4,"max":6},{"min":10,"max":10}], "values": ["Vigilantism"]}
          ]
        },
        {
          "id": "case_briefing_relevance",
          "title": "Table 2: Relevance",
          "dice": ["d12"],
          "columns": ["Result"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Replicant is a victim"]},
            {"ranges": [{"min":2,"max":2}], "values": ["Replicant is a key witness or accuser"]},
            {"ranges": [{"min":3,"max":3}], "values": ["Replicant is a suspect"]},
            {"ranges": [{"min":4,"max":4}], "values": ["Replicant is an accomplice"]},
            {"ranges": [{"min":5,"max":5}], "values": ["Replicant is a consultant on the case"]},
            {"ranges": [{"min":6,"max":6}], "values": ["Involves Replicant tech"]},
            {"ranges": [{"min":7,"max":7}], "values": ["Involves animoid tech"]},
            {"ranges": [{"min":8,"max":8}], "values": ["Involves Human Supremacists"]},
            {"ranges": [{"min":9,"max":9}], "values": ["Involves the Replicant Underground"]},
            {"ranges": [{"min":10,"max":10}], "values": ["Involves Replicant sympathizers"]},
            {"ranges": [{"min":11,"max":11}], "values": ["Involves Wallace Corp interests"]},
            {"ranges": [{"min":12,"max":12}], "values": ["Involves a fellow Blade Runner"]}
          ]
        },
        {
          "id": "case_briefing_initial_complication",
          "title": "Table 3: Initial Complication",
          "dice": ["d12"],
          "columns": ["Result"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Requires unusual discretion or secrecy"]},
            {"ranges": [{"min":2,"max":2}], "values": ["Heavy-handed corporate oversight"]},
            {"ranges": [{"min":3,"max":3}], "values": ["High-profile media scrutiny"]},
            {"ranges": [{"min":4,"max":4}], "values": ["Rival investigator or agency is also on the case"]},
            {"ranges": [{"min":5,"max":5}], "values": ["Compromised crime scene or evidence"]},
            {"ranges": [{"min":6,"max":6}], "values": ["Outside your usual jurisdiction or expertise"]},
            {"ranges": [{"min":7,"max":7}], "values": ["Classified or redacted details – above your pay grade"]},
            {"ranges": [{"min":8,"max":8}], "values": ["Previous investigator killed or missing"]},
            {"ranges": [{"min":9,"max":9}], "values": ["Involves a notorious criminal faction or person"]},
            {"ranges": [{"min":10,"max":10}], "values": ["Involves an influential or powerful person"]},
            {"ranges": [{"min":11,"max":11}], "values": ["Cold case"]},
            {"ranges": [{"min":12,"max":12}], "values": ["Time-sensitive deadline"]}
          ]
        },
        {
          "id": "case_briefing_personal_hook",
          "title": "Table 4: Personal Hook",
          "dice": ["d12"],
          "columns": ["Result"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Involves an aspect of your key memory"]},
            {"ranges": [{"min":2,"max":2}], "values": ["Involves one of your old cases"]},
            {"ranges": [{"min":3,"max":3}], "values": ["Involves a past mistake or regret"]},
            {"ranges": [{"min":4,"max":4}], "values": ["Involves a fellow investigator or mentor"]},
            {"ranges": [{"min":5,"max":5}], "values": ["Involves a personal vice"]},
            {"ranges": [{"min":6,"max":6}], "values": ["Involves your strongly held principles or beliefs"]},
            {"ranges": [{"min":7,"max":7}], "values": ["Involves a former lover"]},
            {"ranges": [{"min":8,"max":8}], "values": ["Involves someone you owe a debt or favor to"]},
            {"ranges": [{"min":9,"max":9}], "values": ["Involves a death or loss from your past"]},
            {"ranges": [{"min":10,"max":10}], "values": ["Involves professional risk or opportunities"]},
            {"ranges": [{"min":11,"max":11}], "values": ["Involves a personal interest or obsession"]},
            {"ranges": [{"min":12,"max":12}], "values": ["Involves your key relationship"]}
          ]
        }
      ]
    },
    {
      "name": "Clue Tables",
      "tables": [
        {
          "id": "clue_table_meaning",
          "title": "Clue Table 1: Meaning",
          "dice": ["d8"],
          "columns": ["Result"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Involves your personal experiences or memories"]},
            {"ranges": [{"min":2,"max":2}], "values": ["Contradicts a previous fact or clue"]},
            {"ranges": [{"min":3,"max":3}], "values": ["Affirms a previous fact or clue"]},
            {"ranges": [{"min":4,"max":4}], "values": ["Connects to a known location"]},
            {"ranges": [{"min":5,"max":5}], "values": ["Connects to a new location"]},
            {"ranges": [{"min":6,"max":6}], "values": ["Connects to a known person"]},
            {"ranges": [{"min":7,"max":7}], "values": ["Connects to an unknown or mysterious person"]},
            {"ranges": [{"min":8,"max":8}], "values": ["Is mysterious, but intriguing"]}
          ]
        },
        {
          "id": "clue_table_evidence_descriptor",
          "title": "Clue Table 2: Evidence Descriptor",
          "dice": ["d6", "d10"],
          "columns": ["Result", "Detail"],
          "rows": [
            {"ranges": [{"min":1,"max":3},{"min":1,"max":1}], "values": ["Altered", "It is modified from its original form or function"]},
            {"ranges": [{"min":1,"max":3},{"min":2,"max":2}], "values": ["Contradictory", "It conflicts with a previously understood aspect of the case"]},
            {"ranges": [{"min":1,"max":3},{"min":3,"max":3}], "values": ["Corrupted", "It has been tampered with or degraded"]},
            {"ranges": [{"min":1,"max":3},{"min":4,"max":4}], "values": ["Counterfeit", "It is forged or faked"]},
            {"ranges": [{"min":1,"max":3},{"min":5,"max":5}], "values": ["Disguised", "It hides its true nature or purpose"]},
            {"ranges": [{"min":1,"max":3},{"min":6,"max":6}], "values": ["Disturbed", "It is displaced or disarranged"]},
            {"ranges": [{"min":1,"max":3},{"min":7,"max":7}], "values": ["Familiar", "It shares characteristics with another piece of evidence"]},
            {"ranges": [{"min":1,"max":3},{"min":8,"max":8}], "values": ["Flawed", "It is marred by damage or defect"]},
            {"ranges": [{"min":1,"max":3},{"min":9,"max":9}], "values": ["Hidden", "It is purposely concealed"]},
            {"ranges": [{"min":1,"max":3},{"min":10,"max":10}], "values": ["Marked", "It bears a message or symbol"]},
            {"ranges": [{"min":4,"max":6},{"min":1,"max":1}], "values": ["Misplaced", "It is in the wrong place or environment"]},
            {"ranges": [{"min":4,"max":6},{"min":2,"max":2}], "values": ["Missing", "It should be here, but is not"]},
            {"ranges": [{"min":4,"max":6},{"min":3,"max":3}], "values": ["Obvious", "It is readily apparent"]},
            {"ranges": [{"min":4,"max":6},{"min":4,"max":4}], "values": ["Partial", "It is incomplete or fragmented"]},
            {"ranges": [{"min":4,"max":6},{"min":5,"max":5}], "values": ["Replaced", "It is swapped for something else"]},
            {"ranges": [{"min":4,"max":6},{"min":6,"max":6}], "values": ["Residual", "It is a trace of something left behind"]},
            {"ranges": [{"min":4,"max":6},{"min":7,"max":7}], "values": ["Ruined", "It is destroyed or broken"]},
            {"ranges": [{"min":4,"max":6},{"min":8,"max":8}], "values": ["Sensitive", "It relates to or exposes protected information"]},
            {"ranges": [{"min":4,"max":6},{"min":9,"max":9}], "values": ["Subtle", "It is unremarkable or inconspicuous"]},
            {"ranges": [{"min":4,"max":6},{"min":10,"max":10}], "values": ["Unexpected", "It should not be here"]}
          ]
        },
        {
          "id": "clue_table_evidence_type",
          "title": "Clue Table 3: Evidence Type",
          "dice": ["d6", "d12"],
          "columns": ["Result"],
          "rows": [
            {"ranges": [{"min":1,"max":3},{"min":1,"max":1}], "values": ["Ammunition"]},
            {"ranges": [{"min":1,"max":3},{"min":2,"max":2}], "values": ["Body"]},
            {"ranges": [{"min":1,"max":3},{"min":3,"max":3}], "values": ["Book / magazine"]},
            {"ranges": [{"min":1,"max":3},{"min":4,"max":4}], "values": ["Container"]},
            {"ranges": [{"min":1,"max":3},{"min":5,"max":5}], "values": ["Credentials"]},
            {"ranges": [{"min":1,"max":3},{"min":6,"max":6}], "values": ["Device"]},
            {"ranges": [{"min":1,"max":3},{"min":7,"max":7}], "values": ["Document"]},
            {"ranges": [{"min":1,"max":3},{"min":8,"max":8}], "values": ["Furnishing"]},
            {"ranges": [{"min":1,"max":3},{"min":9,"max":9}], "values": ["Garment"]},
            {"ranges": [{"min":1,"max":3},{"min":10,"max":10}], "values": ["ID card"]},
            {"ranges": [{"min":1,"max":3},{"min":11,"max":11}], "values": ["Jewelry"]},
            {"ranges": [{"min":1,"max":3},{"min":12,"max":12}], "values": ["Key"]},
            {"ranges": [{"min":4,"max":6},{"min":1,"max":1}], "values": ["Map"]},
            {"ranges": [{"min":4,"max":6},{"min":2,"max":2}], "values": ["Marking / Stain"]},
            {"ranges": [{"min":4,"max":6},{"min":3,"max":3}], "values": ["Memento"]},
            {"ranges": [{"min":4,"max":6},{"min":4,"max":4}], "values": ["Message"]},
            {"ranges": [{"min":4,"max":6},{"min":5,"max":5}], "values": ["Note"]},
            {"ranges": [{"min":4,"max":6},{"min":6,"max":6}], "values": ["Photograph"]},
            {"ranges": [{"min":4,"max":6},{"min":7,"max":7}], "values": ["Print / track"]},
            {"ranges": [{"min":4,"max":6},{"min":8,"max":8}], "values": ["Recording"]},
            {"ranges": [{"min":4,"max":6},{"min":9,"max":9}], "values": ["Substance"]},
            {"ranges": [{"min":4,"max":6},{"min":10,"max":10}], "values": ["Symbol / logo"]},
            {"ranges": [{"min":4,"max":6},{"min":11,"max":11}], "values": ["Tool"]},
            {"ranges": [{"min":4,"max":6},{"min":12,"max":12}], "values": ["Weapon"]}
          ]
        }
      ]
    },
    {
      "name": "Character Tables",
      "tables": [
        {
          "id": "character_table_sphere",
          "title": "Table 1: Sphere",
          "dice": ["d6", "d8"],
          "columns": ["Result"],
          "rows": [
            {"ranges": [{"min":1,"max":3},{"min":1,"max":1}], "values": ["Commerce"]},
            {"ranges": [{"min":1,"max":3},{"min":2,"max":2}], "values": ["Craftsmanship"]},
            {"ranges": [{"min":1,"max":3},{"min":3,"max":3}], "values": ["Crime"]},
            {"ranges": [{"min":1,"max":3},{"min":4,"max":4}], "values": ["Entertainment"]},
            {"ranges": [{"min":1,"max":3},{"min":5,"max":5}], "values": ["Espionage"]},
            {"ranges": [{"min":1,"max":3},{"min":6,"max":6}], "values": ["Ideology"]},
            {"ranges": [{"min":1,"max":3},{"min":7,"max":7}], "values": ["Labor"]},
            {"ranges": [{"min":1,"max":3},{"min":8,"max":8}], "values": ["Law"]},
            {"ranges": [{"min":4,"max":6},{"min":1,"max":1}], "values": ["Media"]},
            {"ranges": [{"min":4,"max":6},{"min":2,"max":2}], "values": ["Medicine"]},
            {"ranges": [{"min":4,"max":6},{"min":3,"max":3}], "values": ["Politics"]},
            {"ranges": [{"min":4,"max":6},{"min":4,"max":4}], "values": ["Science"]},
            {"ranges": [{"min":4,"max":6},{"min":5,"max":5}], "values": ["Security"]},
            {"ranges": [{"min":4,"max":6},{"min":6,"max":6}], "values": ["Street life"]},
            {"ranges": [{"min":4,"max":6},{"min":7,"max":7}], "values": ["Technology"]},
            {"ranges": [{"min":4,"max":6},{"min":8,"max":8}], "values": ["Warfare"]}
          ]
        },
        {
          "id": "character_table_trait",
          "title": "Table 2: Trait",
          "dice": ["d6", "d12"],
          "columns": ["Result"],
          "rows": [
            {"ranges": [{"min":1,"max":2},{"min":1,"max":1}], "values": ["Aged"]},
            {"ranges": [{"min":1,"max":2},{"min":2,"max":2}], "values": ["Aggressive"]},
            {"ranges": [{"min":1,"max":2},{"min":3,"max":3}], "values": ["Alluring"]},
            {"ranges": [{"min":1,"max":2},{"min":4,"max":4}], "values": ["Aloof"]},
            {"ranges": [{"min":1,"max":2},{"min":5,"max":5}], "values": ["Argumentative"]},
            {"ranges": [{"min":1,"max":2},{"min":6,"max":6}], "values": ["Arrogant"]},
            {"ranges": [{"min":1,"max":2},{"min":7,"max":7}], "values": ["Athletic"]},
            {"ranges": [{"min":1,"max":2},{"min":8,"max":8}], "values": ["Charming"]},
            {"ranges": [{"min":1,"max":2},{"min":9,"max":9}], "values": ["Curt"]},
            {"ranges": [{"min":1,"max":2},{"min":10,"max":10}], "values": ["Demanding"]},
            {"ranges": [{"min":1,"max":2},{"min":11,"max":11}], "values": ["Desperate"]},
            {"ranges": [{"min":1,"max":2},{"min":12,"max":12}], "values": ["Eccentric"]},
            {"ranges": [{"min":3,"max":4},{"min":1,"max":1}], "values": ["Evasive"]},
            {"ranges": [{"min":3,"max":4},{"min":2,"max":2}], "values": ["Fearful"]},
            {"ranges": [{"min":3,"max":4},{"min":3,"max":3}], "values": ["Fidgety"]},
            {"ranges": [{"min":3,"max":4},{"min":4,"max":4}], "values": ["Flirty"]},
            {"ranges": [{"min":3,"max":4},{"min":5,"max":5}], "values": ["Forlorn"]},
            {"ranges": [{"min":3,"max":4},{"min":6,"max":6}], "values": ["Glamorous"]},
            {"ranges": [{"min":3,"max":4},{"min":7,"max":7}], "values": ["Gruff"]},
            {"ranges": [{"min":3,"max":4},{"min":8,"max":8}], "values": ["Harried"]},
            {"ranges": [{"min":3,"max":4},{"min":9,"max":9}], "values": ["Helpful"]},
            {"ranges": [{"min":3,"max":4},{"min":10,"max":10}], "values": ["High-strung"]},
            {"ranges": [{"min":3,"max":4},{"min":11,"max":11}], "values": ["Inquisitive"]},
            {"ranges": [{"min":3,"max":4},{"min":12,"max":12}], "values": ["Intimidating"]},
            {"ranges": [{"min":5,"max":6},{"min":1,"max":1}], "values": ["Passive"]},
            {"ranges": [{"min":5,"max":6},{"min":2,"max":2}], "values": ["Polished"]},
            {"ranges": [{"min":5,"max":6},{"min":3,"max":3}], "values": ["Ruthless"]},
            {"ranges": [{"min":5,"max":6},{"min":4,"max":4}], "values": ["Scarred"]},
            {"ranges": [{"min":5,"max":6},{"min":5,"max":5}], "values": ["Secretive"]},
            {"ranges": [{"min":5,"max":6},{"min":6,"max":6}], "values": ["Suspicious"]},
            {"ranges": [{"min":5,"max":6},{"min":7,"max":7}], "values": ["Tattooed"]},
            {"ranges": [{"min":5,"max":6},{"min":8,"max":8}], "values": ["Uncanny"]},
            {"ranges": [{"min":5,"max":6},{"min":9,"max":9}], "values": ["Unkempt"]},
            {"ranges": [{"min":5,"max":6},{"min":10,"max":10}], "values": ["Unserious"]},
            {"ranges": [{"min":5,"max":6},{"min":11,"max":11}], "values": ["Violent"]},
            {"ranges": [{"min":5,"max":6},{"min":12,"max":12}], "values": ["Youthful"]}
          ]
        },
        {
          "id": "human_or_replicant",
          "title": "Human or Replicant",
          "dice": ["d10"],
          "columns": ["Result"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Replicant"]},
            {"ranges": [{"min":2,"max":9}], "values": ["Human"]},
            {"ranges": [{"min":10,"max":10}], "values": ["Ambiguous — imagine an unusual identity, such as a rogue N-8 or digital companion"]}
          ]
        }
      ]
    },
    {
      "name": "Locations",
      "tables": [
        {
          "id": "location_table",
          "title": "Location Table",
          "dice": ["d6", "d12"],
          "columns": ["Environment", "Place"],
          "rows": [
            {"ranges": [{"min":1,"max":2},{"min":1,"max":1}], "values": ["Abandoned", "Alley"]},
            {"ranges": [{"min":1,"max":2},{"min":2,"max":2}], "values": ["Bleak", "Apartment"]},
            {"ranges": [{"min":1,"max":2},{"min":3,"max":3}], "values": ["Blocked", "Arcade"]},
            {"ranges": [{"min":1,"max":2},{"min":4,"max":4}], "values": ["Breached", "Bank"]},
            {"ranges": [{"min":1,"max":2},{"min":5,"max":5}], "values": ["Chaotic", "Bar"]},
            {"ranges": [{"min":1,"max":2},{"min":6,"max":6}], "values": ["Claustrophobic", "Bazaar"]},
            {"ranges": [{"min":1,"max":2},{"min":7,"max":7}], "values": ["Cluttered", "Casino"]},
            {"ranges": [{"min":1,"max":2},{"min":8,"max":8}], "values": ["Confined", "Clinic"]},
            {"ranges": [{"min":1,"max":2},{"min":9,"max":9}], "values": ["Crowded", "Club"]},
            {"ranges": [{"min":1,"max":2},{"min":10,"max":10}], "values": ["Damaged", "Construction site"]},
            {"ranges": [{"min":1,"max":2},{"min":11,"max":11}], "values": ["Dangerous", "Data Center"]},
            {"ranges": [{"min":1,"max":2},{"min":12,"max":12}], "values": ["Dark", "Dock"]},
            {"ranges": [{"min":3,"max":4},{"min":1,"max":1}], "values": ["Decaying", "Facility"]},
            {"ranges": [{"min":3,"max":4},{"min":2,"max":2}], "values": ["Empty", "Factory"]},
            {"ranges": [{"min":3,"max":4},{"min":3,"max":3}], "values": ["Familiar", "Garage"]},
            {"ranges": [{"min":3,"max":4},{"min":4,"max":4}], "values": ["Garish", "Headquarters"]},
            {"ranges": [{"min":3,"max":4},{"min":5,"max":5}], "values": ["Hazy", "Home"]},
            {"ranges": [{"min":3,"max":4},{"min":6,"max":6}], "values": ["Hidden", "Hospital"]},
            {"ranges": [{"min":3,"max":4},{"min":7,"max":7}], "values": ["Isolated", "Hotel"]},
            {"ranges": [{"min":3,"max":4},{"min":8,"max":8}], "values": ["Large", "Lab"]},
            {"ranges": [{"min":3,"max":4},{"min":9,"max":9}], "values": ["Lavish", "Library"]},
            {"ranges": [{"min":3,"max":4},{"min":10,"max":10}], "values": ["Luxurious", "Lobby"]},
            {"ranges": [{"min":3,"max":4},{"min":11,"max":11}], "values": ["Maze-like", "Monument"]},
            {"ranges": [{"min":3,"max":4},{"min":12,"max":12}], "values": ["Neon-lit", "Municipal building"]},
            {"ranges": [{"min":5,"max":6},{"min":1,"max":1}], "values": ["Noisy", "Nightclub"]},
            {"ranges": [{"min":5,"max":6},{"min":2,"max":2}], "values": ["Open", "Office"]},
            {"ranges": [{"min":5,"max":6},{"min":3,"max":3}], "values": ["Rain-soaked", "Restaurant"]},
            {"ranges": [{"min":5,"max":6},{"min":4,"max":4}], "values": ["Ruined", "Rooftop"]},
            {"ranges": [{"min":5,"max":6},{"min":5,"max":5}], "values": ["Safe", "Ruin"]},
            {"ranges": [{"min":5,"max":6},{"min":6,"max":6}], "values": ["Secret", "Safehouse"]},
            {"ranges": [{"min":5,"max":6},{"min":7,"max":7}], "values": ["Silent", "Shop"]},
            {"ranges": [{"min":5,"max":6},{"min":8,"max":8}], "values": ["Simple", "Street"]},
            {"ranges": [{"min":5,"max":6},{"min":9,"max":9}], "values": ["Small", "Transit hub"]},
            {"ranges": [{"min":5,"max":6},{"min":10,"max":10}], "values": ["Stinking", "Tunnel"]},
            {"ranges": [{"min":5,"max":6},{"min":11,"max":11}], "values": ["Subsurface", "Viaduct"]},
            {"ranges": [{"min":5,"max":6},{"min":12,"max":12}], "values": ["Towering", "Warehouse"]}
          ]
        }
      ]
    },
    {
      "name": "Event Tables",
      "tables": [
        {
          "id": "downtime_event_table",
          "title": "Downtime Event Table",
          "dice": ["d12"],
          "columns": ["Home", "Street"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["You experience a dream related to the current case.", "You catch a glimpse of someone or something involved in the current case."]},
            {"ranges": [{"min":2,"max":2}], "values": ["You experience a dream in which you relive your key memory – with a crucial difference.", "You spot some element of your key memory in the streets."]},
            {"ranges": [{"min":3,"max":3}], "values": ["You are contacted or visited by someone involved in the current case.", "You are confronted by someone involved in the current case."]},
            {"ranges": [{"min":4,"max":4}], "values": ["You are contacted or visited by someone involved in an old case.", "You are confronted by someone involved in an old case."]},
            {"ranges": [{"min":5,"max":5}], "values": ["You receive a cryptic message or delivery.", "You encounter a stranger who delivers a message or warning."]},
            {"ranges": [{"min":6,"max":6}], "values": ["You catch sight of a device or person surveilling your home.", "You catch sight of someone watching or following you."]},
            {"ranges": [{"min":7,"max":7}], "values": ["Media reports reveal an unexpected aspect of the current case.", "You encounter an advertisement that evokes an aspect of the current case."]},
            {"ranges": [{"min":8,"max":8}], "values": ["You are contacted or visited by your key relationship or other connection. They need something.", "You encounter your key relationship or other connection. They need something."]},
            {"ranges": [{"min":9,"max":9}], "values": ["Outside your home, cops swarm to a nearby incident.", "You come upon a crime scene or the aftermath of an incident."]},
            {"ranges": [{"min":10,"max":10}], "values": ["A noise or disturbance interrupts a restless sleep.", "You are caught in a dangerous encounter or disturbance."]},
            {"ranges": [{"min":11,"max":11}], "values": ["Time passes without your awareness, hours slipping by.", "You find yourself in an unfamiliar location, barely remembering the path that led you there."]},
            {"ranges": [{"min":12,"max":12}], "values": ["You find comfort in solitude. Heal an extra point of stress.", "You find comfort or companionship among the crowds. Heal an extra point of stress."]}
          ]
        },
        {
          "id": "countdown_event_table",
          "title": "Countdown Event Table",
          "dice": ["d12"],
          "columns": ["Result", "Examples"],
          "rows": [
            {"ranges": [{"min":1,"max":1}], "values": ["Accusation", "Implicated or framed, secrets or misconduct revealed"]},
            {"ranges": [{"min":2,"max":2}], "values": ["Betrayal", "Ally reveals true motive or loyalty, traitor makes their move, sabotage from within"]},
            {"ranges": [{"min":3,"max":3}], "values": ["Confrontation", "Ambushed, pursued, snatched"]},
            {"ranges": [{"min":4,"max":4}], "values": ["Development", "New evidence, another victim, witness comes forward"]},
            {"ranges": [{"min":5,"max":5}], "values": ["Disaster", "Violence breaks out, collision or explosion, mass casualties"]},
            {"ranges": [{"min":6,"max":6}], "values": ["Diversion", "False lead, wild goose chase, drawn into a trap"]},
            {"ranges": [{"min":7,"max":7}], "values": ["Entanglement", "Key relationship drawn into danger, old debt resurfaces, personal vice causes trouble"]},
            {"ranges": [{"min":8,"max":8}], "values": ["Interference", "Bribe or coercion, access or privileges lost, taken off the case"]},
            {"ranges": [{"min":9,"max":9}], "values": ["Loss", "Evidence stolen or destroyed, key person killed or missing, safehouse compromised"]},
            {"ranges": [{"min":10,"max":10}], "values": ["Pressure", "Higher-ups push for progress, new deadline imposed, media coverage amps up"]},
            {"ranges": [{"min":11,"max":11}], "values": ["Summons", "Authority demands your presence, witness or suspect has news, connection asks to meet"]},
            {"ranges": [{"min":12,"max":12}], "values": ["Threat", "Cryptic message, home or vehicle ransacked, warned off the case"]}
          ]
        }
      ]
    }
  ]
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.oscarriva.solomoderoller.data.TablesJsonCoverageTest"
```

Expected: `BUILD SUCCESSFUL`, 1 test passed. If it fails with a "should match exactly one row, matched 0" or "matched 2" message, there's a transcription typo in the named table's ranges — fix the specific range in `tables.json` and re-run.

- [ ] **Step 5: Also re-run the full test suite so far**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all tests from Tasks 3–5 passing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/assets/tables.json \
  app/src/test/java/com/oscarriva/solomoderoller/data/TablesJsonCoverageTest.kt
git commit -m "Add full Solo Mode table data (22 tables) with range coverage test"
```

---

### Task 6: Roll history model

**Files:**
- Create: `app/src/main/java/com/oscarriva/solomoderoller/model/RollEntry.kt`
- Create: `app/src/main/java/com/oscarriva/solomoderoller/model/RollHistoryViewModel.kt`
- Test: `app/src/test/java/com/oscarriva/solomoderoller/model/RollHistoryViewModelTest.kt`

**Interfaces:**
- Consumes: `androidx.compose.runtime.mutableStateListOf` (from Task 2's Compose dependencies; this is pure-JVM, no Android framework needed, so it's usable in local unit tests).
- Produces: `RollEntry(id: Long, tableTitle: String, diceDescription: String, resultLines: List<String>, timestamp: Long)`, `RollHistoryViewModel` with `entries: List<RollEntry>`, `addEntry(tableTitle: String, diceDescription: String, resultLines: List<String>)`, and `clear()`. Task 7's `MainActivity.kt` instantiates this with `remember { RollHistoryViewModel() }`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/oscarriva/solomoderoller/model/RollHistoryViewModelTest.kt`:

```kotlin
package com.oscarriva.solomoderoller.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollHistoryViewModelTest {
    @Test
    fun addEntryPrependsNewestRollFirst() {
        val viewModel = RollHistoryViewModel()

        viewModel.addEntry("Table A", "D6: 3", listOf("Result: Low"))
        viewModel.addEntry("Table B", "D6: 5", listOf("Result: High"))

        assertEquals(2, viewModel.entries.size)
        assertEquals("Table B", viewModel.entries[0].tableTitle)
        assertEquals("Table A", viewModel.entries[1].tableTitle)
    }

    @Test
    fun clearEmptiesTheHistory() {
        val viewModel = RollHistoryViewModel()

        viewModel.addEntry("Table A", "D6: 3", listOf("Result: Low"))
        viewModel.clear()

        assertTrue(viewModel.entries.isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.oscarriva.solomoderoller.model.RollHistoryViewModelTest"
```

Expected: compilation failure — `RollEntry`/`RollHistoryViewModel` don't exist yet.

- [ ] **Step 3: Implement `RollEntry.kt`**

Create `app/src/main/java/com/oscarriva/solomoderoller/model/RollEntry.kt`:

```kotlin
package com.oscarriva.solomoderoller.model

data class RollEntry(
    val id: Long,
    val tableTitle: String,
    val diceDescription: String,
    val resultLines: List<String>,
    val timestamp: Long
)
```

- [ ] **Step 4: Implement `RollHistoryViewModel.kt`**

Create `app/src/main/java/com/oscarriva/solomoderoller/model/RollHistoryViewModel.kt`:

```kotlin
package com.oscarriva.solomoderoller.model

import androidx.compose.runtime.mutableStateListOf

class RollHistoryViewModel {
    private val _entries = mutableStateListOf<RollEntry>()
    val entries: List<RollEntry> get() = _entries

    private var nextId = 0L

    fun addEntry(tableTitle: String, diceDescription: String, resultLines: List<String>) {
        _entries.add(0, RollEntry(nextId++, tableTitle, diceDescription, resultLines, System.currentTimeMillis()))
    }

    fun clear() {
        _entries.clear()
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.oscarriva.solomoderoller.model.RollHistoryViewModelTest"
```

Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/oscarriva/solomoderoller/model/RollEntry.kt \
  app/src/main/java/com/oscarriva/solomoderoller/model/RollHistoryViewModel.kt \
  app/src/test/java/com/oscarriva/solomoderoller/model/RollHistoryViewModelTest.kt
git commit -m "Add roll history model"
```

---

### Task 7: UI screens, noir theme, and navigation wiring

**Files:**
- Create: `app/src/main/java/com/oscarriva/solomoderoller/ui/theme/Color.kt`
- Create: `app/src/main/java/com/oscarriva/solomoderoller/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/oscarriva/solomoderoller/ui/SectionListScreen.kt`
- Create: `app/src/main/java/com/oscarriva/solomoderoller/ui/TableRollScreen.kt`
- Modify: `app/src/main/java/com/oscarriva/solomoderoller/MainActivity.kt` (replaces the Task 2 Hello World body entirely)

**Interfaces:**
- Consumes: `TableRepository.loadFromAssets` (Task 3), `TableSection`/`RollTable` (Task 3), `Roller.roll` (Task 4), `RollHistoryViewModel`/`RollEntry` (Task 6).
- Produces: `SoloModeRollerTheme(content: @Composable () -> Unit)`, `SectionListScreen(sections: List<TableSection>, onTableClick: (RollTable) -> Unit)`, `TableRollScreen(table: RollTable, history: List<RollEntry>, onRoll: () -> Unit, onClear: () -> Unit, onBack: () -> Unit)`. Task 8 exercises these by running the built app.

No new automated tests in this task — Compose UI behavior for this app is verified manually on-device in Task 8, per the design spec's testing plan (no instrumented-test/emulator setup is in scope). Each step below still ends in a concrete, runnable check (`./gradlew assembleDebug` after the final step).

- [ ] **Step 1: Create the noir color palette**

Create `app/src/main/java/com/oscarriva/solomoderoller/ui/theme/Color.kt`:

```kotlin
package com.oscarriva.solomoderoller.ui.theme

import androidx.compose.ui.graphics.Color

val NoirBackground = Color(0xFF0B0E11)
val NoirSurface = Color(0xFF14181D)
val NoirSurfaceVariant = Color(0xFF1E242B)
val NeonAmber = Color(0xFFFFB020)
val NoirOnBackground = Color(0xFFE4E4E4)
val NoirOnSurfaceMuted = Color(0xFFA0A8B0)
```

- [ ] **Step 2: Create the theme**

Create `app/src/main/java/com/oscarriva/solomoderoller/ui/theme/Theme.kt`:

```kotlin
package com.oscarriva.solomoderoller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SoloModeColorScheme = darkColorScheme(
    primary = NeonAmber,
    onPrimary = Color.Black,
    background = NoirBackground,
    onBackground = NoirOnBackground,
    surface = NoirSurface,
    onSurface = NoirOnBackground,
    surfaceVariant = NoirSurfaceVariant,
    onSurfaceVariant = NoirOnSurfaceMuted
)

@Composable
fun SoloModeRollerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoloModeColorScheme,
        content = content
    )
}
```

- [ ] **Step 3: Create the section list screen**

Create `app/src/main/java/com/oscarriva/solomoderoller/ui/SectionListScreen.kt`:

```kotlin
package com.oscarriva.solomoderoller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oscarriva.solomoderoller.data.RollTable
import com.oscarriva.solomoderoller.data.TableSection

@Composable
fun SectionListScreen(
    sections: List<TableSection>,
    onTableClick: (RollTable) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Solo Mode Roller") }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(sections) { section ->
                SectionCard(section = section, onTableClick = onTableClick)
            }
        }
    }
}

@Composable
private fun SectionCard(section: TableSection, onTableClick: (RollTable) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column {
            ListItem(
                headlineContent = { Text(section.name) },
                modifier = Modifier.clickable { expanded = !expanded }
            )
            if (expanded) {
                section.tables.forEach { table ->
                    ListItem(
                        headlineContent = { Text(table.title) },
                        supportingContent = { Text(table.dice.joinToString(" + ") { it.uppercase() }) },
                        modifier = Modifier.clickable { onTableClick(table) }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Create the table roll screen**

Create `app/src/main/java/com/oscarriva/solomoderoller/ui/TableRollScreen.kt`:

```kotlin
package com.oscarriva.solomoderoller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oscarriva.solomoderoller.data.RollTable
import com.oscarriva.solomoderoller.model.RollEntry

@Composable
fun TableRollScreen(
    table: RollTable,
    history: List<RollEntry>,
    onRoll: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(table.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear history")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onRoll, modifier = Modifier.fillMaxWidth()) {
                Text("Roll")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { entry ->
                    RollEntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun RollEntryCard(entry: RollEntry) {
    Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(entry.diceDescription)
            entry.resultLines.forEach { line -> Text(line) }
        }
    }
}
```

- [ ] **Step 5: Replace `MainActivity.kt` with the full navigation wiring**

Replace the entire contents of `app/src/main/java/com/oscarriva/solomoderoller/MainActivity.kt`:

```kotlin
package com.oscarriva.solomoderoller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.oscarriva.solomoderoller.data.RollTable
import com.oscarriva.solomoderoller.data.TableRepository
import com.oscarriva.solomoderoller.logic.Roller
import com.oscarriva.solomoderoller.model.RollHistoryViewModel
import com.oscarriva.solomoderoller.ui.SectionListScreen
import com.oscarriva.solomoderoller.ui.TableRollScreen
import com.oscarriva.solomoderoller.ui.theme.SoloModeRollerTheme

private sealed class Screen {
    data object SectionList : Screen()
    data class TableRoll(val table: RollTable) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tablesFile = TableRepository.loadFromAssets(this)

        setContent {
            SoloModeRollerTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.SectionList) }
                val historyViewModel = remember { RollHistoryViewModel() }

                when (val current = screen) {
                    is Screen.SectionList -> SectionListScreen(
                        sections = tablesFile.sections,
                        onTableClick = { table -> screen = Screen.TableRoll(table) }
                    )
                    is Screen.TableRoll -> TableRollScreen(
                        table = current.table,
                        history = historyViewModel.entries,
                        onRoll = {
                            val result = Roller.roll(current.table)
                            val diceDescription = current.table.dice.zip(result.dieValues)
                                .joinToString(" · ") { (die, value) -> "${die.uppercase()}: $value" }
                            val resultLines = current.table.columns.zip(result.row.values)
                                .map { (column, value) -> "$column: $value" }
                            historyViewModel.addEntry(current.table.title, diceDescription, resultLines)
                        },
                        onClear = { historyViewModel.clear() },
                        onBack = { screen = Screen.SectionList }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 6: Build to confirm everything compiles and links**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`, `app/build/outputs/apk/debug/app-debug.apk` rebuilt.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/oscarriva/solomoderoller/ui/ \
  app/src/main/java/com/oscarriva/solomoderoller/MainActivity.kt
git commit -m "Add noir-themed UI screens and wire up navigation and rolling"
```

---

### Task 8: Install and smoke-test on a device or emulator

**Files:** None — this task installs and exercises the built APK; no source changes are expected unless the smoke test surfaces a bug, in which case fix it in the relevant file from Tasks 3–7 and re-run this task's steps.

**Interfaces:**
- Consumes: `app/build/outputs/apk/debug/app-debug.apk` (Task 7).
- Produces: a confirmed-working installed app. Nothing downstream depends on this task.

- [ ] **Step 1: Check for a connected device or running emulator**

```bash
adb devices
```

Expected: at least one line under "List of devices attached" with status `device` (not `unauthorized` or `offline`). If nothing is listed, connect an Android phone via USB with USB debugging enabled (Settings → About phone → tap Build number 7 times → Developer options → USB debugging), and re-run this command. If no physical device is available, install one Android platform's system image and an AVD via `sdkmanager "system-images;android-36;google_apis;x86_64"` and `avdmanager create avd -n solo_roller -k "system-images;android-36;google_apis;x86_64"`, then start it with `$ANDROID_HOME/emulator/emulator -avd solo_roller` before continuing.

- [ ] **Step 2: Install the APK**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`.

- [ ] **Step 3: Launch the app**

```bash
adb shell am start -n com.oscarriva.solomoderoller/.MainActivity
```

Expected: the app opens on the device/emulator showing the "Solo Mode Roller" section list.

- [ ] **Step 4: Manual smoke test checklist**

On the device, work through this checklist (report any failure with the specific section/table name):

1. All 9 sections are visible and expandable/collapsible: Character Creation, Investigation Basics, Combat & Chases, Case File Tools, Case Briefing, Clue Tables, Character Tables, Locations, Event Tables.
2. Open "Blade Runner Origin" (Character Creation), tap Roll 3 times — each roll shows a D12 value and matching origin text, and 3 entries accumulate in the history list, newest first.
3. Open "Cipher Table" (Case File Tools) — a two-die table — tap Roll, confirm both a Method and a Focus value are shown together for the same roll.
4. Open "Question Check" (Investigation Basics), roll until a 1 and a 10 come up (or roll ~15 times) — confirm "Extreme no" and "Extreme yes" both appear correctly (this table had a transcription risk noted in Task 5).
5. On any table's roll screen, tap the clear-history (trash) icon — confirm the history list empties.
6. Tap the back arrow — confirm it returns to the section list, and that re-opening a different table starts with the history from earlier taps still present (history is per-session, shared across tables, not per-table).
7. Force-close and relaunch the app (`adb shell am force-stop com.oscarriva.solomoderoller` then Step 3 again) — confirm history is empty again (in-memory only, as designed).

Expected: all 7 checks pass. If any table's displayed text looks wrong or truncated, cross-check it against `Blade Runner - Solo Mode.pdf` and fix the corresponding row in `app/src/main/assets/tables.json`, then re-run Task 5 Step 4's test, rebuild (Task 7 Step 6), and repeat this task's install/launch steps.

- [ ] **Step 5: No commit needed** — this task only installs and exercises the already-committed build. If Step 4 required a `tables.json` fix, commit that fix with a message describing which table was corrected.

---

## Self-Review Notes

- **Spec coverage:** every spec section maps to a task — data schema → Tasks 3 & 5; architecture/file structure → Tasks 3, 4, 6, 7; build tooling → Task 1 & 2; UI/UX → Task 7; testing plan (unit tests for Roller, JSON coverage check, manual smoke test) → Tasks 4, 5, 8.
- **Type/name consistency checked:** `RollTable`, `TableRow`, `DieRange`, `TableSection`, `TablesFile` (Task 3) are used with identical field names in Tasks 4, 5, 6, and 7. `Roller.roll`/`rollDice`/`resolveRow` (Task 4) match their usage in `MainActivity.kt` (Task 7). `RollHistoryViewModel.addEntry`/`clear`/`entries` (Task 6) match their usage in Task 7. Package `com.oscarriva.solomoderoller` and its `applicationId`/`namespace` match everywhere.
- **No placeholders:** all 22 tables in Task 5 are fully transcribed with real text, not summarized or stubbed.
