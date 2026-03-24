"""
Admin Authentication Router
Simple admin authentication for the broker setup dashboard
Includes visitor analytics tracking
"""

from fastapi import APIRouter, HTTPException, Depends, Header, Request
from fastapi.responses import FileResponse, HTMLResponse
from pydantic import BaseModel
from typing import Optional, List
import jwt
import os
from datetime import datetime, timedelta
from sqlalchemy import select, func, distinct
from sqlalchemy.ext.asyncio import AsyncSession
import hashlib

from ..config import settings
from ..database import get_db
from ..models.visitor import Visitor, PageView
from ..models.device_token import DeviceToken
from ..services.push_notification_service import push_service
from ..services.market_monitor_service import market_monitor_service
from ..services.cache_service import cache


router = APIRouter(tags=["Admin"])


# Admin credentials from environment (defaults for development)
ADMIN_USERNAME = os.getenv("ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "optix2024")
ADMIN_JWT_SECRET = os.getenv("ADMIN_JWT_SECRET", settings.jwt_secret_key)
ADMIN_TOKEN_EXPIRY_HOURS = 24


class AdminLoginRequest(BaseModel):
    username: str
    password: str


class AdminLoginResponse(BaseModel):
    token: str
    expires_in: int
    username: str


class AdminVerifyResponse(BaseModel):
    valid: bool
    username: str


def create_admin_token(username: str) -> str:
    """Create JWT token for admin session"""
    payload = {
        "sub": username,
        "type": "admin",
        "iat": datetime.utcnow(),
        "exp": datetime.utcnow() + timedelta(hours=ADMIN_TOKEN_EXPIRY_HOURS)
    }
    return jwt.encode(payload, ADMIN_JWT_SECRET, algorithm="HS256")


def verify_admin_token(token: str) -> Optional[str]:
    """Verify admin JWT token, returns username if valid"""
    try:
        payload = jwt.decode(token, ADMIN_JWT_SECRET, algorithms=["HS256"])
        if payload.get("type") != "admin":
            return None
        return payload.get("sub")
    except jwt.ExpiredSignatureError:
        return None
    except jwt.InvalidTokenError:
        return None


async def get_admin_user(authorization: Optional[str] = Header(None)) -> str:
    """Dependency to verify admin authentication"""
    if not authorization:
        raise HTTPException(status_code=401, detail="Authorization header required")

    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid authorization format")

    token = authorization[7:]  # Remove "Bearer " prefix
    username = verify_admin_token(token)

    if not username:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    return username


# ==================== Admin Routes ====================

@router.post("/api/admin/login", response_model=AdminLoginResponse)
async def admin_login(request: AdminLoginRequest):
    """
    Admin login endpoint.
    Returns JWT token for authenticated session.
    """
    if request.username != ADMIN_USERNAME or request.password != ADMIN_PASSWORD:
        raise HTTPException(status_code=401, detail="Invalid credentials")

    token = create_admin_token(request.username)

    return AdminLoginResponse(
        token=token,
        expires_in=ADMIN_TOKEN_EXPIRY_HOURS * 3600,
        username=request.username
    )


@router.get("/api/admin/verify", response_model=AdminVerifyResponse)
async def admin_verify(username: str = Depends(get_admin_user)):
    """
    Verify admin token is valid.
    Used by dashboard to check if session is still active.
    """
    return AdminVerifyResponse(
        valid=True,
        username=username
    )


# ==================== Static Page Routes ====================

static_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "static")


@router.get("/admin/login")
async def admin_login_page():
    """Serve admin login page"""
    login_path = os.path.join(static_dir, "login.html")
    if os.path.exists(login_path):
        return FileResponse(login_path)
    raise HTTPException(status_code=404, detail="Login page not found")


@router.get("/admin/dashboard")
async def admin_dashboard_page():
    """Serve admin dashboard page"""
    dashboard_path = os.path.join(static_dir, "index.html")
    if os.path.exists(dashboard_path):
        return FileResponse(dashboard_path)
    raise HTTPException(status_code=404, detail="Dashboard not found")


# ==================== Visitor Tracking ====================

class TrackVisitorRequest(BaseModel):
    visitor_id: str
    page_url: Optional[str] = None
    page_title: Optional[str] = None
    referrer: Optional[str] = None
    session_id: Optional[str] = None


class VisitorStatsResponse(BaseModel):
    total_visitors: int
    unique_visitors: int
    total_page_views: int
    visitors_today: int
    visitors_this_week: int
    visitors_this_month: int
    top_pages: List[dict]
    visitors_by_day: List[dict]
    device_breakdown: dict
    browser_breakdown: dict


def parse_user_agent(user_agent: str) -> dict:
    """Simple user agent parser"""
    ua = user_agent.lower() if user_agent else ""

    # Device type
    if "mobile" in ua or "android" in ua or "iphone" in ua:
        device_type = "mobile"
    elif "tablet" in ua or "ipad" in ua:
        device_type = "tablet"
    else:
        device_type = "desktop"

    # Browser
    if "chrome" in ua and "edg" not in ua:
        browser = "Chrome"
    elif "firefox" in ua:
        browser = "Firefox"
    elif "safari" in ua and "chrome" not in ua:
        browser = "Safari"
    elif "edg" in ua:
        browser = "Edge"
    elif "opera" in ua or "opr" in ua:
        browser = "Opera"
    else:
        browser = "Other"

    # OS
    if "windows" in ua:
        os_name = "Windows"
    elif "mac" in ua:
        os_name = "macOS"
    elif "linux" in ua:
        os_name = "Linux"
    elif "android" in ua:
        os_name = "Android"
    elif "iphone" in ua or "ipad" in ua:
        os_name = "iOS"
    else:
        os_name = "Other"

    return {
        "device_type": device_type,
        "browser": browser,
        "os": os_name
    }


