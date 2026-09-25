# Firebase Cloud Messaging (FCM) Setup Guide

## 1. Requirement
To deliver incoming call rings and real-time message alerts when the Chatooz Android app is completely killed or in Doze mode, Google FCM is mandatory.

## 2. Setup Steps

### Step 1: Firebase Project Registration
1. Go to [Firebase Console](https://console.firebase.google.com/).
2. Create a project named `Chatooz`.
3. Add an Android App with package name: `com.chatooz.app`.
4. Download `google-services.json` and place it in the `app/` folder:
   `app/google-services.json`.

### Step 2: Android Manifest & Service
1. Add `com.google.gms.google-services` plugin to `app/build.gradle.kts`.
2. Implement `ChatoozFirebaseMessagingService` extending `FirebaseMessagingService`.
3. Handle `onNewToken(token: String)`: Send token to server via `POST /users/fcm-token`.
4. Handle `onMessageReceived(remoteMessage: RemoteMessage)`:
   - For `call.incoming`: Trigger high-priority Full-Screen Intent notification with Ringtone.
   - For `message.new`: Trigger NotificationManager with Direct Reply.

### Step 3: Server-side Dispatcher
1. In `chatooz_online_server.py` or cloud backend, use `firebase-admin` Python SDK.
2. When `POST /messages` or `POST /call/signal` is received and recipient is offline, send FCM Data Message with `priority: "high"`.
