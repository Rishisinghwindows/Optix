from pydantic_settings import BaseSettings
from functools import lru_cache
from typing import Optional


class Settings(BaseSettings):
    # App
    app_name: str = "Optix"
    environment: str = "development"
    debug: bool = True

    # Database
    database_url: str = "postgresql+asyncpg://user:password@localhost:5432/nifty_auth"

    # Redis / Cache
    redis_url: str = "redis://localhost:6379"
    cache_backend: str = "memory"  # "memory" for single server, "redis" for multi-server

    # JWT
    jwt_secret_key: str = "your-super-secret-key-min-32-chars-long-here"
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 15
    refresh_token_expire_days: int = 30

    # Twilio SMS
    twilio_account_sid: Optional[str] = None
    twilio_auth_token: Optional[str] = None
    twilio_phone_number: Optional[str] = None

    # MSG91 (Alternative for India)
    msg91_auth_key: Optional[str] = None
    msg91_sender_id: Optional[str] = None
    msg91_template_id: Optional[str] = None

    # Google OAuth
    google_client_id: Optional[str] = None

    # Apple Sign In
    apple_team_id: Optional[str] = None
    apple_key_id: Optional[str] = None
    apple_client_id: Optional[str] = None
    apple_private_key: Optional[str] = None

    # Facebook OAuth
    facebook_app_id: Optional[str] = None
    facebook_app_secret: Optional[str] = None

    # Upstox API (for live market data)
    upstox_api_key: Optional[str] = None
    upstox_api_secret: Optional[str] = None
    upstox_redirect_uri: str = "http://localhost:8000/api/v1/market/upstox/callback"

    # OTP Settings
    otp_max_attempts: int = 3
    otp_expire_minutes: int = 5
    otp_rate_limit_per_hour: int = 3
    otp_resend_cooldown_seconds: int = 30

    # AI Chatbot
    openai_api_key: Optional[str] = None
    anthropic_api_key: Optional[str] = None
    ai_provider: str = "openai"  # "openai" or "anthropic"
    ai_model: str = "gpt-4o-mini"  # Default model

    # Google Gemini (FREE - for IPO Analysis)
    gemini_api_key: Optional[str] = None

    # Algo Trading Configuration
    algo_enabled: bool = True
    algo_default_capital: float = 100000.0
    algo_risk_per_trade: float = 0.02  # 2%
    algo_max_daily_loss: float = 0.05  # 5%
    algo_max_positions: int = 2
    algo_stop_loss_pct: float = 0.30  # 30% of premium
    algo_target_pct: float = 0.50  # 50% profit
    algo_market_start: str = "09:20"
    algo_market_end: str = "15:15"
    algo_paper_mode: bool = True  # Default to paper trading

    # Background services
    enable_alerts: bool = True
    enable_position_monitor: bool = True

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


@lru_cache()
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