@router.post("/api/v1/track/visit")
async def track_visitor(
    request: Request,
    data: TrackVisitorRequest,
    db: AsyncSession = Depends(get_db)
):
    """
    Track a website visitor (called from frontend).
    No authentication required - public endpoint.
    """
    # Get IP and user agent
    ip_address = request.client.host if request.client else None
    user_agent = request.headers.get("user-agent", "")

    # Parse user agent
    ua_info = parse_user_agent(user_agent)

    # Check if this is a new visitor (hasn't visited before today)
    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
    existing_today = await db.execute(
        select(Visitor).where(
            Visitor.visitor_id == data.visitor_id,
            Visitor.visited_at >= today_start
        )
    )
    is_new_today = existing_today.scalar_one_or_none() is None

    # Check if completely new visitor
    existing_ever = await db.execute(
        select(Visitor).where(Visitor.visitor_id == data.visitor_id).limit(1)
    )
    is_new_visitor = existing_ever.scalar_one_or_none() is None

    # Create visitor record
    visitor = Visitor(
        visitor_id=data.visitor_id,
        ip_address=ip_address,
        user_agent=user_agent,
        referrer=data.referrer,
        page_url=data.page_url,
        page_title=data.page_title,
        device_type=ua_info["device_type"],
        browser=ua_info["browser"],
        os=ua_info["os"],
        session_id=data.session_id,
        is_new_visitor=is_new_visitor,
    )

    db.add(visitor)

    # Also track page view
    if data.page_url:
        page_view = PageView(
            visitor_id=data.visitor_id,
            session_id=data.session_id,
            page_url=data.page_url,
            page_title=data.page_title,
        )
        db.add(page_view)

    await db.commit()

    return {"success": True, "is_new_visitor": is_new_visitor}


