





## Database Setup!

### Prerequisites
- MySQL installed locally
- MySQL running on port 3306

### Initial Setup
1. start MySQL server
2. run the database initialization script:
```bash
   mysql -u root -p < scripts/setup/init_database.sql



## Backend Setup

### Prerequisites
- Java 11 or higher
- MySQL running on port 3306

### Running the Backend

1. Download dependencies (see `backend/lib/download-dependencies.md`)
2. Initialize database:
```bash
   mysql -u root -p < scripts/setup/init_database.sql
```
3. Compile and run server:
```bash
   cd backend
   javac -cp ".:lib/*" Server.java
   java -cp ".:lib/*" com.checkmate.Server
```

Backend will run on `http://localhost:8080`

### Testing Backend
```bash
# Test signup
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@test.com","phoneNumber":"5551234567","password":"password123"}'

# Test login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"emailOrPhone":"test@test.com","password":"password123"}'
```