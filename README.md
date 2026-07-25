# 🛡️ CareCircle

> AI-Powered Parental Control System built with Kotlin, XML, Firebase, CameraX, ML Kit and WebRTC.

CareCircle is a modern parental control solution consisting of separate Parent and Child Android applications. The system enables parents to monitor children's device usage, manage screen time, receive real-time alerts, and encourage healthier digital habits through AI-powered computer vision and secure communication.

---

# ✨ Features

## 👨‍👩‍👧 Parent Application

- 📊 Parent Dashboard
- 📱 Live Child Device Monitoring
- ⏱️ Screen Time Management
- 📈 App Usage Analytics
- 🔒 Remote Screen Lock
- 🔔 Real-time Notifications
- 👥 Parent-Child Device Pairing

---

## 👦 Child Application

- 📏 Face Distance Monitoring
- 👀 Eye Blink Detection
- 📲 App Usage Tracking
- 🛡️ Accessibility Service Monitoring
- 🎥 WebRTC Screen Monitoring
- ⚡ Background Monitoring Service
- 🔒 Screen Time Enforcement

---

## 🔥 Firebase Integration

- Firebase Authentication
- Cloud Firestore
- Firebase Cloud Messaging (FCM)

---

# 🎥 Demo

<p align="center">
  <img src="demo/demo.gif" width="900" alt="CareCircle Demo"/>
</p>

---

# 📸 Screenshots

<!-- Add the same HTML table style used in your previous repositories -->

---

# 🏗 System Architecture

```text
                   Parent App
                        │
                        ▼
             Firebase Authentication
                        │
                        ▼
               Cloud Firestore
                        ▲
                        │
                   Child App
                        │
       Accessibility Service + CameraX
                        │
                        ▼
           ML Kit Face Detection
                        │
                        ▼
              WebRTC Screen Monitoring
```

---

# ⚙️ Tech Stack

## Android

- Kotlin
- XML
- Material Design 3
- Navigation 
- Coroutines
- Retrofit

## Firebase

- Firebase Authentication
- Cloud Firestore
- Firebase Cloud Messaging

## AI & Computer Vision

- CameraX
- ML Kit Face Detection
- Eye Blink Detection
- Face Distance Monitoring

## Communication

- WebRTC
- Node.js (Backend Architecture)

---

---

# 📱 Repository Contents

| Module | Description |
|---------|-------------|
| 👨‍👩‍👧 Parent App | Dashboard for parents to monitor and manage child devices. |
| 👦 Child App | Installed on the child's device for monitoring, AI detection, and communication. |

---


# 👨‍💻 Developer

**Muhammad Usama Ali**

Android Developer | AI-Powered Mobile Applications

📧 Email: usama.priv@gmail.com

💼 LinkedIn: https://www.linkedin.com/in/usama-ali-66b963320

⭐ If you found this project useful, consider giving it a star!
