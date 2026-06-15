<div align="center">

# AppTruyen

**Ứng dụng Đọc & Nghe Truyện Android đa nguồn chuẩn Material 3**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=android)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=26)

<img src="build/screenshot.png" height="500" alt="AppTruyen Screenshot"/>

</div>

AppTruyen là một ứng dụng Android mã nguồn mở được phát triển cá nhân nhằm mang lại trải nghiệm đọc và nghe truyện chữ (Text-To-Speech) tối ưu, giao diện sạch sẽ và hoàn toàn không có quảng cáo. 

---

## 📑 Mục lục
- [Tính năng nổi bật](#-tính-năng-nổi-bật)
- [Tech Stack](#-tech-stack)
- [Yêu cầu hệ thống](#-yêu-cầu-hệ-thống)
- [Cài đặt & Build](#-cài-đặt--build)
- [Tài liệu dự án](#-tài-liệu-dự-án)
- [Đóng góp](#-đóng-góp)
- [Giấy phép](#-giấy-phép)

## ✨ Tính năng nổi bật

- 📚 **Đa nguồn & Smart Dedup**: Tự động gộp dữ liệu từ 4 nguồn phổ biến (TruyenCom, Tàng Thư Viện, SsTruyện, TruyenFull) giúp bạn luôn có chương mới nhất và đầy đủ nhất.
- 🔊 **Nghe truyện (Text-To-Speech)**: Đọc truyện bằng giọng nói tiếng Việt với bộ tiền xử lý từ ngữ thông minh, có thể chỉnh tốc độ, cao độ, và nghe khi tắt màn hình qua Foreground Service.
- 📥 **Tải truyện Offline**: Cơ chế tải nền mạnh mẽ, lưu trữ toàn bộ nội dung xuống Database nội bộ (Room) để đọc không cần mạng.
- ⚙️ **Cá nhân hóa trải nghiệm đọc**:
  - Tự động dọn dẹp và định dạng văn bản (Auto Format).
  - Tự động cuộn trang (Auto-scroll) với tốc độ tùy chỉnh.
  - Tính năng "Thay chữ" thông minh (áp dụng toàn cục hoặc cho từng truyện riêng biệt).
  - Tùy biến giao diện: 3 chủ đề (Sáng/Tối/Sepia), 4 font chữ tích hợp sẵn, thay đổi cỡ chữ/khoảng cách dòng linh hoạt.
- 📊 **Thống kê cá nhân**: Biểu đồ trực quan theo dõi lịch sử đọc, thời gian đọc, chuỗi ngày đọc (Streak).
- 💾 **Sao lưu & Khôi phục**: Xuất toàn bộ tiến trình và cài đặt ra tệp ZIP an toàn thông qua Storage Access Framework (SAF).

## 🛠 Tech Stack

Dự án được xây dựng dựa trên các tiêu chuẩn hiện đại nhất của Android (Modern Android Development):

| Thành phần | Công nghệ sử dụng |
|---|---|
| **Ngôn ngữ** | Kotlin |
| **Giao diện (UI)** | Jetpack Compose + Material 3 Window Size Class |
| **Điều hướng** | Compose Navigation |
| **Kiến trúc** | Clean Architecture + MVVM (MVI pattern tại ReaderScreen) |
| **Dependency Injection** | Dagger Hilt |
| **Concurrency** | Coroutines + StateFlow / SharedFlow |
| **Network & Crawl** | OkHttp3 + Jsoup |
| **Lưu trữ nội bộ** | Room Database (SQLite) + Preferences DataStore |
| **Tối ưu hiệu năng** | Baseline Profiles, LeakCanary (Debug mode), Immutable Collections |

## 📋 Yêu cầu hệ thống

Để build và chạy dự án này trên môi trường phát triển cá nhân, bạn cần:
- **Android Studio**: Phiên bản mới nhất (Iguana trở lên khuyến nghị)
- **JDK**: Java 17
- **Android SDK**: Build Tools & API level 36 (Minimum SDK: 26)

## 🚀 Cài đặt & Build

1. Clone repository về máy:
```bash
git clone https://github.com/your-username/AppTruyen.git
cd AppTruyen
```

2. Chạy ứng dụng (môi trường Debug):
```powershell
.\gradlew.bat assembleDebug
```

3. Build bản Release (Lưu ý: Bạn cần cấu hình tệp `keystore.properties` hợp lệ):
```powershell
.\gradlew.bat assembleRelease
```

## 📖 Tài liệu dự án

Hệ thống tài liệu cho nhà phát triển được đặt trong thư mục [`docs/`](docs/):

- 📐 [Kiến trúc tổng quan (Architecture)](docs/architecture.md)
- 📈 [Tiến độ phát triển (Progress)](docs/progress.md)
- 📝 [Lịch sử cập nhật (Changelog)](docs/changelog.md)
- 🕸️ [Hướng dẫn hệ thống Scraping (Scraping Guide)](docs/scraping.md)

## 🤝 Đóng góp

Mọi đóng góp (Báo lỗi, Yêu cầu tính năng, hay Pull Request) đều luôn được chào đón! Vui lòng đọc kỹ [Hướng dẫn đóng góp (Contributing)](docs/contributing.md) trước khi tạo PR.

Dự án này ứng dụng tự động hóa bằng AI Agent. Nếu bạn dùng AI Agent (như Cline, Cursor, Antigravity) để viết code, vui lòng tuân thủ các quy tắc trong [`.agent/rules/`](.agent/rules/).

## 📄 Giấy phép

Dự án này là ứng dụng cá nhân và mã nguồn mở. Hiện tại dự án chưa đính kèm file LICENSE cụ thể, mọi quyền sử dụng và chia sẻ được giữ lại cho mục đích giáo dục và phi thương mại (Non-Commercial).
