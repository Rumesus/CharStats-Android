# CharStats Android

Мобільна версія програми підрахунку символів.

Функції:
- TXT, DOC, DOCX, XLS, XLSX, PDF, JPG/JPEG/PNG/BMP/WEBP/TIF/TIFF.
- Старий `.doc` автоматично читається через Apache POI HWPF і перетворюється в текст для підрахунку — Microsoft Word на телефоні не потрібен.
- OCR `ukr + rus + eng` через Tesseract 5.5.1.
- Для фото враховується EXIF; для OCR програма автоматично пробує 0/90/270/180° та вибирає найкращий результат.
- PDF: текстовий шар + OCR сканів; є режим OCR усіх сторінок.
- Розрахунок `+15%`, сторінки `/1800`, тариф і вартість.
- Excel з тими самими 6 колонками, сортування `РОБОТА → ЗАМОВЛЕННЯ → Файл`, числа з 2 знаками.
- PDF для клієнта.
- `Поділитися PDF` використовує системне Android-меню — Telegram/Viber/Gmail отримують саме PDF-файл.

## APK через GitHub Actions
Після завантаження цього проєкту в GitHub відкрийте `Actions → Build APK → Run workflow`.
Готовий APK буде в артефакті `CharStats-Android-APK`.

## Локальна збірка
Потрібні JDK 17, Android SDK 35 і Gradle 8.14.2:
`gradle :app:assembleDebug`

APK:
`app/build/outputs/apk/debug/app-debug.apk`
