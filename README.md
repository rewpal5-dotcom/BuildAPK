# BuildAPK — Android APK

**FreeWebToApk** (https://freewebtoapk.com/generator/website-url) ka native Android shell — website ke ads block karke, apne ads bina.

**Direct APK download:** https://github.com/rewpal5-dotcom/BuildAPK/releases/latest/download/app-debug.apk

## Kya hai isme

- **Ad blocker** — site ke ad/tracker networks request level par block, ad frames aur ad containers hide, pop-up windows band
- **Koi apna ad nahi** — is build mein koi ad SDK nahi hai (Start.io bhi nahi)
- **Progress bar** — page load hote waqt upar patli bar
- **Back button** — page history mein peeche jaata hai
- **Offline screen** — internet na ho to "Internet nahi mila" + Retry
- **Zoom lock** — pinch zoom, double-tap zoom aur left-right panning band; page device width par fix
- **Edge-to-edge safe** — Android 15 ke status/navigation bar ke neeche content nahi jaata
- **External links** — doosri site ke link phone ke browser mein khulte hain
- **Downloads** — Android DownloadManager se Downloads folder mein
- Sirf `INTERNET` aur `ACCESS_NETWORK_STATE` permission, koi tracker nahi

## Ad blocking kaise kaam karta hai

`MainActivity.java` mein `AD_HOSTS` list hai — request level par `shouldInterceptRequest` se block hoti hai.
`hideAdSlots()` page load hone ke baad `ins.adsbygoogle`, ad iframes aur ad containers ko CSS se hide karta hai,
aur `window.open` ko null kar deta hai taaki pop-under na khule.

Naya ad network block karna ho to bas `AD_HOSTS` mein domain add kar dein.

## Build

GitHub Actions (`.github/workflows/build-apk.yml`) — har build release mein APK attach karti hai.

```bash
./gradlew assembleDebug
```

## Tech

Java · minSdk 24 · targetSdk 35 · compileSdk 36 · AGP 8.9.3 · Gradle 8.11.1 · koi third-party SDK nahi

## License

MIT
