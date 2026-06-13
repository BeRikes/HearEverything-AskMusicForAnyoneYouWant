# App Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing app icon and notification icon with the M+♬ fusion design.

**Architecture:** Pure vector drawables (no raster images). Adaptive icon uses two layers: solid dark background + red foreground. Same design used for notification small icon.

**Tech Stack:** Android Vector Drawable XML (standard `<vector>` with `<path>`), no additional dependencies.

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `androidApp/src/main/res/drawable/ic_launcher_foreground.xml` | Rewrite | M+♬ 前景图形 |
| `androidApp/src/main/res/drawable/ic_launcher_background.xml` | No change | 暗色底 #141418（已正确） |
| `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | No change | Adaptive icon 定义（已正确） |
| `shared/src/androidMain/res/drawable/ic_music_note.xml` | Rewrite | 通知小图标，同图形 |

---

### Task 1: Rewrite launcher foreground icon

**Files:**
- Modify: `androidApp/src/main/res/drawable/ic_launcher_foreground.xml`

- [ ] **Step 1: Write the new foreground vector**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- M + ♬ fusion icon — pure red #E84B4B on dark background -->

    <!-- Note heads (drawn first, stems + beams on top) -->
    <!-- Left: center (34,72), rx=12, ry=9, rotated -20° -->
    <path
        android:fillColor="#E84B4B"
        android:pathData="M22,72 C22,67 27,63 34,63 C41,63 46,67 46,72 C46,77 41,81 34,81 C27,81 22,77 22,72Z"
        android:rotation="-20"
        android:pivotX="34"
        android:pivotY="72" />

    <!-- Right: center (72,70), rx=12, ry=9, rotated -20° -->
    <path
        android:fillColor="#E84B4B"
        android:pathData="M60,70 C60,65 65,61 72,61 C79,61 84,65 84,70 C84,75 79,79 72,79 C65,79 60,75 60,70Z"
        android:rotation="-20"
        android:pivotX="72"
        android:pivotY="70" />

    <!-- Stems + double-V beams (all one stroked path for clean joints) -->
    <!-- Left stem: (40,66)→(40,22) | Right stem: (78,64)→(78,24) -->
    <!-- Upper V: (40,26)→(70,31)→(78,27) | Lower V: (40,36)→(49,41)→(78,37) -->
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#E84B4B"
        android:strokeWidth="4"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="
            M40,22 L40,66
            M78,24 L78,64
            M40,26 L70,31 L78,27
            M40,36 L49,41 L78,37
        " />

</vector>
```

- [ ] **Step 2: Build and inspect**

```bash
./gradlew :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. Install APK and verify icon appears on launcher.

**Note:** The `pathData` for note heads uses approximate bezier curves. If the note head shape doesn't render correctly, use `<path>` with explicit arc commands or fall back to `<ellipse>` elements with rotation via `android:rotation`.

---

### Task 2: Rewrite notification icon

**Files:**
- Modify: `shared/src/androidMain/res/drawable/ic_music_note.xml`

- [ ] **Step 1: Write the notification icon vector (same M+♬, 24dp viewport)**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">

    <!-- Note heads (scaled to 24dp: rx≈3, ry≈2.2) -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M5.5,16 C5.5,14.5 7,13 9,13 C11,13 12.5,14.5 12.5,16 C12.5,17.5 11,19 9,19 C7,19 5.5,17.5 5.5,16Z"
        android:rotation="-20"
        android:pivotX="9"
        android:pivotY="16" />

    <path
        android:fillColor="#FFFFFF"
        android:pathData="M13.5,15.5 C13.5,14 15,12.5 17,12.5 C19,12.5 20.5,14 20.5,15.5 C20.5,17 19,18.5 17,18.5 C15,18.5 13.5,17 13.5,15.5Z"
        android:rotation="-20"
        android:pivotX="17"
        android:pivotY="15.5" />

    <!-- Stems + double-V beams -->
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="1.5"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="
            M9,4 L9,15
            M17,5 L17,14.5
            M9,5 L15,6.5 L17,6
            M9,8 L11,9 L17,8.5
        " />

</vector>
```

- [ ] **Step 2: Build**

```bash
./gradlew :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

---

### Task 3: Final verification

- [ ] **Step 1: Clean build**

```bash
./gradlew clean :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Verify icon resources exist in APK**

```bash
# Check that the drawable resources are packaged
unzip -l androidApp/build/outputs/apk/debug/androidApp-debug.apk | grep -E "ic_launcher|ic_music_note"
```

Expected: Lists all four drawable files.

---

## Self-Review Notes

1. Note heads use `<path>` with `android:rotation`/`android:pivotX`/`android:pivotY` attributes (API 26+, matches minSdk).
2. The notification icon (24dp) uses proportionally scaled coordinates and stroke widths.
3. Both icons are monochrome — Android notification system auto-tints the small icon for visibility.
