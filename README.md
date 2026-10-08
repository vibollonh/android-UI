# BaseUI for Android

Android port of the iOS BaseUI components, for **Jetpack Compose** and **XML views**.

| Module | What's in it |
|---|---|
| `baseui-core` | Shared, UI-free logic: `DeviceScreen` (cutout + corner radius), toast geometry, reorder operations, zoom drag maths. Unit-tested. |
| `baseui-compose` | Compose components. |
| `baseui-views` | View / XML components (Material Components). |
| `sample` | Catalog app; every feature has a Compose tab and an XML tab. |

## Components

| Feature | Compose | XML views |
|---|---|---|
| Cutout toast (grows out of the punch-hole camera; pill under the status bar elsewhere) | `CutoutToastHost` + `rememberCutoutToastState()` | `CutoutToast.show(activity, …)` |
| Button (filled / tonal / outlined / text, 3 sizes, loading) | `BaseButton` | `<…views.BaseButton app:baseVariant app:baseSize app:loading>` |
| Text field (title, icon, helper / error, password toggle) | `BaseTextField` | `<…views.BaseTextField app:title app:isPassword>` |
| Dropdown field | `BaseDropdownField` | `<…views.BaseDropdownField android:entries>` |
| Reorder list (items across sections, whole sections, pinned / locked / swap-on-drop sections; list, grid, carousel, banner, horizontal banner) | `BaseReorderCollection` | `<…views.BaseReorderView>` |
| Zoom transition (card → full screen; drag down / right or back gesture to close; no drag-to-close on pushed screens) | `BaseZoomHost`, `Modifier.zoomSource`, `LocalZoom.current.present` | `BaseZoomTransition.present(activity, view, fragment)` |
| Device screen | `DeviceScreen.from(view)` (core) | same |

## Quick start

```kotlin
// Compose
BaseZoomHost {
    val toast = rememberCutoutToastState()
    Box {
        Screen(onPaid = { toast.show("Transfer completed", ToastStyle.Success) })
        CutoutToastHost(toast)
    }
}

// Views
CutoutToast.show(this, "Transfer completed", ToastStyle.Success)
BaseZoomTransition.present(this, cardView, DetailFragment())
```

The cutout toast draws over the status bar, so call `enableEdgeToEdge()` in the activity.

## Build

```bash
./gradlew :baseui-core:testDebugUnitTest :sample:installDebug
```

Requires JDK 17+ (Android Studio's bundled JBR works) and compileSdk 37.
