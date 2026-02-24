
# Volunteer Map for Everyone!

## Project Structure

```
/orga        // organization / documentation / planning files
/frontend    // Svelte frontend (SPA, fetches data from backend)
/backend     // Spring Boot backend (REST API + database)
```

---

## Basic Idea

* Open source, non-commercial
* Interactive map for volunteers
* Volunteer hosts can **register their events** and **sync via calendar**
* Frontend fetches data from backend via **REST API**
* SPA handled by **Svelte**, backend handled by **Spring Boot + Kotlin**

---

## How to Run (Development)

### Backend (Spring Boot)

```bash
cd backend
./gradlew bootRun      # run Spring Boot
```

* Runs on `http://localhost:8080`
* Provides REST endpoints (e.g., `/api/volunteers`)
* Supports **CORS** for frontend development

---

### Frontend (Svelte)

```bash
cd frontend
npm install
npm run dev            # runs Svelte dev server
```

* Runs on `http://localhost:5173`
* Fetches data from backend via `/api` endpoints

---

### Production Build

* Build Svelte frontend:

```bash
cd frontend
npm run build
```

* Copy `frontend/dist/` → `backend/src/main/resources/static/`
* Spring Boot will now serve both frontend (`/`) and backend (`/api`) from **one server**

---

### Notes

* No HTML templates in backend — frontend Svelte SPA handles UI
* Modular frontend allows multiple pages/components
* Can extend with database entities (e.g., `VolunteerActivity`)
* Works best with JS enabled; Svelte SPA will be blank if JS is blocked

