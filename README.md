# SysOverlay — Wi-Fi системный оверлей для Android 11+

Плавающий виджет поверх всех приложений, показывает:
- 📶 Имя Wi-Fi сети (SSID)
- 📡 Диапазон (2.4 / 5 / 6 GHz) + частота в MHz
- 🌐 IP-адрес устройства
- ⚡ Текущая скорость соединения (Mbps)
- 🚀 Максимальная заявленная скорость сети (Mbps)
- ⏱ Аптайм устройства

---

## Сборка APK

### Способ 1 — Android Studio (рекомендуется)
1. Открыть папку `SysOverlay` как проект в Android Studio
2. `Build → Build Bundle(s) / APK(s) → Build APK(s)`
3. APK будет в `app/build/outputs/apk/debug/app-debug.apk`

### Способ 2 — командная строка (Linux/macOS)
```bash
# Убедиться что установлены: JDK 17+, Android SDK (ANDROID_HOME)
cd SysOverlay
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Способ 3 — Windows
```cmd
cd SysOverlay
gradlew.bat assembleDebug
```

---

## Установка на телефон

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Или скопировать APK на телефон и установить вручную (нужно разрешить установку из неизвестных источников).

---

## Разрешения при первом запуске

1. **Поверх других приложений** — обязательно, для оверлея
2. **Геолокация** — требуется Android для чтения SSID (имени сети). Данные не покидают устройство.

---

## Примечания

- `getMaxSupportedTxLinkSpeedMbps()` доступен с Android 10 (API 29) — на Android 11 работает
- SSID без геолокации возвращает `<unknown ssid>` — это ограничение Android 10+, не баг
- Оверлей работает через foreground service, не убивается системой
