
# Volunteer Map Backend

## Overview

This is the **backend server** for the Volunteer Map project.
It provides REST APIs for managing volunteer events and activities, and optionally serves the frontend in production.

* Built with **Spring Boot** + **Kotlin**
* Uses **H2 database** (in-memory) for development (can switch to PostgreSQL/MySQL)
* Provides **CORS support** for Svelte frontend development

---

## Project Structure

```text
backend/
├─ src/main/kotlin/com/example/demo/     # Kotlin source code
│    ├─ DemoApplication.kt              # Main Spring Boot application
│    ├─ controller/                     # REST controllers
│    │    └─ MainController.kt
│    ├─ model/                          # Data classes / entities
│    │    └─ VolunteerActivity.kt
│    └─ repository/                     # Spring Data repositories
├─ src/main/resources/
│    ├─ application.properties          # Configuration (datasource, etc.)
│    └─ static/                          # Optional: static files for production frontend
└─ build.gradle.kts                       # Gradle build file
```

---

## Dependencies

* `spring-boot-starter-web` → REST endpoints
* `spring-boot-starter-data-jpa` → ORM for database
* `h2` → In-memory database for development
* `kotlin-reflect` → Reflection support for Kotlin
* `spring-boot-starter-test` → Unit & integration testing

---

## How to Run (Development)

1. Open terminal and go to `backend/` folder
2. Run Spring Boot:

```bash
./gradlew bootRun
```

3. The backend runs on **`http://localhost:8080`**
4. Example endpoints:

| Endpoint | Method | Description                          |
| -------- | ------ | ------------------------------------ |
| `/api`   | GET    | Returns volunteer activities         |
| `/`      | GET    | Simple test endpoint (`Hello World`) |

> Make sure your frontend is running on a different port (default Svelte dev server: 5173) and CORS is enabled.

---

## Configuration

`application.properties` (example):

```properties id="a9xw0q"
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.h2.console.enabled=true
```

* For production, replace H2 with a persistent DB and adjust connection details.

---

## Testing

* Unit tests use **JUnit 5**
* Run tests with Gradle:

```bash
./gradlew test
```

---

## Notes

* **Controllers** handle API requests and return JSON
* **Entities** map to database tables
* **Repositories** provide easy DB access (CRUD)
* The backend is modular — new features can be added via new controllers, entities, or services

