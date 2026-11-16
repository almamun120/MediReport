# MediReport — রোগী রিপোর্ট পোর্টাল (Android, Kotlin)

PHP ওয়েব পোর্টালের নেটিভ অ্যান্ড্রয়েড রূপ। Kotlin + Jetpack Compose (Material 3) + Room, সম্পূর্ণ অফলাইন।

## ফিচার
**অ্যাডমিন:** পিন-সুরক্ষিত লগইন (PBKDF2 হ্যাশ, ৫ বার ভুলে ৩০ সেকেন্ড লক) · ড্যাশবোর্ড (পরিসংখ্যান, ৭ দিনের চার্ট) · রোগী CRUD, সার্চ ও সর্ট, স্বয়ংক্রিয় ID · PDF/ছবি আপলোড, দেখা, শেয়ার, ডিলিট · HTML টেমপ্লেট এডিটর (প্লেসহোল্ডার `{{...}}` অটো-ডিটেক্ট, প্রিভিউ) · টেমপ্লেট থেকে A4 PDF তৈরি (প্রতি পাতায় হেডার/ফুটার/ওয়াটারমার্ক) · পেজ সেটআপ ও PDF প্রিভিউ · ব্যাকআপ/রিস্টোর (zip) · পাসওয়ার্ড পরিবর্তন।
**রোগী:** Patient ID + ফোন দিয়ে লগইন · নিজের তথ্য ও রিপোর্ট দেখা/শেয়ার।
**সাধারণ:** লাইট/ডার্ক থিম · বাংলা/ইংরেজি ভাষা · ফাইল প্রাইভেট স্টোরেজে (FileProvider)।

ডিফল্ট অ্যাডমিন: `admin` / `admin123` — প্রথমেই সেটিংসে পরিবর্তন করুন।

## GitHub-এ অটো বিল্ড
`.github/workflows/android.yml` প্রতিটি push / PR-এ চলে:
- **debug** APK → Actions → Artifacts
- **signed release** APK (`apksigner verify` সহ) → Artifacts
- `v1.2.3` ট্যাগ push করলে GitHub Release-এ APK যুক্ত হয়

### স্থায়ী সাইনিং কী (গুরুত্বপূর্ণ)
কী না দিলে প্রতিবার অস্থায়ী কী ব্যবহৃত হয় (আপডেট ইনস্টল হবে না)। একবার বানান:
```bash
keytool -genkeypair -v -keystore release.jks -alias medireport -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks   # macOS: base64 -i release.jks
```
Repo → Settings → Secrets and variables → Actions-এ যোগ করুন: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`। `.jks` ফাইল কখনো commit করবেন না।

## লোকাল বিল্ড
Android Studio (Ladybug+) দিয়ে খুলুন, অথবা Gradle 8.9 + JDK 17: `gradle assembleDebug`।
(Gradle wrapper jar সংযুক্ত নেই; চাইলে `gradle wrapper --gradle-version 8.9` চালান।)
