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
