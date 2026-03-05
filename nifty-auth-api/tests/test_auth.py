import pytest
from httpx import AsyncClient
from unittest.mock import patch, AsyncMock


@pytest.mark.asyncio
async def test_send_otp_valid_phone(client: AsyncClient):
    """Test sending OTP to a valid phone number."""
    with patch("app.routers.auth.sms_service.send_otp", new_callable=AsyncMock) as mock_sms:
        mock_sms.return_value = True

        response = await client.post(
            "/api/v1/auth/otp/send",
            json={"phone": "+919876543210", "purpose": "login"}
        )

        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "expires_in" in data
        assert "resend_after" in data


@pytest.mark.asyncio
async def test_send_otp_invalid_phone(client: AsyncClient):
    """Test sending OTP to an invalid phone number."""
    response = await client.post(
        "/api/v1/auth/otp/send",
        json={"phone": "invalid", "purpose": "login"}
    )

    assert response.status_code == 422  # Validation error


@pytest.mark.asyncio
async def test_verify_otp_invalid_format(client: AsyncClient):
    """Test verifying OTP with invalid format."""
    response = await client.post(
        "/api/v1/auth/otp/verify",
        json={"phone": "+919876543210", "otp": "12345"}  # Too short
    )

    assert response.status_code == 422  # Validation error


@pytest.mark.asyncio
async def test_refresh_token_invalid(client: AsyncClient):
    """Test refresh token with invalid token."""
    response = await client.post(
        "/api/v1/auth/refresh",
        json={"refresh_token": "invalid_token_here"}
    )

    assert response.status_code == 401


@pytest.mark.asyncio
async def test_protected_endpoint_without_auth(client: AsyncClient):
    """Test accessing protected endpoint without authentication."""
    response = await client.get("/api/v1/user/me")

    assert response.status_code == 403  # No credentials


@pytest.mark.asyncio
async def test_protected_endpoint_with_invalid_token(client: AsyncClient):
    """Test accessing protected endpoint with invalid token."""
    response = await client.get(
        "/api/v1/user/me",
        headers={"Authorization": "Bearer invalid_token"}
    )

    assert response.status_code == 401


@pytest.mark.asyncio
async def test_social_login_missing_token(client: AsyncClient):
    """Test social login without token."""
    response = await client.post(
        "/api/v1/auth/social/google",
        json={"device_info": {"device_name": "Test"}}
    )

    assert response.status_code == 400
