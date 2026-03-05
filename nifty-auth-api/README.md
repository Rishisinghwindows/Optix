# Optix Authentication API

A FastAPI backend for user authentication supporting mobile OTP login, social authentication (Google, Apple, Facebook), and guest mode with lazy authentication for the Optix options trading app.

## Features

- **Mobile OTP Login**: Phone number verification via Twilio/MSG91
- **Social Login**: Google, Apple, Facebook Sign-In
- **Guest Mode**: Browse freely, prompt login on trade actions
- **JWT Authentication**: Access tokens (15 min) + Refresh tokens (30 days)
- **Session Management**: Multi-device support with device tracking
- **Rate Limiting**: OTP rate limiting via Redis

## Tech Stack

- **Backend**: Python 3.11+ with FastAPI
- **Database**: PostgreSQL 15+
- **ORM**: SQLAlchemy 2.0 + Alembic
- **Caching**: Redis
- **SMS**: Twilio / MSG91

## Quick Start

### Using Docker (Recommended)

```bash
# Clone and navigate to the project
cd optix-auth-api

# Copy environment file
cp .env.example .env

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f api
```

The API will be available at `http://localhost:8000`

### Manual Setup

```bash
# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Set up environment variables
cp .env.example .env
# Edit .env with your configuration

# Start PostgreSQL and Redis (required)
# ...

# Run migrations
alembic upgrade head

# Start the server
uvicorn app.main:app --reload
```

## API Documentation

Once running, access:
- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc

## API Endpoints

### Authentication (`/api/v1/auth`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/otp/send` | Send OTP to phone number |
| POST | `/otp/verify` | Verify OTP and login |
| POST | `/social/google` | Google Sign-In |
| POST | `/social/apple` | Apple Sign-In |
| POST | `/social/facebook` | Facebook Login |
| POST | `/refresh` | Refresh access token |
| POST | `/logout` | Logout current session |
| POST | `/logout-all` | Logout all sessions |

### User (`/api/v1/user`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/me` | Get current user profile |
| PATCH | `/me` | Update profile |
| DELETE | `/me` | Delete account |
| GET | `/sessions` | List active sessions |
| DELETE | `/sessions/{id}` | Revoke a session |

## Example Requests

### Send OTP
```bash
curl -X POST http://localhost:8000/api/v1/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"phone": "+919876543210", "purpose": "login"}'
```

### Verify OTP
```bash
curl -X POST http://localhost:8000/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+919876543210",
    "otp": "123456",
    "device_info": {
      "device_name": "iPhone 15 Pro",
      "os": "iOS 17.0",
      "app_version": "1.0.0"
    }
  }'
```

### Refresh Token
```bash
curl -X POST http://localhost:8000/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "eyJhbGciOiJIUzI1NiIs..."}'
```

### Get User Profile
```bash
curl http://localhost:8000/api/v1/user/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

## Configuration

### Required Environment Variables

```env
# Database
DATABASE_URL=postgresql+asyncpg://user:password@localhost:5432/optix_auth

# Redis
REDIS_URL=redis://localhost:6379

# JWT (IMPORTANT: Change in production!)
JWT_SECRET_KEY=your-super-secret-key-min-32-chars-long-here

# SMS Provider (at least one required for production)
TWILIO_ACCOUNT_SID=...
TWILIO_AUTH_TOKEN=...
TWILIO_PHONE_NUMBER=...
```

### Social Login Configuration

For social login to work, you need to configure:

1. **Google**: Add `GOOGLE_CLIENT_ID`
2. **Apple**: Add `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_CLIENT_ID`, `APPLE_PRIVATE_KEY`
3. **Facebook**: Add `FACEBOOK_APP_ID`, `FACEBOOK_APP_SECRET`

## Database Migrations

```bash
# Create a new migration
alembic revision --autogenerate -m "Description"

# Apply migrations
alembic upgrade head

# Rollback one migration
alembic downgrade -1
```

## Testing

```bash
# Run all tests
pytest

# Run with coverage
pytest --cov=app

# Run specific test file
pytest tests/test_auth.py -v
```

## iOS Integration

The `ios-integration/` directory contains Swift files for integrating with the Optix iOS app:

- `Models/AuthModels.swift` - Data models
- `Services/AuthAPIService.swift` - API client
- `Services/AuthManager.swift` - Auth state manager
- `Views/Auth/LoginSheetView.swift` - Login UI
- `Views/Auth/TradeGuardModifier.swift` - Trade protection

### Integration Steps

1. Add the Swift files to your Xcode project
2. Install dependencies via SPM:
   - `GoogleSignIn`
   - `FacebookLogin`
3. Configure social login in your app
4. Use `AuthManager.shared` for auth state
5. Add `.tradeGuard()` modifier to views with trade actions

### Example Usage

```swift
struct TradingView: View {
    @EnvironmentObject var authManager: AuthManager

    var body: some View {
        VStack {
            // Your trading UI...

            Button("Execute Trade") {
                guard authManager.requireAuth(action: {
                    await executeTrade()
                }) else { return }

                Task { await executeTrade() }
            }
        }
        .tradeGuard() // Shows login sheet when needed
    }
}
```

## Production Deployment

### Railway/Render

1. Connect your GitHub repository
2. Set environment variables
3. Deploy

### Docker (Self-hosted)

```bash
# Build production image
docker build -t optix-auth-api .

# Run with production settings
docker run -d \
  -p 8000:8000 \
  -e DATABASE_URL=postgresql+asyncpg://... \
  -e REDIS_URL=redis://... \
  -e JWT_SECRET_KEY=... \
  -e ENVIRONMENT=production \
  optix-auth-api
```

## Security

- All passwords/tokens are hashed with bcrypt
- JWT tokens with short expiry (15 min access, 30 day refresh)
- Rate limiting on OTP endpoints
- SQL injection protection via SQLAlchemy ORM
- Input validation via Pydantic

## License

MIT
