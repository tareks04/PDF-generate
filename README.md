# Smart Page Library (স্মার্ট পেজ লাইব্রেরি)

An Android (Kotlin + Jetpack Compose + Material 3) educational study organizer that automatically detects and validates consecutive JPG page URLs, organizes them into Physics, Chemistry, and Biology chapters, stores them locally in a Room SQLite database with JSON backup/restore, and builds aspect-ratio-preserving PDFs with an embedded PDF preview and native Android Print / Save as PDF workflow.

## Features

- **Bilingual UI (Bangla Default + English)**: Instant **B / E** toggle in the top app bar translating all UI labels, status badges, errors, and subject/chapter labels while keeping URLs untouched.
- **Light & Dark Theme**: Material 3 academic palette with persistent theme preference.
- **Automatic Page Link Scanner**:
  - Smart detection for `/page-001.jpg`, `/page_001.jpg`, `/page001.jpg`, `/001.jpg`, `?page=1`, `?page=001`, `/image/1/`, etc., preserving zero-padding and query parameters.
  - Ambiguity detection prompts the user to choose the exact numeric token when multiple numbers exist.
  - Strict validation: HTTP status classification (`VALID`, `MISSING`, `BLOCKED`, `RATE LIMITED`, `SERVER ERROR`, `NETWORK ERROR`, `TIMEOUT`, `INVALID IMAGE`), JPEG magic byte (`FF D8 FF`) check, and `BitmapFactory` decode verification (rejects HTTP 200 HTML error pages).
  - Double-retry confirmation on HTTP `404`/`410` before confirming `MISSING` and stopping; automatic pause with Resume/Retry on `403`, `429`, `5xx`, timeouts, or network errors without discarding valid links.
- **Chapter-wise Library (Room SQLite)**:
  - Physics (1st Paper: 11 chapters, 2nd Paper: 11 chapters)
  - Chemistry (1st Paper: 5 chapters, 2nd Paper: 5 chapters)
  - Biology (Botany: 12 chapters, Zoology: 12 chapters)
  - Custom chapter renaming, Merge/Replace saving, duplicate detection, numeric page sorting, Copy All, TXT export (SAF), multi-select delete, and JSON backup/restore.
- **PDF Preview & Native Print Workflow**:
  - Bounded-concurrency retrieval & verification of all chapter pages before PDF generation (never silently omits failed pages; provides Retry Failed Pages).
  - Memory-safe `PdfDocument` generation with A4 Fit (centered, white background, zero cropping/distortion) and Original Size modes.
  - Embedded `PdfRenderer` scrollable & zoomable viewer; opens Android's native `PrintManager` Print / Save as PDF dialog only on explicit user action.

## Optional Backend Relay (`backend/server.mjs`)

Native Android fetches public HTTP/HTTPS image URLs directly via OkHttp without browser CORS restrictions. If you also want to route requests through a controlled allowlisted server relay:

1. Deploy `backend/server.mjs` (Node.js 18+) with `ALLOWED_DOMAINS="example.com"` set in environment variables.
2. In the app, open **Settings → Optional Relay Endpoint URL** and enter your relay base URL.
3. The relay enforces domain allowlisting, blocks localhost/private/reserved IPs (SSRF & DNS-rebinding defense), caps image size at 20 MB, and verifies JPEG magic bytes.
