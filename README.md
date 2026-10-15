# CareerBridge backend (Spring Boot 3, Java 17, MySQL 8)

This repository contains the standalone Spring Boot REST API for CareerBridge.
The React frontend is maintained in a separate frontend repository.

## Requirements

- Java 17 or newer
- Maven 3.9+
- MySQL 8

## Set up

From the repository root, create the local database and tables:

```powershell
mysql -u root -p < database\schema.sql
```

Copy `.env.example` to `.env` and set your database password, a unique random
`JWT_SECRET` of at least 32 characters, and strong administrator and demo
passwords. The `.env` file is local configuration and must not be committed.

For local frontend development, set `CORS_ALLOWED_ORIGINS` to the frontend's
origin (by default, `http://localhost:5173`). The frontend API base URL is
configured separately with `VITE_API_URL`.

```powershell
.\run-local.ps1         # loads .env and starts the API
mvn test                # integration tests (in-memory H2; no MySQL needed)
mvn clean package       # builds target\job-portal-backend-1.0.0.jar
```

API docs (Swagger UI): http://localhost:8080/swagger-ui.html
The database schema is in `database\schema.sql`; Hibernate never changes it.
Uploaded resumes are kept in the local `uploads` directory and are not tracked
by Git. Set `SEED_DEMO_DATA=false` when deploying with production data.
