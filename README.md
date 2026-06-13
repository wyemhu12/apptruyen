# AppTruyen — Đọc Truyện Android App

Ứng dụng Android cá nhân để đọc truyện online từ truyencom.com, với tính năng TTS tiếng Việt và tải offline.

## Tính năng

- 🔍 Tìm kiếm truyện
- 📖 Đọc chương online/offline
- 🔊 Nghe truyện bằng TTS giọng Việt Nam
- 📥 Tải truyện để đọc offline
- 🎨 Giao diện sáng/tối/sepia
- 📱 UI 100% tiếng Việt

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation |
| HTTP | OkHttp3 + Jsoup |
| Database | Room (SQLite) |
| DI | Hilt |
| Async | Coroutines + Flow |
| TTS | Android TextToSpeech |
| Architecture | MVVM + Clean Architecture |

## Build

```bash
# Yêu cầu: Android SDK, JDK 17
./gradlew assembleDebug
```

## Tài liệu

Xem thêm trong thư mục [`docs/`](docs/):

- [Kiến trúc & Cấu trúc](docs/architecture.md)
- [Tiến độ phát triển](docs/progress.md)
- [Changelog](docs/changelog.md)
- [Scraping Guide](docs/scraping.md)
- [Hướng dẫn đóng góp](docs/contributing.md)

## Rules cho AI Agent

Xem [`.agent/rules/`](.agent/rules/) — tự động cập nhật docs khi có thay đổi.
