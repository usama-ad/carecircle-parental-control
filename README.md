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
  <img src="demo/demo.gif" width="320" alt="CareCircle Demo"/>
</p>

---

# 📸 Screenshots


<h2 align="center">📸 Screenshots</h2>

<table align="center">

<tr>
<td align="center">
<img src="https://github.com/user-attachments/assets/f77f4f18-35ea-4a9d-b7df-6e6a1d377f6c" width="250"><br>
<b>Parent Login</b>
</td>

<td align="center">
<img src="https://github.com/user-attachments/assets/0e579ed2-3b80-4db2-a1ef-3f3f6f8916b2" width="250"><br>
<b>Child Signup</b>
</td>

<td align="center">
<img src="https://github.com/user-attachments/assets/89042ded-98a6-4c11-9321-4ac36954aedc" width="250"><br>
<b>Parent Dashboard</b>
</td>
</tr>

<tr>
<td align="center">
<img src="https://github.com/user-attachments/assets/9f2aca32-4e38-432d-a601-4456d481a854" width="250"><br>
<b>Child Usage Analytics</b>
</td>

<td align="center">
<img src="https://github.com/user-attachments/assets/365fa62e-5892-40a7-8acd-9542e96b6d5a" width="250"><br>
<b>Parent Alerts</b>
</td>

<td align="center">
<img src="https://github.com/user-attachments/assets/a19d03fe-85ad-4b6d-9b1a-ea6d6ae24306" width="250"><br>
<b>Restricted Applications</b>
</td>
</tr>

<tr>
<td align="center">
<img src="https://github.com/user-attachments/assets/934432f9-068a-4ff0-9191-29bfb576f7a8" width="250"><br>
<b>Parent Settings</b>
</td>

<td align="center">
<img src="https://github.com/user-attachments/assets/131593f6-266a-434a-8841-7f77c6373aba" width="250"><br>
<b>Device Settings</b>
</td>

<td align="center">
<img src="https://github.com/user-attachments/assets/d98e4049-0bb8-4aab-b290-81621be126bb" width="250"><br>
<b>Child Settings</b>
</td>
</tr>

<tr>
<td align="center">
<img src="https://github.com/user-attachments/assets/1632c9df-4595-4a81-9f1a-00b039f1f45c" width="250"><br>
<b>Face Distance Warning</b>
</td>

<td align="center">
<img src="https://github.com/user-attachments/assets/3f1aefaa-c804-4b9d-9800-6a1e604fdab5" width="250"><br>
<b>Eye Blink Warning</b>
</td>

<td align="center">
<img src="https://github.com/user-attachments/assets/b6ad7179-2788-4fb4-9ea3-fbac3758a17a" width="250"><br>
<b>Child Home</b>
</td>
</tr>

</table>


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
