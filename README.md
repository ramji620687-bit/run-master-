# Run Master - GPS Running & Stadium Track App (v1.1)

Native Android app: Kotlin, Jetpack Compose (Material 3), Fused Location, OpenStreetMap (osmdroid), Room.

## Kya kya hai
- **Background tracking**: Foreground Service (type = location) + notification (Pause / Stop buttons) + partial wake lock.
  Screen lock karke ya app minimize karke bhi GPS record hota rehta hai.
- **GPS filter**: accuracy > 25 m aur speed > 15 m/s wale jump reject.
- **Stadium track mode**: 200 / 300 / 400 m ya custom length; optional start/finish line (Mark A / Mark B).
- **Auto lap counter**: line-crossing (agar line set hai) warna distance-based.
- **Voice**: Hindi + English announcements (har km aur har lap par). Hindi voice phone me installed honi chahiye.
- **Live map**, **History** (lap splits ke saath), **Stats** (totals + last 7 workouts chart).

## APK banane ka tarika
1. Zip extract karo.
2. **Android Studio Koala (2024.1.1) ya naya** kholo -> Open -> `RunMaster` folder.
3. Gradle sync hone do (internet chahiye). Agar Studio "Gradle wrapper" ke liye puche to OK karo.
4. Build -> Build Bundle(s)/APK(s) -> Build APK(s).
5. `app/build/outputs/apk/debug/app-debug.apk` phone me install karo.

## Background me achhe se chalane ke liye (zaroori)
1. Run shuru karte waqt **Location = "While using the app"/"Allow"** do (background-location permission ki zaroorat nahi, kyunki service app khula hone par start hoti hai).
2. **Notifications allow** karo (Android 13+).
3. Home screen par **"Allow background running"** dabao (battery optimization off).
4. Xiaomi / Redmi / Poco / Oppo / Realme / Vivo / OnePlus: Settings -> Apps -> Run Master -> **Autostart ON** aur Battery -> **No restrictions**; Recent apps me app ko **lock** kar do.
5. Phone ko app swipe-away karne par bhi service chalti rehti hai; band karne ke liye notification ka **Stop** ya app me **FINISH** dabao.

## Project structure
- `service/LocationTrackingService.kt` - background GPS engine, laps, voice, wake lock, save to DB
- `service/TrackingState.kt` - live state shared with UI
- `domain/` - LapDetectionEngine, VoiceAnnouncementManager
- `data/` - Room database (workouts + laps), SharedPreferences
- `ui/` - Compose screens (Home, Live, Ground, History, Stats)
