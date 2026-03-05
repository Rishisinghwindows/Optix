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
                <button onclick="logout()" class="text-gray-600 hover:text-red-500">Logout</button>
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
