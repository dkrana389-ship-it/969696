# Focus (v2) — bina Accessibility ke

Jab bhi Instagram / YouTube / Facebook / Snapchat khulta hai, poori screen ka **Focus check-in popup** aata hai:
aaj ka time, "saal mein itne ghante", kitni baar khola, aur aapka goal (default: *Mahindra Scorpio*),
phir **Close** ya **Continue in 3s**.

## Accessibility kyun nahi chahiye
- **Usage access** se pata chalta hai kaunsa app khula (UsageStatsManager) aur kitna time laga.
- **Display over other apps** se popup poori screen par app ke upar aata hai.
- Ek foreground service (notification ke saath) background mein watch karti hai.

## Setup (phone par)
1. App kholo → **Usage access** → Grant → Focus ko ON karo.
2. **Display over other apps** → Grant.
3. (Recommended) Notifications aur Unrestricted battery → Allow.
4. Goal likho, apps chuno, **Start Focus** dabao. **Preview the check-in** se popup test kar sakte ho.

## APK kaise banega
GitHub par push karo → Actions tab → *Build Focus* → artifact **Focus-APK** (Focus-debug.apk + Focus-source.zip).
`v*` tag push karoge to Release bhi ban jayegi.

Local build: `gradle assembleDebug` (JDK 17 + Android SDK 34).

## Streak
Din tab count hota hai jab guarded apps par total time aapki *Daily limit* (default 60 min) ke andar rahe.
Service chalti rahe to har din ka record apne aap save hota hai.

## Files
- `FocusActivity.kt` — full-screen popup
- `MonitorService.kt` — background watcher (no accessibility)
- `UsageHelper.kt` — usage stats, permissions, streak
- `MainActivity.kt` — setup / settings screen
- `UiKit.kt` — gradient background, countdown button, helpers
