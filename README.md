# PayAssist AI

Intelligent merchant & terminal support chatbot — an Android app backed by a Retrieval-Augmented Generation (RAG) service, built for Mall of Sfax merchants during an 8-week summer internship.

PayAssist AI lets merchants troubleshoot payment-terminal errors in plain language (grounded in real payment documentation via RAG), verify transactions, simulate card payments, and review sales on a dashboard — with separate merchant and admin experiences.

## Project Structure
PayAssistAI/
├── app/ # Android client (Kotlin + Jetpack Compose)
│ └── src/main/java/com/payassistai/app/
│ ├── data/ # Room entities, DAOs, repositories
│ ├── di/ # Hilt modules (Database, Network, Repository, Session)
│ ├── models/ # Retrofit request/response models
│ ├── network/ # ApiService (Retrofit)
│ ├── ui/screens/ # Compose screens
│ ├── viewmodels/ # AuthViewModel, ChatViewModel, TransactionsViewModel
│ └── util/ # PdfExporter, etc.
├── backend/ # RAG service (Python + FastAPI)
│ ├── main.py # FastAPI server — /ask, /transactions, /process_payment
│ ├── ingest.py # One-time document ingestion into ChromaDB
│ ├── requirements.txt
│ ├── PaymentGuide.docx
│ └── Apex_Merchant_Payment_Error_Guide.pdf
└── gradle/libs.versions.toml


## Prerequisites

- **Android Studio** (recent stable release) with an emulator or physical device, API 24+
- **Python 3.10+**
- **[Ollama](https://ollama.com)** installed and running locally
- The `llama3.2` model pulled:
```bash
  ollama pull llama3.2
```

## Backend Setup (`backend/`)

1. **Create a virtual environment and install dependencies:**
```bash
   cd backend
   python -m venv venv
   source venv/bin/activate        # Windows: venv\Scripts\Activate.ps1
   pip install -r requirements.txt
```

2. **Make sure Ollama is running** in its own terminal:
```bash
   ollama serve
```
(If it says the address is already in use, it's already running — that's fine.)

3. **Ingest the payment documents into ChromaDB.** This reads `PaymentGuide.docx` and `Apex_Merchant_Payment_Error_Guide.pdf`, chunks them, embeds them, and builds the local vector store at `backend/chroma_db/`:
```bash
   python ingest.py
```
Run this once before the first launch, and again any time the source documents change or `ingest.py` itself is updated — it deletes and rebuilds `chroma_db/` from scratch each time.

4. **Start the API server:**
```bash
   python main.py
```
You should see `✅ ChromaDB collection loaded` and `✅ Ollama is running with model: llama3.2` in the startup logs. If the second line instead says Ollama isn't available, stop here and fix that first (see Troubleshooting) — the app will still run, but chat answers will fall back to raw document excerpts instead of natural-language responses.

The server listens on `http://0.0.0.0:8000`. Swagger docs are available at `http://localhost:8000/docs`.

## Android Setup (`app/`)

1. Open the project root in Android Studio and let Gradle sync.
2. The backend base URL is set via `BuildConfig.API_BASE_URL`:
    - **Debug builds** default to `http://10.0.2.2:8000/` — this only works from the **Android emulator**, which maps that address to your host machine's `localhost`. If you're running on a **physical device**, change this in `app/build.gradle.kts` to your computer's LAN IP (e.g. `http://192.168.1.42:8000/`) and make sure the phone and backend are on the same network.
3. Run the app (▶) on an emulator or device.
4. Log in with one of the seeded accounts (see below) — merchant and admin accounts are created automatically on first launch.

### Seeded accounts

An admin and ~45 Mall of Sfax merchant accounts are seeded automatically the first time the app runs against an empty database. Passwords are the merchant's name, lowercased, with spaces/punctuation removed (e.g. Carrefour → `carrefour`).

| Role | Email | Password |
|---|---|---|
| Admin | `admin@mall-sfax.net` | `admin123` |
| Merchant (example) | `carrefour@mall-sfax.net` | `carrefour` |

## Typical Demo Flow

1. Log in as a merchant (or admin).
2. Ask the chat a troubleshooting question, e.g. *"What does DEC-001 mean?"* — answers are grounded in the ingested payment guides.
3. Make a payment (Transactions tab → Pay) — try both an amount under and over the PIN threshold.
4. Check the Dashboard for the sales breakdown and transaction list.
5. (Admin only) Open the Admin Panel to add or remove merchants.

## Troubleshooting

**Chat answers look like raw document dumps instead of natural sentences**
Ollama isn't running, isn't reachable, or `llama3.2` isn't pulled. Check the backend's startup log for `⚠️ Ollama is not running or model not available.` `check_ollama_available()` only runs once at startup — if you start Ollama *after* `main.py`, restart `main.py` too.

**Login fails with "Invalid email or password" even with the right credentials from the table above**
The app only seeds accounts into an empty database. If you're switching between builds that changed the password-hashing scheme, or the database already has stale data, clear the app's data (**Settings → Apps → PayAssistAI → Storage → Clear Data**) or uninstall/reinstall so it seeds fresh.

**Chat/transactions work but nothing shows up, or the emulator can't reach the backend at all**
Confirm `main.py` is actually running and listening on port 8000, and that `BuildConfig.API_BASE_URL` matches your setup (emulator vs. physical device — see Android Setup above).

**CSV export/import doesn't do anything on Android 9 (API 28) or below**
Those OS versions require the `WRITE_EXTERNAL_STORAGE` runtime permission; make sure to grant it when prompted.

## Tech Stack

**Android:** Kotlin, Jetpack Compose, Material 3, Hilt (dependency injection), Room, Retrofit + Gson, Kotlin Coroutines & Flow, Android `PdfDocument` API

**Backend:** FastAPI + Uvicorn, LangChain (document loading/splitting), Sentence-Transformers (`all-MiniLM-L6-v2`), ChromaDB (embedded vector store), Ollama (`llama3.2`, local inference — no payment data or documentation leaves the machine)

## Author

Chayma Bouassida — Summer Internship, TELNET INC
