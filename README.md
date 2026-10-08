# Route SMS (club.ithueti.routesms)

Минимальное Android-приложение, которое:
- принимает входящие SMS;
- по SIM-мэппингу пересылает их в Telegram-бота (формат: «От: <номер>\n<текст>»);
- выполняет health-check каждые 12 часов и, если есть проблемы, присылает предупреждение в чат: «⚠️ Проверь телефон с приложением».

## Требования
- Android Studio (Arctic/Koala или новее) с установленным Android SDK 35 (Android 15). Если у вас установлен SDK 34 — просто поменяйте `compileSdk`/`targetSdk` на 34 в `app/build.gradle.kts`.
- JDK 17 (Android Gradle Plugin 8.5.x).
- Телефон с Android 7.0+ (minSdk=24).

## Сборка APK в Android Studio
1. Откройте папку проекта `RouteSMS` в Android Studio.
2. Дождитесь синхронизации Gradle.
3. Соберите APK: **Build > Build Bundle(s)/APK(s) > Build APK(s)**.
4. Готовый APK появится в `app/build/outputs/apk/debug/app-debug.apk` (для debug-сборки).

## Сборка из командной строки
Если у вас установлен `gradle` (8.x) и настроены `ANDROID_HOME`/SDK пути:
```bash
cd RouteSMS
gradle assembleDebug
```
APK будет в `app/build/outputs/apk/debug/app-debug.apk`.

> Примечание: проект не содержит gradle-wrapper. Android Studio может автоматически добавить его при первой синхронизации (или используйте свою установку Gradle).

## Настройка в приложении
1. Запустите приложение на телефоне.
2. Нажмите **Request SMS permission** и выдайте разрешение на получение SMS.
3. Добавьте маппинги:
   - Ключи вида `mapping_sub_<ID>` (например, `mapping_sub_1`) для конкретной SIM по `subscriptionId`.
   - Или общий `mapping_default` — используется, если `subscriptionId` не удалось определить.
4. Введите **Bot Token** и **Chat ID** для каждого ключа и нажмите **Save mapping**.
5. Кнопкой **Run health check now** можно вручную запустить проверку.

### Как узнать subscriptionId
На большинстве устройств для первой SIM это `1`, для второй — `2`. Если нужно точно:
- отправьте тестовую SMS на каждую SIM;
- временно добавьте `mapping_default` для отладки;
- при необходимости можно добавить логирование extras в `SmsReceiver` (ключи: `subscription`, `subscription_id`, `sub_id`, `android.telephony.extra.SUBSCRIPTION_ID`, и т.д.).

## Формат пересылаемого сообщения
```
От: +71234567890
Текст сообщения...
```

## Безопасность
- Токены хранятся в `SharedPreferences`. Для продакшна используйте `EncryptedSharedPreferences`.
- Проект не отправляет ничего, кроме ваших SMS (по событиям `SMS_RECEIVED`).

## Лицензия
MIT