@router.get("/api/admin/stats/visitors", response_model=VisitorStatsResponse)
async def get_visitor_stats(
    username: str = Depends(get_admin_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Get visitor statistics for admin dashboard.
    Requires admin authentication.
    """
    now = datetime.utcnow()
    today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
    week_start = today_start - timedelta(days=7)
    month_start = today_start - timedelta(days=30)

    # Total visitors (all records)
    total_result = await db.execute(select(func.count(Visitor.id)))
    total_visitors = total_result.scalar() or 0

    # Unique visitors
    unique_result = await db.execute(select(func.count(distinct(Visitor.visitor_id))))
    unique_visitors = unique_result.scalar() or 0

    # Total page views
    pageviews_result = await db.execute(select(func.count(PageView.id)))
    total_page_views = pageviews_result.scalar() or 0

    # Visitors today
    today_result = await db.execute(
        select(func.count(distinct(Visitor.visitor_id))).where(
            Visitor.visited_at >= today_start
        )
    )
    visitors_today = today_result.scalar() or 0

    # Visitors this week
    week_result = await db.execute(
        select(func.count(distinct(Visitor.visitor_id))).where(
            Visitor.visited_at >= week_start
        )
    )
    visitors_this_week = week_result.scalar() or 0

    # Visitors this month
    month_result = await db.execute(
        select(func.count(distinct(Visitor.visitor_id))).where(
            Visitor.visited_at >= month_start
        )
    )
    visitors_this_month = month_result.scalar() or 0

    # Top pages
    top_pages_result = await db.execute(
        select(
            PageView.page_url,
            PageView.page_title,
            func.count(PageView.id).label("views")
        )
        .group_by(PageView.page_url, PageView.page_title)
        .order_by(func.count(PageView.id).desc())
        .limit(10)
    )
    top_pages = [
        {"url": row.page_url, "title": row.page_title or row.page_url, "views": row.views}
        for row in top_pages_result.all()
    ]

    # Visitors by day (last 7 days)
    visitors_by_day = []
    for i in range(7):
        day_start = today_start - timedelta(days=i)
        day_end = day_start + timedelta(days=1)
        day_result = await db.execute(
            select(func.count(distinct(Visitor.visitor_id))).where(
                Visitor.visited_at >= day_start,
                Visitor.visited_at < day_end
            )
        )
        visitors_by_day.append({
            "date": day_start.strftime("%Y-%m-%d"),
            "visitors": day_result.scalar() or 0
        })
    visitors_by_day.reverse()

    # Device breakdown
    device_result = await db.execute(
        select(
            Visitor.device_type,
            func.count(distinct(Visitor.visitor_id)).label("count")
        )
        .group_by(Visitor.device_type)
    )
    device_breakdown = {row.device_type or "unknown": row.count for row in device_result.all()}

    # Browser breakdown
    browser_result = await db.execute(
        select(
            Visitor.browser,
            func.count(distinct(Visitor.visitor_id)).label("count")
        )
        .group_by(Visitor.browser)
    )
    browser_breakdown = {row.browser or "unknown": row.count for row in browser_result.all()}

    return VisitorStatsResponse(
        total_visitors=total_visitors,
        unique_visitors=unique_visitors,
        total_page_views=total_page_views,
        visitors_today=visitors_today,
        visitors_this_week=visitors_this_week,
        visitors_this_month=visitors_this_month,
        top_pages=top_pages,
        visitors_by_day=visitors_by_day,
        device_breakdown=device_breakdown,
        browser_breakdown=browser_breakdown,
    )


# ==================== Push Notification Admin ====================

class SendPushRequest(BaseModel):
    title: str
    body: str
    target: str = "all"  # "all", "ios", "android", "web"
    data: Optional[dict] = None


class ThresholdUpdateRequest(BaseModel):
    pcr_shift: Optional[float] = None
    vix_change_pct: Optional[float] = None
    oi_surge_pct: Optional[float] = None
    price_move_pct: Optional[float] = None
    oi_change_pct: Optional[float] = None
    max_pain_shift_pct: Optional[float] = None
    cooldown_seconds: Optional[int] = None
    monitored_indices: Optional[List[str]] = None


@router.get("/api/admin/push/devices")
async def list_push_devices(
    username: str = Depends(get_admin_user),
    db: AsyncSession = Depends(get_db),
):
    """List all registered push notification devices."""
    result = await db.execute(select(DeviceToken))
    devices = result.scalars().all()
    return [
        {
            "id": d.id,
            "user_id": d.user_id,
            "platform": d.platform,
            "device_name": d.device_name,
            "is_active": d.is_active,
            "created_at": d.created_at.isoformat() if d.created_at else None,
            "last_used_at": d.last_used_at.isoformat() if d.last_used_at else None,
            "token": d.token[:20] + "..." if d.token and len(d.token) > 20 else d.token,
        }
        for d in devices
    ]


@router.post("/api/admin/push/send")
async def send_push_notification(
    request: SendPushRequest,
    username: str = Depends(get_admin_user),
    db: AsyncSession = Depends(get_db),
):
    """Send a custom push notification to devices."""
    target = request.target.lower()

    if target == "all":
        result = await push_service.send_to_all(
            title=request.title,
            body=request.body,
            data=request.data,
        )
        return result

    # Platform-specific send
    if target not in ("ios", "android", "web"):
        raise HTTPException(status_code=400, detail="target must be 'all', 'ios', 'android', or 'web'")

    query = select(DeviceToken).where(
        DeviceToken.platform == target,
        DeviceToken.is_active == True,
    )
    db_result = await db.execute(query)
    tokens = db_result.scalars().all()

    sent = 0
    failed = 0
    for device in tokens:
        success = await push_service._send_to_device(
            token=device.token,
            title=request.title,
            body=request.body,
            data=request.data,
            platform=device.platform,
        )
        if success:
            sent += 1
        else:
            failed += 1

    return {"sent_count": sent, "failed_count": failed, "target": target}


@router.get("/api/admin/push/thresholds")
async def get_push_thresholds(
    username: str = Depends(get_admin_user),
):
    """Get current market monitor thresholds."""
    return {
        "thresholds": market_monitor_service.get_thresholds(),
        "monitored_indices": market_monitor_service.get_monitored_indices(),
    }


@router.put("/api/admin/push/thresholds")
async def update_push_thresholds(
    request: ThresholdUpdateRequest,
    username: str = Depends(get_admin_user),
):
    """Update market monitor thresholds and persist to cache."""
    # Update threshold values
    threshold_updates = {}
    for field in ("pcr_shift", "vix_change_pct", "oi_surge_pct", "price_move_pct", "oi_change_pct", "max_pain_shift_pct"):
        value = getattr(request, field)
        if value is not None:
            threshold_updates[field] = value

    if threshold_updates:
        market_monitor_service.update_thresholds(threshold_updates)

    # Update cooldown
    if request.cooldown_seconds is not None:
        market_monitor_service._signal_cooldown = request.cooldown_seconds

    # Update monitored indices
    if request.monitored_indices is not None:
        market_monitor_service.update_monitored_indices(request.monitored_indices)

    # Persist to cache so values survive restarts
    persist_data = {
        "thresholds": market_monitor_service.get_thresholds(),
        "monitored_indices": market_monitor_service.get_monitored_indices(),
    }
    await cache.set("market_monitor:config", persist_data, ttl=86400 * 365)

    return {
        "updated": True,
        **persist_data,
    }


@router.get("/api/admin/push/signals")
async def get_recent_signals(
    username: str = Depends(get_admin_user),
):
    """Get recent market monitor signal history from cache."""
    all_signals = []

    # Collect signals for each monitored index + global
    sources = market_monitor_service.get_monitored_indices() + ["global"]
    for source in sources:
        cache_key = f"market_signals:{source}"
        try:
            signals = await cache.get(cache_key)
            if signals:
                all_signals.extend(signals)
        except Exception:
            pass

    # Sort by timestamp descending
    all_signals.sort(key=lambda s: s.get("timestamp", ""), reverse=True)

    return {"signals": all_signals[:50]}


@router.get("/admin/analytics", response_class=HTMLResponse)
async def admin_analytics_page():
    """Serve admin analytics dashboard"""
    html_content = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Optix Analytics Dashboard</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
    </style>
</head>
<body class="bg-gray-100 min-h-screen">
    <div id="login-page" class="min-h-screen flex items-center justify-center">
        <div class="bg-white p-8 rounded-xl shadow-lg w-full max-w-md">
            <h1 class="text-2xl font-bold text-center mb-6 text-gray-800">Admin Login</h1>
            <form id="login-form">
                <input type="text" id="username" placeholder="Username"
                    class="w-full p-3 border rounded-lg mb-4 focus:outline-none focus:ring-2 focus:ring-green-500">
                <input type="password" id="password" placeholder="Password"
                    class="w-full p-3 border rounded-lg mb-4 focus:outline-none focus:ring-2 focus:ring-green-500">
                <button type="submit"
                    class="w-full bg-green-500 text-white p-3 rounded-lg font-semibold hover:bg-green-600 transition">
                    Login
                </button>
                <p id="login-error" class="text-red-500 text-center mt-4 hidden"></p>
            </form>
        </div>
    </div>

    <div id="dashboard" class="hidden">
        <nav class="bg-white shadow-sm border-b">
            <div class="max-w-7xl mx-auto px-4 py-4 flex justify-between items-center">
                <h1 class="text-xl font-bold text-gray-800">Optix Analytics</h1>
                <div class="flex items-center gap-4">
                    <a href="/admin/push" class="text-gray-500 hover:text-green-500 text-sm">Push Notifications</a>
                    <button onclick="logout()" class="text-gray-600 hover:text-red-500 text-sm">Logout</button>
                </div>
            </div>
        </nav>

        <main class="max-w-7xl mx-auto px-4 py-8">
            <!-- Stats Cards -->
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                <div class="bg-white rounded-xl shadow p-6">
                    <p class="text-gray-500 text-sm">Today's Visitors</p>
                    <p id="visitors-today" class="text-3xl font-bold text-green-500">-</p>
                </div>
                <div class="bg-white rounded-xl shadow p-6">
                    <p class="text-gray-500 text-sm">This Week</p>
                    <p id="visitors-week" class="text-3xl font-bold text-blue-500">-</p>
                </div>
                <div class="bg-white rounded-xl shadow p-6">
                    <p class="text-gray-500 text-sm">This Month</p>
                    <p id="visitors-month" class="text-3xl font-bold text-purple-500">-</p>
                </div>
                <div class="bg-white rounded-xl shadow p-6">
                    <p class="text-gray-500 text-sm">Total Unique Visitors</p>
                    <p id="visitors-total" class="text-3xl font-bold text-gray-800">-</p>
                </div>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
                <!-- Visitors Chart -->
                <div class="bg-white rounded-xl shadow p-6">
                    <h2 class="text-lg font-semibold mb-4">Visitors (Last 7 Days)</h2>
                    <canvas id="visitors-chart"></canvas>
                </div>

                <!-- Device Breakdown -->
                <div class="bg-white rounded-xl shadow p-6">
                    <h2 class="text-lg font-semibold mb-4">Device Breakdown</h2>
                    <canvas id="device-chart"></canvas>
                </div>
            </div>

            <!-- Top Pages -->
            <div class="bg-white rounded-xl shadow p-6">
                <h2 class="text-lg font-semibold mb-4">Top Pages</h2>
                <div class="overflow-x-auto">
                    <table class="w-full">
                        <thead>
                            <tr class="border-b">
                                <th class="text-left py-3 px-4 text-gray-600">Page</th>
                                <th class="text-right py-3 px-4 text-gray-600">Views</th>
                            </tr>
                        </thead>
                        <tbody id="top-pages-table"></tbody>
                    </table>
                </div>
            </div>
        </main>
    </div>

    <script>
        let token = localStorage.getItem('admin_token');
        let visitorsChart, deviceChart;

        // Check if already logged in
        if (token) {
            verifyAndShowDashboard();
        }

        document.getElementById('login-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const username = document.getElementById('username').value;
            const password = document.getElementById('password').value;

            try {
                const res = await fetch('/api/admin/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username, password })
                });

                if (res.ok) {
                    const data = await res.json();
                    localStorage.setItem('admin_token', data.token);
                    token = data.token;
                    showDashboard();
                } else {
                    document.getElementById('login-error').textContent = 'Invalid credentials';
                    document.getElementById('login-error').classList.remove('hidden');
                }
            } catch (err) {
                document.getElementById('login-error').textContent = 'Login failed';
                document.getElementById('login-error').classList.remove('hidden');
            }
        });

        async function verifyAndShowDashboard() {
            try {
                const res = await fetch('/api/admin/verify', {
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                if (res.ok) {
                    showDashboard();
                } else {
                    localStorage.removeItem('admin_token');
                }
            } catch (err) {
                localStorage.removeItem('admin_token');
            }
        }

        function showDashboard() {
            document.getElementById('login-page').classList.add('hidden');
            document.getElementById('dashboard').classList.remove('hidden');
            loadStats();
        }

        function logout() {
            localStorage.removeItem('admin_token');
            location.reload();
        }

        async function loadStats() {
            try {
                const res = await fetch('/api/admin/stats/visitors', {
                    headers: { 'Authorization': `Bearer ${token}` }
                });

                if (!res.ok) throw new Error('Failed to load stats');

                const data = await res.json();

                // Update stats
                document.getElementById('visitors-today').textContent = data.visitors_today.toLocaleString();
                document.getElementById('visitors-week').textContent = data.visitors_this_week.toLocaleString();
                document.getElementById('visitors-month').textContent = data.visitors_this_month.toLocaleString();
                document.getElementById('visitors-total').textContent = data.unique_visitors.toLocaleString();

                // Visitors chart
                const ctx1 = document.getElementById('visitors-chart').getContext('2d');
                if (visitorsChart) visitorsChart.destroy();
                visitorsChart = new Chart(ctx1, {
                    type: 'line',
                    data: {
                        labels: data.visitors_by_day.map(d => d.date.slice(5)),
                        datasets: [{
                            label: 'Visitors',
                            data: data.visitors_by_day.map(d => d.visitors),
                            borderColor: '#22C55E',
                            backgroundColor: 'rgba(34, 197, 94, 0.1)',
                            fill: true,
                            tension: 0.4
                        }]
                    },
                    options: { responsive: true, plugins: { legend: { display: false } } }
                });

                // Device chart
                const ctx2 = document.getElementById('device-chart').getContext('2d');
                if (deviceChart) deviceChart.destroy();
                deviceChart = new Chart(ctx2, {
                    type: 'doughnut',
                    data: {
                        labels: Object.keys(data.device_breakdown),
                        datasets: [{
                            data: Object.values(data.device_breakdown),
                            backgroundColor: ['#22C55E', '#3B82F6', '#F59E0B', '#EF4444']
                        }]
                    },
                    options: { responsive: true }
                });

                // Top pages table
                const tableBody = document.getElementById('top-pages-table');
                tableBody.innerHTML = data.top_pages.map(page => `
                    <tr class="border-b hover:bg-gray-50">
                        <td class="py-3 px-4">${page.title || page.url}</td>
                        <td class="py-3 px-4 text-right font-semibold">${page.views.toLocaleString()}</td>
                    </tr>
                `).join('');

            } catch (err) {
                console.error('Failed to load stats:', err);
            }
        }

        // Refresh stats every 30 seconds
        setInterval(() => {
            if (!document.getElementById('dashboard').classList.contains('hidden')) {
                loadStats();
            }
        }, 30000);
    </script>
</body>
</html>
    """
    return HTMLResponse(content=html_content)


@router.get("/admin/push", response_class=HTMLResponse)
async def admin_push_page():
    """Serve admin push notification management page"""
    html_content = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Optix Push Notifications</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        .toast { position: fixed; top: 20px; right: 20px; z-index: 9999; transition: opacity 0.3s; }
        .toast.hidden { opacity: 0; pointer-events: none; }
        .tab-active { border-bottom: 2px solid #22C55E; color: #22C55E; font-weight: 600; }
        .signal-badge { font-size: 0.7rem; padding: 2px 8px; border-radius: 9999px; font-weight: 600; text-transform: uppercase; }
    </style>
</head>
<body class="bg-gray-100 min-h-screen">
    <!-- Toast -->
    <div id="toast" class="toast hidden">
        <div id="toast-inner" class="px-6 py-3 rounded-lg shadow-lg text-white text-sm font-medium"></div>
    </div>

    <!-- Login Page -->
    <div id="login-page" class="min-h-screen flex items-center justify-center">
        <div class="bg-white p-8 rounded-xl shadow-lg w-full max-w-md">
            <h1 class="text-2xl font-bold text-center mb-2 text-gray-800">Push Notifications</h1>
            <p class="text-gray-500 text-center mb-6 text-sm">Admin Login Required</p>
            <form id="login-form">
                <input type="text" id="username" placeholder="Username"
                    class="w-full p-3 border rounded-lg mb-4 focus:outline-none focus:ring-2 focus:ring-green-500">
                <input type="password" id="password" placeholder="Password"
                    class="w-full p-3 border rounded-lg mb-4 focus:outline-none focus:ring-2 focus:ring-green-500">
                <button type="submit"
                    class="w-full bg-green-500 text-white p-3 rounded-lg font-semibold hover:bg-green-600 transition">
                    Login
                </button>
                <p id="login-error" class="text-red-500 text-center mt-4 hidden"></p>
            </form>
        </div>
    </div>

    <!-- Dashboard -->
    <div id="dashboard" class="hidden">
        <nav class="bg-white shadow-sm border-b">
            <div class="max-w-7xl mx-auto px-4 py-4 flex justify-between items-center">
                <h1 class="text-xl font-bold text-gray-800">Optix Push Notifications</h1>
                <div class="flex items-center gap-4">
                    <a href="/admin/analytics" class="text-gray-500 hover:text-green-500 text-sm">Analytics</a>
                    <button onclick="logout()" class="text-gray-600 hover:text-red-500 text-sm">Logout</button>
                </div>
            </div>
        </nav>

        <!-- Tabs -->
        <div class="max-w-7xl mx-auto px-4 pt-4">
            <div class="flex gap-1 border-b bg-white rounded-t-xl overflow-x-auto">
                <button onclick="switchTab('send')" id="tab-send" class="px-6 py-3 text-sm text-gray-600 hover:text-green-500 whitespace-nowrap tab-active">Send Push</button>
                <button onclick="switchTab('devices')" id="tab-devices" class="px-6 py-3 text-sm text-gray-600 hover:text-green-500 whitespace-nowrap">Devices</button>
                <button onclick="switchTab('monitor')" id="tab-monitor" class="px-6 py-3 text-sm text-gray-600 hover:text-green-500 whitespace-nowrap">Market Monitor</button>
                <button onclick="switchTab('signals')" id="tab-signals" class="px-6 py-3 text-sm text-gray-600 hover:text-green-500 whitespace-nowrap">Signal History</button>
            </div>
        </div>

        <main class="max-w-7xl mx-auto px-4 py-6">

            <!-- Send Push Section -->
            <div id="section-send">
                <div class="bg-white rounded-xl shadow p-6">
                    <h2 class="text-lg font-semibold mb-4 text-gray-800">Send Push Notification</h2>
                    <form id="send-form" class="space-y-4">
                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-1">Title</label>
                            <input type="text" id="push-title" placeholder="Notification title" required
                                class="w-full p-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500">
                        </div>
                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-1">Body</label>
                            <textarea id="push-body" placeholder="Notification body text" rows="3" required
                                class="w-full p-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500"></textarea>
                        </div>
                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-1">Target</label>
                            <select id="push-target"
                                class="w-full p-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500 bg-white">
                                <option value="all">All Devices</option>
                                <option value="ios">iOS Only</option>
                                <option value="android">Android Only</option>
                                <option value="web">Web Only</option>
                            </select>
                        </div>
                        <div>
                            <button type="button" onclick="toggleCustomData()" class="text-sm text-green-600 hover:text-green-700 font-medium">
                                + Custom Data (JSON)
                            </button>
                            <div id="custom-data-section" class="hidden mt-2">
                                <textarea id="push-custom-data" placeholder='{"key": "value"}' rows="3"
                                    class="w-full p-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500 font-mono text-sm"></textarea>
                            </div>
                        </div>
                        <button type="submit" id="send-btn"
                            class="bg-green-500 text-white px-8 py-3 rounded-lg font-semibold hover:bg-green-600 transition">
                            Send Notification
                        </button>
                    </form>
                    <div id="send-result" class="hidden mt-4 p-4 rounded-lg"></div>
                </div>
            </div>

            <!-- Devices Section -->
            <div id="section-devices" class="hidden">
                <div class="bg-white rounded-xl shadow p-6">
                    <div class="flex justify-between items-center mb-4">
                        <h2 class="text-lg font-semibold text-gray-800">Registered Devices</h2>
                        <span class="text-xs text-gray-400">Auto-refreshes every 30s</span>
                    </div>
                    <div class="overflow-x-auto">
                        <table class="w-full">
                            <thead>
                                <tr class="border-b">
                                    <th class="text-left py-3 px-4 text-gray-600 text-sm">Platform</th>
                                    <th class="text-left py-3 px-4 text-gray-600 text-sm">Device Name</th>
                                    <th class="text-left py-3 px-4 text-gray-600 text-sm">Token</th>
                                    <th class="text-left py-3 px-4 text-gray-600 text-sm">Status</th>
                                    <th class="text-left py-3 px-4 text-gray-600 text-sm">Last Used</th>
                                    <th class="text-left py-3 px-4 text-gray-600 text-sm">Created</th>
                                </tr>
                            </thead>
                            <tbody id="devices-table">
                                <tr><td colspan="6" class="py-8 text-center text-gray-400">Loading...</td></tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            <!-- Market Monitor Section -->
            <div id="section-monitor" class="hidden">
                <div class="bg-white rounded-xl shadow p-6">
                    <h2 class="text-lg font-semibold mb-6 text-gray-800">Market Monitor Thresholds</h2>
                    <form id="thresholds-form" class="space-y-6">
                        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">PCR Shift Threshold</label>
                                <p class="text-xs text-gray-400 mb-1">Minimum change in Put-Call Ratio to trigger alert</p>
                                <input type="number" id="th-pcr-shift" step="0.01" value="0.12"
                                    class="w-full p-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">VIX Change %</label>
                                <p class="text-xs text-gray-400 mb-1">Percentage change in India VIX to trigger alert</p>
                                <input type="number" id="th-vix-change" step="0.5" value="3.0"
                                    class="w-full p-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">OI Surge %</label>
                                <p class="text-xs text-gray-400 mb-1">Open Interest surge percentage to trigger unusual activity alert</p>
                                <input type="number" id="th-oi-surge" step="1" value="15.0"
                                    class="w-full p-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">Price Move %</label>
                                <p class="text-xs text-gray-400 mb-1">Underlying price move percentage to trigger alert</p>
                                <input type="number" id="th-price-move" step="0.1" value="0.3"
                                    class="w-full p-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">OI Change % for Buildup</label>
                                <p class="text-xs text-gray-400 mb-1">OI change threshold to detect long/short buildup or unwinding</p>
                                <input type="number" id="th-oi-buildup" step="0.5" value="5.0"
                                    class="w-full p-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">Max Pain Shift %</label>
                                <p class="text-xs text-gray-400 mb-1">Percentage shift in max pain level to trigger alert</p>
                                <input type="number" id="th-max-pain-shift" step="0.1" value="1.0"
                                    class="w-full p-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500">
                            </div>
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-1">Signal Cooldown (seconds)</label>
                                <p class="text-xs text-gray-400 mb-1">Minimum seconds between repeated signals of same type</p>
                                <input type="number" id="th-cooldown" step="60" value="900"
                                    class="w-full p-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500">
                            </div>
                        </div>

                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-2">Monitored Indices</label>
                            <p class="text-xs text-gray-400 mb-2">Select which indices to monitor for market signals</p>
                            <div class="flex flex-wrap gap-4">
                                <label class="flex items-center gap-2 text-sm">
                                    <input type="checkbox" id="idx-nifty" value="NIFTY" checked class="accent-green-500 w-4 h-4"> NIFTY
                                </label>
                                <label class="flex items-center gap-2 text-sm">
                                    <input type="checkbox" id="idx-banknifty" value="BANKNIFTY" checked class="accent-green-500 w-4 h-4"> BANKNIFTY
                                </label>
                                <label class="flex items-center gap-2 text-sm">
                                    <input type="checkbox" id="idx-finnifty" value="FINNIFTY" class="accent-green-500 w-4 h-4"> FINNIFTY
                                </label>
                                <label class="flex items-center gap-2 text-sm">
                                    <input type="checkbox" id="idx-midcpnifty" value="MIDCPNIFTY" class="accent-green-500 w-4 h-4"> MIDCPNIFTY
                                </label>
                                <label class="flex items-center gap-2 text-sm">
                                    <input type="checkbox" id="idx-sensex" value="SENSEX" class="accent-green-500 w-4 h-4"> SENSEX
                                </label>
                            </div>
                        </div>

                        <div class="flex gap-3">
                            <button type="submit"
                                class="bg-green-500 text-white px-8 py-3 rounded-lg font-semibold hover:bg-green-600 transition">
                                Save Thresholds
                            </button>
                            <button type="button" onclick="resetThresholds()"
                                class="bg-gray-200 text-gray-700 px-8 py-3 rounded-lg font-semibold hover:bg-gray-300 transition">
                                Reset to Defaults
                            </button>
                        </div>
                    </form>
                </div>
            </div>

            <!-- Signal History Section -->
            <div id="section-signals" class="hidden">
                <div class="bg-white rounded-xl shadow p-6">
                    <div class="flex justify-between items-center mb-4">
                        <h2 class="text-lg font-semibold text-gray-800">Signal History</h2>
                        <span class="text-xs text-gray-400">Auto-refreshes every 60s</span>
                    </div>
                    <div id="signals-list">
                        <p class="text-gray-400 text-center py-8">Loading...</p>
                    </div>
                </div>
            </div>

        </main>
    </div>

    <script>
        let token = localStorage.getItem('admin_token');
        let activeTab = 'send';
        let devicesInterval = null;
        let signalsInterval = null;

        const SIGNAL_COLORS = {
            pcr_shift: { bg: 'bg-purple-100', text: 'text-purple-800', dot: 'bg-purple-500' },
            vix_change: { bg: 'bg-red-100', text: 'text-red-800', dot: 'bg-red-500' },
            long_buildup: { bg: 'bg-green-100', text: 'text-green-800', dot: 'bg-green-500' },
            short_buildup: { bg: 'bg-red-100', text: 'text-red-800', dot: 'bg-red-500' },
            long_unwinding: { bg: 'bg-orange-100', text: 'text-orange-800', dot: 'bg-orange-500' },
            short_covering: { bg: 'bg-blue-100', text: 'text-blue-800', dot: 'bg-blue-500' },
            oi_surge: { bg: 'bg-yellow-100', text: 'text-yellow-800', dot: 'bg-yellow-500' },
            max_pain_shift: { bg: 'bg-gray-100', text: 'text-gray-800', dot: 'bg-gray-500' }
        };

        const PLATFORM_BADGE = {
            ios: '<span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800">iOS</span>',
            android: '<span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-800">Android</span>',
            web: '<span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-orange-100 text-orange-800">Web</span>'
        };

        const DEFAULTS = {
            pcr_shift: 0.12, vix_change: 3.0, oi_surge: 15.0, price_move: 0.3,
            oi_buildup: 5.0, max_pain_shift: 1.0, cooldown: 900,
            indices: ['NIFTY', 'BANKNIFTY']
        };

        // Check auth on load
        if (token) { verifyAndShowDashboard(); }

        // Login
        document.getElementById('login-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const username = document.getElementById('username').value;
            const password = document.getElementById('password').value;
            try {
                const res = await fetch('/api/admin/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username, password })
                });
                if (res.ok) {
                    const data = await res.json();
                    localStorage.setItem('admin_token', data.token);
                    token = data.token;
                    showDashboard();
                } else {
                    showLoginError('Invalid credentials');
                }
            } catch (err) {
                showLoginError('Login failed');
            }
        });

        function showLoginError(msg) {
            const el = document.getElementById('login-error');
            el.textContent = msg;
            el.classList.remove('hidden');
        }

        async function verifyAndShowDashboard() {
            try {
                const res = await fetch('/api/admin/verify', {
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                if (res.ok) { showDashboard(); }
                else { localStorage.removeItem('admin_token'); token = null; }
            } catch (err) {
                localStorage.removeItem('admin_token'); token = null;
            }
        }

        function showDashboard() {
            document.getElementById('login-page').classList.add('hidden');
            document.getElementById('dashboard').classList.remove('hidden');
            switchTab('send');
        }

        function logout() {
            localStorage.removeItem('admin_token');
            location.reload();
        }

        function showToast(msg, success = true) {
            const toast = document.getElementById('toast');
            const inner = document.getElementById('toast-inner');
            inner.textContent = msg;
            inner.className = 'px-6 py-3 rounded-lg shadow-lg text-white text-sm font-medium ' + (success ? 'bg-green-500' : 'bg-red-500');
            toast.classList.remove('hidden');
            setTimeout(() => toast.classList.add('hidden'), 3000);
        }

        function apiHeaders() {
            return { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token };
        }

        // Tab switching
        function switchTab(tab) {
            activeTab = tab;
            ['send', 'devices', 'monitor', 'signals'].forEach(t => {
                document.getElementById('section-' + t).classList.toggle('hidden', t !== tab);
                document.getElementById('tab-' + t).classList.toggle('tab-active', t === tab);
            });
            if (devicesInterval) { clearInterval(devicesInterval); devicesInterval = null; }
            if (signalsInterval) { clearInterval(signalsInterval); signalsInterval = null; }
            if (tab === 'devices') { loadDevices(); devicesInterval = setInterval(loadDevices, 30000); }
            if (tab === 'signals') { loadSignals(); signalsInterval = setInterval(loadSignals, 60000); }
            if (tab === 'monitor') { loadThresholds(); }
        }

        function toggleCustomData() {
            document.getElementById('custom-data-section').classList.toggle('hidden');
        }

        // Send Push
        document.getElementById('send-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const btn = document.getElementById('send-btn');
            btn.disabled = true; btn.textContent = 'Sending...';

            const payload = {
                title: document.getElementById('push-title').value,
                body: document.getElementById('push-body').value,
                target: document.getElementById('push-target').value
            };

            const customData = document.getElementById('push-custom-data').value.trim();
            if (customData) {
                try {
                    payload.custom_data = JSON.parse(customData);
                } catch (err) {
                    showToast('Invalid JSON in custom data', false);
                    btn.disabled = false; btn.textContent = 'Send Notification';
                    return;
                }
            }

            try {
                const res = await fetch('/api/admin/push/send', {
                    method: 'POST', headers: apiHeaders(), body: JSON.stringify(payload)
                });
                const data = await res.json();
                const resultEl = document.getElementById('send-result');
                resultEl.classList.remove('hidden');
                if (res.ok) {
                    const sent = data.sent_count || 0;
                    const failed = data.failed_count || 0;
                    resultEl.className = 'mt-4 p-4 rounded-lg bg-green-50 border border-green-200';
                    resultEl.innerHTML = '<p class="text-green-800 font-medium">Sent successfully</p>' +
                        '<p class="text-green-600 text-sm mt-1">Delivered: ' + sent + ' | Failed: ' + failed + '</p>';
                } else {
                    resultEl.className = 'mt-4 p-4 rounded-lg bg-red-50 border border-red-200';
                    resultEl.innerHTML = '<p class="text-red-800 font-medium">Send failed</p>' +
                        '<p class="text-red-600 text-sm mt-1">' + (data.detail || 'Unknown error') + '</p>';
                }
            } catch (err) {
                showToast('Failed to send notification', false);
            }
            btn.disabled = false; btn.textContent = 'Send Notification';
        });

        // Devices
        async function loadDevices() {
            try {
                const res = await fetch('/api/admin/push/devices', { headers: apiHeaders() });
                if (!res.ok) throw new Error('Failed');
                const data = await res.json();
                const devices = data.devices || data || [];
                const tbody = document.getElementById('devices-table');
                if (devices.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="6" class="py-8 text-center text-gray-400">No devices registered</td></tr>';
                    return;
                }
                tbody.innerHTML = devices.map(function(d) {
                    var platform = (d.platform || 'web').toLowerCase();
                    var badge = PLATFORM_BADGE[platform] || PLATFORM_BADGE.web;
                    var tokenStr = d.token ? (d.token.substring(0, 20) + '...') : '-';
                    var statusBadge = (d.status === 'active' || d.is_active)
                        ? '<span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-800">Active</span>'
                        : '<span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-500">Inactive</span>';
                    var lastUsed = d.last_used ? new Date(d.last_used).toLocaleString() : '-';
                    var created = d.created_at ? new Date(d.created_at).toLocaleString() : '-';
                    return '<tr class="border-b hover:bg-gray-50">' +
                        '<td class="py-3 px-4">' + badge + '</td>' +
                        '<td class="py-3 px-4 text-sm">' + (d.device_name || d.name || '-') + '</td>' +
                        '<td class="py-3 px-4 text-xs font-mono text-gray-500">' + tokenStr + '</td>' +
                        '<td class="py-3 px-4">' + statusBadge + '</td>' +
                        '<td class="py-3 px-4 text-sm text-gray-600">' + lastUsed + '</td>' +
                        '<td class="py-3 px-4 text-sm text-gray-600">' + created + '</td>' +
                        '</tr>';
                }).join('');
            } catch (err) {
                document.getElementById('devices-table').innerHTML =
                    '<tr><td colspan="6" class="py-8 text-center text-red-400">Failed to load devices</td></tr>';
            }
        }

        // Thresholds
        async function loadThresholds() {
            try {
                const res = await fetch('/api/admin/push/thresholds', { headers: apiHeaders() });
                if (!res.ok) throw new Error('Failed');
                const data = await res.json();
                const th = data.thresholds || data;
                if (th.pcr_shift !== undefined) document.getElementById('th-pcr-shift').value = th.pcr_shift;
                if (th.vix_change_pct !== undefined) document.getElementById('th-vix-change').value = th.vix_change_pct;
                if (th.oi_surge_pct !== undefined) document.getElementById('th-oi-surge').value = th.oi_surge_pct;
                if (th.price_move_pct !== undefined) document.getElementById('th-price-move').value = th.price_move_pct;
                if (th.oi_change_pct !== undefined) document.getElementById('th-oi-buildup').value = th.oi_change_pct;
                if (th.max_pain_shift_pct !== undefined) document.getElementById('th-max-pain-shift').value = th.max_pain_shift_pct;
                if (th.cooldown_seconds !== undefined) document.getElementById('th-cooldown').value = th.cooldown_seconds;
                var indices = data.monitored_indices || DEFAULTS.indices;
                document.getElementById('idx-nifty').checked = indices.includes('NIFTY');
                document.getElementById('idx-banknifty').checked = indices.includes('BANKNIFTY');
                document.getElementById('idx-finnifty').checked = indices.includes('FINNIFTY');
                document.getElementById('idx-midcpnifty').checked = indices.includes('MIDCPNIFTY');
                document.getElementById('idx-sensex').checked = indices.includes('SENSEX');
            } catch (err) {
                // Defaults already set in HTML
            }
        }

        document.getElementById('thresholds-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            var indices = [];
            if (document.getElementById('idx-nifty').checked) indices.push('NIFTY');
            if (document.getElementById('idx-banknifty').checked) indices.push('BANKNIFTY');
            if (document.getElementById('idx-finnifty').checked) indices.push('FINNIFTY');
            if (document.getElementById('idx-midcpnifty').checked) indices.push('MIDCPNIFTY');
            if (document.getElementById('idx-sensex').checked) indices.push('SENSEX');

            var payload = {
                pcr_shift: parseFloat(document.getElementById('th-pcr-shift').value),
                vix_change_pct: parseFloat(document.getElementById('th-vix-change').value),
                oi_surge_pct: parseFloat(document.getElementById('th-oi-surge').value),
                price_move_pct: parseFloat(document.getElementById('th-price-move').value),
                oi_change_pct: parseFloat(document.getElementById('th-oi-buildup').value),
                max_pain_shift_pct: parseFloat(document.getElementById('th-max-pain-shift').value),
                cooldown_seconds: parseInt(document.getElementById('th-cooldown').value),
                monitored_indices: indices
            };

            try {
                const res = await fetch('/api/admin/push/thresholds', {
                    method: 'PUT', headers: apiHeaders(), body: JSON.stringify(payload)
                });
                if (res.ok) { showToast('Thresholds saved successfully'); }
                else { showToast('Failed to save thresholds', false); }
            } catch (err) {
                showToast('Failed to save thresholds', false);
            }
        });

        function resetThresholds() {
            document.getElementById('th-pcr-shift').value = DEFAULTS.pcr_shift;
            document.getElementById('th-vix-change').value = DEFAULTS.vix_change;
            document.getElementById('th-oi-surge').value = DEFAULTS.oi_surge;
            document.getElementById('th-price-move').value = DEFAULTS.price_move;
            document.getElementById('th-oi-buildup').value = DEFAULTS.oi_buildup;
            document.getElementById('th-max-pain-shift').value = DEFAULTS.max_pain_shift;
            document.getElementById('th-cooldown').value = DEFAULTS.cooldown;
            document.getElementById('idx-nifty').checked = true;
            document.getElementById('idx-banknifty').checked = true;
            document.getElementById('idx-finnifty').checked = false;
            document.getElementById('idx-midcpnifty').checked = false;
            document.getElementById('idx-sensex').checked = false;
            showToast('Reset to defaults (click Save to apply)');
        }

        // Signals
        async function loadSignals() {
            try {
                const res = await fetch('/api/admin/push/signals', { headers: apiHeaders() });
                if (!res.ok) throw new Error('Failed');
                const data = await res.json();
                const signals = data.signals || data || [];
                const container = document.getElementById('signals-list');
                if (signals.length === 0) {
                    container.innerHTML = '<p class="text-gray-400 text-center py-8">No signals recorded yet</p>';
                    return;
                }
                container.innerHTML = signals.map(function(s) {
                    var type = s.signal_type || s.type || 'unknown';
                    var colors = SIGNAL_COLORS[type] || { bg: 'bg-gray-100', text: 'text-gray-800', dot: 'bg-gray-500' };
                    var ts = s.timestamp || s.created_at;
                    var timeStr = ts ? new Date(ts).toLocaleString() : '-';
                    var index = s.index || s.symbol || '';
                    return '<div class="flex items-start gap-4 py-4 border-b last:border-0">' +
                        '<div class="flex-shrink-0 mt-1"><div class="w-3 h-3 rounded-full ' + colors.dot + '"></div></div>' +
                        '<div class="flex-grow min-w-0">' +
                        '<div class="flex flex-wrap items-center gap-2 mb-1">' +
                        '<span class="signal-badge ' + colors.bg + ' ' + colors.text + '">' + type.replace(/_/g, ' ') + '</span>' +
                        (index ? '<span class="text-xs text-gray-500 font-medium">' + index + '</span>' : '') +
                        '<span class="text-xs text-gray-400">' + timeStr + '</span>' +
                        '</div>' +
                        '<p class="text-sm font-medium text-gray-800">' + (s.title || '') + '</p>' +
                        '<p class="text-sm text-gray-500 mt-0.5">' + (s.body || s.message || '') + '</p>' +
                        '</div></div>';
                }).join('');
            } catch (err) {
                document.getElementById('signals-list').innerHTML =
                    '<p class="text-red-400 text-center py-8">Failed to load signals</p>';
            }
        }
    </script>
</body>
</html>
    """
    return HTMLResponse(content=html_content)
