# FreedomChat (Freedom Messenger)

FreedomChat is a modern, real-time messaging application designed for seamless communication. Built with native Android technologies and a powerful backend-as-a-service, it provides a fast and secure chatting experience.

## App Description
FreedomChat leverages the latest Android development practices to provide a high-performance messaging platform. The app features a real-time chat interface where users can exchange messages instantly, manage their profiles, and stay notified of new activities.

### Key Features
- **Real-time Messaging**: Instant message delivery and synchronization using Supabase Realtime.
- **Secure Authentication**: Robust user authentication and session management via Supabase Auth.
- **Media Support**: Ability to share and view images, powered by Supabase Storage and Coil.
- **Push Notifications**: Stay updated even when the app is in the background with Firebase Cloud Messaging (FCM) integration.
- **Local Persistence**: Offline access to your message history using Room Database.
- **Modern Material 3 UI**: A beautiful, responsive user interface built entirely with Jetpack Compose.

## Technologies Used
- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
- **Backend**: [Supabase](https://supabase.com/)
    - PostgREST (Database API)
    - Realtime (WebSockets)
    - Auth (Authentication)
    - Storage (File Management)
- **Notifications**: [Firebase Cloud Messaging (FCM)](https://firebase.google.com/docs/cloud-messaging)
- **Local Database**: [Room](https://developer.android.com/training/data-storage/room)
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Serialization**: [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Architecture**: MVVM (Model-View-ViewModel) with Clean Architecture principles.

## Getting Started

### Prerequisites
- [Android Studio Jellyfish](https://developer.android.com/studio) or newer.
- A Supabase project.
- A Firebase project (for notifications).

### Setup
1. Clone the repository.
2. Create a `.env` file in the root directory (refer to `.env.example`).
3. Add your Supabase URL and API Key to the `.env` file.
4. Add your `google-services.json` to the `app/` directory.
5. Sync the project with Gradle.
6. Run the app on an emulator or physical device.

---
Developed as a showcase of modern Android development with Supabase.
