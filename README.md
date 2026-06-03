<div align="center">
  <h1>🕊️ FreedomChat</h1>
  <p><strong>A Modern & Fast Real-Time Android Messaging Experience</strong></p>
  
  <p>
    <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
    <img src="https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpack-compose&logoColor=white" alt="Jetpack Compose" />
    <img src="https://img.shields.io/badge/Supabase-3ECF8E?style=for-the-badge&logo=supabase&logoColor=white" alt="Supabase" />
    <img src="https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" alt="Firebase" />
  </p>
</div>

<br />

> **FreedomChat** (Freedom Messenger) adalah aplikasi *messaging* modern yang dibangun secara *native* untuk perangkat Android. Aplikasi ini menawarkan pengalaman *chatting* yang cepat, mulus, dan aman berkat integrasi dengan platform Backend-as-a-Service yang *powerful*.

---

## ✨ Fitur Unggulan

- ⚡ **Real-time Messaging:** Kirim dan terima pesan secara instan tanpa jeda menggunakan teknologi *WebSockets* dari Supabase Realtime.
- 🔒 **Secure Authentication:** Sistem autentikasi pengguna yang aman dan manajemen sesi yang andal ditenagai oleh Supabase Auth.
- 🖼️ **Media Sharing Support:** Bagikan momen lewat gambar dengan mudah. Menggunakan Supabase Storage dan dirender secara optimal oleh library Coil.
- 🔔 **Smart Push Notifications:** Tetap *up-to-date* dengan pesan baru berkat integrasi Firebase Cloud Messaging (FCM), bahkan saat aplikasi berjalan di latar belakang (*background*).
- 🗃️ **Local Persistence (Offline-ready):** Riwayat *chat* tersimpan dengan aman secara lokal menggunakan Room Database, sehingga tetap bisa diakses meski tanpa koneksi internet.
- 🎨 **Modern Material 3 UI:** Antarmuka yang indah, dinamis, dan responsif, dibangun sepenuhnya menggunakan **Jetpack Compose**.

---

## 🛠️ Tech Stack & Architecture

Proyek ini mendemonstrasikan implementasi praktik terbaik (*best practices*) dalam ekosistem Android modern:

### 📱 Android Frontend
- **Bahasa:** [Kotlin](https://kotlinlang.org/)
- **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material 3
- **Arsitektur:** **MVVM** (Model-View-ViewModel) dengan prinsip **Clean Architecture**
- **Local Database:** [Room](https://developer.android.com/training/data-storage/room)
- **Networking:** [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Data Parsing:** [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- **Image Loading:** [Coil](https://coil-kt.github.io/coil/)

### ☁️ Backend & Services
- **Backend Infrastructure:** [Supabase](https://supabase.com/) (PostgREST, Realtime, Auth, Storage)
- **Push Notification:** [Firebase Cloud Messaging (FCM)](https://firebase.google.com/docs/cloud-messaging)

---

## 🚀 Panduan Instalasi (Getting Started)

Ingin mencoba menjalankan aplikasi ini di komputermu? Ikuti langkah-langkah simpel berikut:

### Prasyarat
- [Android Studio Jellyfish](https://developer.android.com/studio) atau versi yang lebih baru.
- Akun & *Project* [Supabase](https://supabase.com/).
- Akun & *Project* [Firebase](https://firebase.google.com/) (untuk mengaktifkan fitur notifikasi).

### Langkah-langkah Konfigurasi

1. **Clone repositori ini:**
   ```bash
   git clone https://github.com/ekosanto2008/FreedomChat.git
   ```
2. Buka folder proyek di **Android Studio**.
3. Buat file bernama `.env` di direktori *root* proyek (kamu bisa menggunakan file `.env.example` sebagai referensi).
4. Tambahkan URL dan API Key Supabase milikmu ke dalam file `.env`:
   ```properties
   SUPABASE_URL="url_supabase_kamu"
   SUPABASE_ANON_KEY="anon_key_supabase_kamu"
   ```
5. Unduh konfigurasi `google-services.json` dari konsol Firebase dan letakkan di dalam direktori `app/`.
6. Lakukan proses **Sync Project with Gradle Files**.
7. ▶️ Jalankan aplikasi di Emulator atau *device* fisik kamu!

---
<div align="center">
  <p><i>Developed as a showcase of modern Android development with Supabase.</i></p>
</div>
