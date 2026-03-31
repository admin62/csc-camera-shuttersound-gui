# 📸 Samsung Camera Shutter Sound Disabler (GUI)

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux-lightgrey?style=for-the-badge)

A user-friendly GUI tool to safely and easily disable the camera shutter sound on Samsung Galaxy smartphones via ADB (Android Debug Bridge). Designed to let anyone change the setting with just a few clicks, without needing to type complex terminal commands.

---

## ✨ Key Features

*   **Intuitive GUI:** A clean Java Swing-based interface that provides real-time progress updates and logs.
*   **Smart Device Detection:** Automatically identifies Samsung devices connected via USB to prevent accidental configuration of other devices.
*   **Multilingual Support:** Supports English (Default), Korean, and Japanese, with instant language switching at runtime.
*   **Cross-Platform:** Supports both Windows and Linux environments.
*   **Fully Automated:** No need to install ADB tools separately; the program automatically extracts and uses integrated ADB resources.

## 🚀 How to Use

### 1. Prepare Your Smartphone
1.  Go to **Settings > About phone > Software information**.
2.  Tap **Build number** 7 times to enable 'Developer mode'.
3.  Go to **Settings > Developer options** and enable **USB debugging**.

### 2. Connect to PC and Run
1.  Connect your smartphone to your PC via a USB cable.
2.  Run the **AdbShutterSoundGUI.jar** file directly.
3.  When the program window appears, read the disclaimer/notice and click **I Agree**.
4.  The tool will automatically scan for your device and disable the shutter sound setting.
5.  Completion is confirmed when you see the `Success` message in the log window.

## 🛠 Tech Stack

*   **Language:** Java 8 or higher (JRE recommended)
*   **UI Framework:** Java Swing (Cross-platform GUI)
*   **Background Processing:** `SwingWorker` for asynchronous tasks and real-time log updates.
*   **Communication:** Integrated ADB (Android Debug Bridge) binaries.

## ⚖️ License

This project is licensed under the **Apache License 2.0**.

*   **ADB Binaries:** The tools included in the `adb-windows/` and `adb-linux/` folders are part of the [Google Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools). Copyright and license notices for these tools can be found in the `NOTICE.txt` file within each folder.
*   **Icons and Resources:** Donation-related images included in the project are proprietary resources of the creator.

## ☕ Support the Project

If this tool helped you, please consider buying the developer a coffee via the **Donate** button at the bottom of the program! Your support helps keep this tool updated.