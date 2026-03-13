# OfflineSync - Справка по проекту

## Общее описание
Приложение для синхронизации файлов между Android и Ubuntu через локальную сеть (WiFi) без облака.

## Структура проекта

```
android_app/
├── app/
│   ├── src/main/java/com/offlinesync/
│   │   ├── service/
│   │   │   ├── WebServer.kt      # HTTP сервер на Ktor (порт 8080)
│   │   │   ├── MDNSService.kt    # Обнаружение устройств (mDNS)
│   │   │   └── SyncService.kt    # Foreground service
│   │   └── presentation/         # Compose UI
│   └── build/outputs/apk/debug/  # app-debug.apk
├── offline-sync-gui.py           # GUI клиент для Ubuntu (Tkinter)
├── offline-sync-ubuntu.py         # CLI клиент для Ubuntu
└── offline_file_sync_plan.md      # План разработки
```

## Использование

### Android
1. Установить `app-debug.apk` на устройство
2. Запустить приложение
3. Предоставить разрешения (хранилище, сеть)

### Ubuntu
```bash
# Установка зависимостей
pip install requests zeroconf

# Запуск GUI
python3 offline-sync-gui.py

# CLI
python3 offline-sync-ubuntu.py ~/SyncFolder --discover
python3 offline-sync-ubuntu.py ~/SyncFolder --ip 192.168.x.x --sync
```

## API Endpoints (Android)

| Endpoint | Описание |
|----------|----------|
| `GET /` | Проверка работы сервера |
| `GET /ping` | Пинг |
| `POST /upload` | Загрузка файла |
| `GET /download/{filename}` | Скачивание файла |
| `GET /files` | Список файлов |

## Текущий статус
- ✅ Загрузка файлов Android → Ubuntu
- ✅ Скачивание файлов Ubuntu → Android
- ✅ Обнаружение устройств mDNS
- ⏳ Двусторонняя синхронизация (базовая)
- ⏳ Авто-синхронизация
- ⏳ Desktop приложение (полноценное)

## Известные проблемы
- Файлы сохраняются в `/storage/emulated/0/Download/OfflineSync/`
- Требуется Android 8+ (API 26+)
- IP устройства меняется при переподключении к WiFi

## Следующие шаги
1. Улучшить UI Android приложения
2. Добавить выбор папки синхронизации в Android
3. Реализовать авто-синхронизацию
4. Создать Desktop приложение на Electron

---

# OfflineSync - Project README

## Overview
OfflineSync is an Android application designed for seamless, local network file synchronization between Android devices and desktop computers (initially Ubuntu, with future plans for broader support). It aims to provide a user-friendly alternative to more complex tools like Syncthing, focusing on a quick and easy setup. The synchronization happens over a local WiFi network, eliminating the need for cloud services.

## Project Structure

```
android_app/
├── app/
│   ├── src/main/java/com/offlinesync/
│   │   ├── service/
│   │   │   ├── WebServer.kt      # Ktor HTTP Server (port 8080) for file operations
│   │   │   ├── MDNSService.kt    # mDNS (Bonjour) for device discovery
│   │   │   └── SyncService.kt    # Foreground service for background sync tasks
│   │   └── presentation/         # Jetpack Compose UI
│   └── build/outputs/apk/debug/  # Output directory for app-debug.apk
├── offline-sync-gui.py           # GUI client for Ubuntu (Tkinter)
├── offline-sync-ubuntu.py         # CLI client for Ubuntu
└── offline_file_sync_plan.md      # Detailed development plan and vision
```

## Technologies Used
The Android application is built with modern Android technologies:
-   **Language:** Kotlin with Coroutines.
-   **UI:** Jetpack Compose for a declarative and modern user interface.
-   **Networking:** Ktor HTTP server to expose a REST API for file operations.
-   **Discovery:** jmdns for mDNS (Bonjour) service discovery, enabling automatic detection of devices on the local network.
-   **Background Operations:** WorkManager to handle reliable background synchronization tasks, likely with a Foreground Service for uninterrupted operation.
-   **Data Persistence:** Room database, likely used for storing file metadata, sync history, or device information.
-   **Dependency Injection:** Hilt for managing application dependencies.

The desktop client is currently implemented in Python, utilizing `requests` for HTTP communication and `zeroconf` for mDNS discovery.

## Usage

### Android App
1.  Install the `app-debug.apk` onto your Android device.
2.  Launch the application.
3.  Grant necessary permissions (storage, network access).

### Ubuntu Desktop Client
1.  **Install dependencies:**
    ```bash
    pip install requests zeroconf
    ```
2.  **Run the GUI client:**
    ```bash
    python3 offline-sync-gui.py
    ```
3.  **Run the CLI client:**
    -   **Discover devices and initiate sync:**
        ```bash
        python3 offline-sync-ubuntu.py ~/SyncFolder --discover
        ```
    -   **Sync with a known IP address:**
        ```bash
        python3 offline-sync-ubuntu.py ~/SyncFolder --ip 192.168.x.x --sync
        ```

## API Endpoints (Android Application)

The Android application exposes the following REST API endpoints via its Ktor server:

| Endpoint                 | Description                               |
| :----------------------- | :---------------------------------------- |
| `GET /`                  | Server health check                       |
| `GET /ping`              | Ping endpoint                             |
| `POST /upload`           | Upload a file to the Android device       |
| `GET /download/{filename}` | Download a file from the Android device   |
| `GET /files`             | List files available on the Android device |

## Current Status
-   ✅ File upload: Android → Desktop
-   ✅ File download: Desktop → Android
-   ✅ mDNS device discovery
-   ⏳ Basic bidirectional synchronization
-   ⏳ Automatic synchronization
-   ⏳ Full-featured Desktop application

## Known Issues
-   Files are currently saved to `/storage/emulated/0/Download/OfflineSync/` on the Android device.
-   Requires Android 8.0 (API 26) or higher.
-   Device IP address may change upon WiFi reconnection, requiring re-discovery or manual IP update for clients.

## Next Steps
1.  Improve Android application UI/UX.
2.  Add a feature to select the synchronization folder within the Android app.
3.  Implement robust automatic synchronization.
4.  Develop a cross-platform Desktop application (e.g., using Electron).
