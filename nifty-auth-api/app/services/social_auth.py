from typing import Optional
import httpx
from google.oauth2 import id_token
from google.auth.transport import requests
import jwt
from jwt.algorithms import RSAAlgorithm
from datetime import datetime
import time
import json

from app.config import settings

# Firebase project ID for token verification
FIREBASE_PROJECT_ID = "d23ai-d3862"

# Cache for Google's public keys
_google_keys_cache = {
    "keys": None,
    "expires_at": 0
}


class SocialAuthService:
    @staticmethod
    async def _fetch_google_public_keys() -> dict:
        """
        Fetch Google's public keys for JWT verification.
        Keys are cached based on Cache-Control header.
        """
        now = time.time()

        # Return cached keys if still valid
        if _google_keys_cache["keys"] and now < _google_keys_cache["expires_at"]:
            return _google_keys_cache["keys"]

        try:
            async with httpx.AsyncClient() as client:
                # Fetch from Google's public key endpoint
                response = await client.get(
                    "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
                )

                if response.status_code == 200:
                    keys = response.json()

                    # Parse cache-control header for expiry
                    cache_control = response.headers.get("cache-control", "")
                    max_age = 3600  # Default 1 hour
                    for part in cache_control.split(","):
                        if "max-age=" in part:
                            try:
                                max_age = int(part.split("=")[1].strip())
                            except:
                                pass

                    _google_keys_cache["keys"] = keys
                    _google_keys_cache["expires_at"] = now + max_age

                    return keys
        except Exception as e:
            print(f"Failed to fetch Google public keys: {e}")

        return _google_keys_cache["keys"] or {}

    @staticmethod
    async def verify_firebase_token(token: str) -> Optional[dict]:
        """
        Verify Firebase ID token manually without Firebase Admin SDK.
        Returns user info if valid, None otherwise.
        """
        try:
            from cryptography.x509 import load_pem_x509_certificate
            from cryptography.hazmat.backends import default_backend

            # Get the unverified header to find the key ID
            unverified_header = jwt.get_unverified_header(token)
            kid = unverified_header.get("kid")

            if not kid:
                return None

            # Fetch Google's public keys
            public_keys = await SocialAuthService._fetch_google_public_keys()

            if kid not in public_keys:
                print(f"Key ID {kid} not found in Google's public keys")
                return None

            # Get the certificate for this key ID (it's an X.509 certificate)
            certificate_str = public_keys[kid]

            # Parse the X.509 certificate to get the public key
            certificate = load_pem_x509_certificate(
                certificate_str.encode('utf-8'),
                default_backend()
            )
            public_key = certificate.public_key()

            # Decode and verify the token
            decoded = jwt.decode(
                token,
                public_key,
                algorithms=["RS256"],
                audience=FIREBASE_PROJECT_ID,
                issuer=f"https://securetoken.google.com/{FIREBASE_PROJECT_ID}",
            )

            # Extract user info
            return {
                "provider_id": decoded.get("user_id") or decoded.get("sub"),
                "email": decoded.get("email"),
                "full_name": decoded.get("name"),
                "avatar_url": decoded.get("picture"),
                "email_verified": decoded.get("email_verified", False),
            }

        except jwt.ExpiredSignatureError:
            print("Firebase token has expired")
            return None
        except jwt.InvalidAudienceError:
            print(f"Firebase token has invalid audience")
            return None
        except jwt.InvalidIssuerError:
            print(f"Firebase token has invalid issuer")
            return None
        except Exception as e:
            print(f"Firebase token verification failed: {e}")
            return None

    @staticmethod
    async def verify_google_token(token: str) -> Optional[dict]:
        """
        Verify Google/Firebase ID token and return user info.
        Supports both Firebase ID tokens and raw Google ID tokens.
        Returns None if verification fails.
        """
        # First, try to verify as a Firebase ID token
        firebase_result = await SocialAuthService.verify_firebase_token(token)
        if firebase_result:
            return firebase_result

        # Fallback: try to verify as a raw Google ID token
        try:
            # Verify the token using Google's library
            idinfo = id_token.verify_oauth2_token(
                token,
                requests.Request(),
                settings.google_client_id,
            )

            # Check issuer
            if idinfo["iss"] not in [
                "accounts.google.com",
                "https://accounts.google.com",
            ]:
                return None

            return {
                "provider_id": idinfo["sub"],
                "email": idinfo.get("email"),
                "full_name": idinfo.get("name"),
                "avatar_url": idinfo.get("picture"),
                "email_verified": idinfo.get("email_verified", False),
            }
        except Exception as e:
            print(f"Google token verification failed: {e}")
            return None

    @staticmethod
    async def verify_apple_token(identity_token: str) -> Optional[dict]:
        """
        Verify Apple identity token and return user info.
        Returns None if verification fails.
        """
        try:
            # Fetch Apple's public keys
            async with httpx.AsyncClient() as client:
                response = await client.get("https://appleid.apple.com/auth/keys")
                apple_keys = response.json()["keys"]

            # Decode the token header to get the key id
            unverified_header = jwt.get_unverified_header(identity_token)
            kid = unverified_header["kid"]

            # Find the matching key
            key_data = None
            for key in apple_keys:
                if key["kid"] == kid:
                    key_data = key
                    break

            if not key_data:
                return None

            # Construct the public key
            public_key = RSAAlgorithm.from_jwk(key_data)

            # Verify and decode the token
            decoded = jwt.decode(
                identity_token,
                public_key,
                algorithms=["RS256"],
                audience=settings.apple_client_id,
                issuer="https://appleid.apple.com",
            )

            return {
                "provider_id": decoded["sub"],
                "email": decoded.get("email"),
                "email_verified": decoded.get("email_verified", False),
                "full_name": None,  # Apple doesn't always provide name in token
                "avatar_url": None,
            }
        except Exception:
            return None

    @staticmethod
    async def verify_facebook_token(access_token: str) -> Optional[dict]:
        """
        Verify Facebook access token and return user info.
        Supports both Firebase tokens (from signInWithPopup) and raw Facebook tokens.
        Returns None if verification fails.
        """
        # First, try to verify as a Firebase ID token (if it's from Firebase Auth)
        firebase_result = await SocialAuthService.verify_firebase_token(access_token)
        if firebase_result:
            return firebase_result

        # Fallback: verify as raw Facebook access token
        try:
            async with httpx.AsyncClient() as client:
                # First, verify the token is valid
                debug_url = (
                    f"https://graph.facebook.com/debug_token"
                    f"?input_token={access_token}"
                    f"&access_token={settings.facebook_app_id}|{settings.facebook_app_secret}"
                )
                debug_response = await client.get(debug_url)
                debug_data = debug_response.json()

                if not debug_data.get("data", {}).get("is_valid"):
                    return None

                # Check app_id matches
                if debug_data["data"].get("app_id") != settings.facebook_app_id:
                    return None

                # Get user info
                user_url = (
                    f"https://graph.facebook.com/me"
                    f"?fields=id,name,email,picture.type(large)"
                    f"&access_token={access_token}"
                )
                user_response = await client.get(user_url)
                user_data = user_response.json()

                if "error" in user_data:
                    return None

                return {
                    "provider_id": user_data["id"],
                    "email": user_data.get("email"),
                    "full_name": user_data.get("name"),
                    "avatar_url": user_data.get("picture", {}).get("data", {}).get("url"),
                    "email_verified": True,  # Facebook emails are verified
                }
        except Exception:
            return None

    @staticmethod
    def generate_apple_client_secret() -> str:
        """
        Generate a client secret for Apple Sign In.
        Apple requires a JWT signed with your private key.
        """
        if not all([
            settings.apple_team_id,
            settings.apple_key_id,
            settings.apple_client_id,
            settings.apple_private_key,
        ]):
            raise ValueError("Apple Sign In not configured")

        now = int(time.time())

        headers = {
            "kid": settings.apple_key_id,
            "alg": "ES256",
        }

        payload = {
            "iss": settings.apple_team_id,
            "iat": now,
            "exp": now + 86400 * 180,  # 180 days
            "aud": "https://appleid.apple.com",
            "sub": settings.apple_client_id,
        }

        # Handle escaped newlines in the private key
        private_key = settings.apple_private_key.replace("\\n", "\n")

        return jwt.encode(
            payload,
            private_key,
            algorithm="ES256",
            headers=headers,
        )


social_auth_service = SocialAuthService()
