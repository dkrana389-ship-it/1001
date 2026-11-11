# Reel Counter v2

Instagram Reels ki live ginti, camera cutout ke paas chhote black pill mein.

## Permissions
Koi `uses-permission` nahi hai: na internet, na "draw over other apps", na notification.
Sirf Accessibility service (user khud ON karta hai). Isse Play Protect ka risk kam rehta hai.

## Build
GitHub Actions `assembleRelease` chalata hai aur stable key se signed `app-release.apk` banata hai.
Key: `keystore/release.jks` (alias `reelcounter`, password `reelcounter123`).
Repository PRIVATE rakho. Play Store par daalte waqt Play App Signing use karo aur apni nayi upload key banao.
