from fastapi import Request
from typing import Optional


def get_client_ip(request: Request) -> Optional[str]:
    """
    Extract the client IP address from the request.
    Handles X-Forwarded-For header for proxied requests.
    """
    # Check X-Forwarded-For header (for proxies/load balancers)
    forwarded_for = request.headers.get("X-Forwarded-For")
    if forwarded_for:
        # Take the first IP in the chain (original client)
        return forwarded_for.split(",")[0].strip()

    # Check X-Real-IP header
    real_ip = request.headers.get("X-Real-IP")
    if real_ip:
        return real_ip

    # Fall back to the client's direct IP
    if request.client:
        return request.client.host

    return None
