"""
IPO Service - Handles IPO data scraping, caching, and AI analysis
Scrapes from ipowatch.in with investorgain.com as fallback
Uses Google Gemini 2.5 Flash for AI analysis (FREE tier)
"""

import re
import json
import random
from typing import Optional, List, Dict, Any
from datetime import datetime

import httpx
from bs4 import BeautifulSoup

from app.services.cache_service import cache
from app.schemas.ipo import IPOStatus, IPOType
from app.config import settings


# User agents for rotation
USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
]

# Gemini API configuration (using 2.5-flash - current free tier model)
GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"


class IPOService:
    """Service for fetching and managing IPO data with GMP."""

    # Cache TTLs
    LISTINGS_TTL = 3600  # 1 hour
    FALLBACK_TTL = 86400  # 24 hours
    ANALYSIS_TTL = 7200  # 2 hours

    # Data sources
    PRIMARY_URL = "https://ipowatch.in/ipo-grey-market-premium-latest-ipo-gmp/"
    FALLBACK_URL = "https://www.investorgain.com/report/live-ipo-gmp/331/"  # Fallback for GMP data
    DETAIL_URL = "https://www.chittorgarh.com/ipo/ipo_list.asp"  # For additional details
    SUBSCRIPTION_URL = "https://www.chittorgarh.com/ipo/ipo_live_subscription_status.asp"

    def __init__(self):
        self._gemini_api_key = settings.gemini_api_key

    def is_ai_configured(self) -> bool:
        """Check if Gemini API is configured."""
        return bool(self._gemini_api_key)

    def _get_headers(self) -> Dict[str, str]:
        """Get request headers with random user agent."""
        return {
            "User-Agent": random.choice(USER_AGENTS),
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.5",
            "Connection": "keep-alive",
        }

    def _generate_slug(self, company_name: str) -> str:
        """Generate URL-friendly slug from company name."""
        slug = company_name.lower()
        slug = re.sub(r'[^a-z0-9\s-]', '', slug)
        slug = re.sub(r'[\s_-]+', '-', slug)
        slug = slug.strip('-')
        return slug[:50]  # Limit length

    def _parse_gmp_value(self, text: str) -> Optional[float]:
        """Parse GMP value from text like '+120' or '-50' or 'Rs 120' or '₹99'."""
        if not text or text.strip() in ['-', 'NA', 'N/A', '', '-%']:
            return None
        text = text.replace('Rs', '').replace('Rs.', '').replace(',', '').replace('₹', '').strip()
        match = re.search(r'[+-]?\d+\.?\d*', text)
        if match:
            return float(match.group())
        return None

    def _parse_price(self, text: str) -> Optional[float]:
        """Parse price value from text."""
        if not text or text.strip() in ['-', 'NA', 'N/A', '']:
            return None
        text = text.replace('Rs', '').replace('Rs.', '').replace(',', '').replace('₹', '').strip()
        match = re.search(r'\d+\.?\d*', text)
        if match:
            return float(match.group())
        return None

    def _parse_percentage(self, text: str) -> Optional[float]:
        """Parse percentage value from text like '+15.5%' or '15.5'."""
        if not text or text.strip() in ['-', 'NA', 'N/A', '']:
            return None
        text = text.replace('%', '').strip()
        match = re.search(r'[+-]?\d+\.?\d*', text)
        if match:
            return float(match.group())
        return None

    def _determine_status(self, row_text: str, dates: Dict) -> IPOStatus:
        """Determine IPO status from row data."""
        text_lower = row_text.lower()

        if 'listed' in text_lower or 'listing' in text_lower:
            return IPOStatus.LISTED
        if 'allotment' in text_lower:
            return IPOStatus.ALLOTMENT
        if 'closed' in text_lower:
            return IPOStatus.CLOSED
        if 'open' in text_lower or 'live' in text_lower:
            return IPOStatus.OPEN

        # Try to determine from dates
        today = datetime.now().date()
        if dates.get('open_date') and dates.get('close_date'):
            try:
                open_dt = datetime.strptime(dates['open_date'], '%d %b %Y').date()
                close_dt = datetime.strptime(dates['close_date'], '%d %b %Y').date()
                if today < open_dt:
                    return IPOStatus.UPCOMING
                if open_dt <= today <= close_dt:
                    return IPOStatus.OPEN
                if today > close_dt:
                    return IPOStatus.CLOSED
            except (ValueError, TypeError):
                pass

        return IPOStatus.UPCOMING

    def _determine_status_from_dates(self, ipo: Dict) -> IPOStatus:
        """Determine IPO status from enriched date fields."""
        today = datetime.now().date()
        open_date = ipo.get('open_date')
        close_date = ipo.get('close_date')
        listing_date = ipo.get('listing_date')

        # Try multiple date formats
        date_formats = ['%d %b %Y', '%d %B %Y', '%Y-%m-%d']

        def parse_date(date_str):
            if not date_str:
                return None
            for fmt in date_formats:
                try:
                    return datetime.strptime(date_str, fmt).date()
                except (ValueError, TypeError):
                    continue
            return None

        open_dt = parse_date(open_date)
        close_dt = parse_date(close_date)
        listing_dt = parse_date(listing_date)

        # If listed date is in the past, it's listed
        if listing_dt and listing_dt <= today:
            return IPOStatus.LISTED

        # Determine status from open/close dates
        if open_dt and close_dt:
            if today < open_dt:
                return IPOStatus.UPCOMING
            if open_dt <= today <= close_dt:
                return IPOStatus.OPEN
            if today > close_dt:
                # After close, check if allotment period (1-5 days after close)
                days_after_close = (today - close_dt).days
                if days_after_close <= 5:
                    return IPOStatus.ALLOTMENT
                return IPOStatus.CLOSED

        return IPOStatus.UPCOMING

    def _determine_ipo_type(self, text: str) -> IPOType:
        """Determine if IPO is mainboard or SME."""
        if 'sme' in text.lower():
            return IPOType.SME
        return IPOType.MAINBOARD

    async def scrape_ipowatch(self) -> List[Dict[str, Any]]:
        """Scrape IPO data from ipowatch.in."""
        try:
            async with httpx.AsyncClient(timeout=30.0, follow_redirects=True) as client:
                response = await client.get(self.PRIMARY_URL, headers=self._get_headers())
                response.raise_for_status()
                return self._parse_ipowatch_html(response.text)
        except Exception as e:
            print(f"[IPO] Error scraping ipowatch.in: {e}")
            return []

    def _parse_ipowatch_html(self, html: str) -> List[Dict[str, Any]]:
        """Parse ipowatch.in HTML table."""
        soup = BeautifulSoup(html, 'lxml')
        ipos = []

        # Find GMP table - usually has specific class or ID
        tables = soup.find_all('table')

        for table in tables:
            rows = table.find_all('tr')
            if len(rows) < 2:
                continue

            # Check if this looks like a GMP table by header
            header_row = rows[0]
            header_text = header_row.get_text().lower()
            if 'gmp' not in header_text and 'grey market' not in header_text:
                continue

            # Parse header to find column indices
            headers = [th.get_text().strip().lower() for th in header_row.find_all(['th', 'td'])]

            for row in rows[1:]:
                cells = row.find_all(['td', 'th'])
                if len(cells) < 4:
                    continue

                try:
                    row_data = [cell.get_text().strip() for cell in cells]
                    row_text = ' '.join(row_data)

                    # Extract company name (usually first column)
                    company_name = row_data[0] if row_data else ''
                    if not company_name or len(company_name) < 3:
                        continue

                    # Skip if it looks like a header
                    if 'company' in company_name.lower() or 'ipo name' in company_name.lower():
                        continue

                    ipo = {
                        'company_name': company_name.replace(' IPO', '').strip(),
                        'slug': self._generate_slug(company_name),
                        'ipo_type': self._determine_ipo_type(row_text).value,
                        'gmp': {},
                    }

                    # Try to extract GMP value - look for column with GMP in header
                    for i, header in enumerate(headers):
                        if i >= len(row_data):
                            break

                        cell_val = row_data[i]

                        if 'gmp' in header and 'est' not in header:
                            gmp_val = self._parse_gmp_value(cell_val)
                            if gmp_val is not None:
                                ipo['gmp']['gmp_value'] = gmp_val

                        elif 'price' in header and 'est' not in header:
                            price = self._parse_price(cell_val)
                            if price:
                                # Check if it's a price band (e.g., "100-120")
                                if '-' in cell_val:
                                    parts = cell_val.split('-')
                                    ipo['price_band_low'] = self._parse_price(parts[0])
                                    ipo['price_band_high'] = self._parse_price(parts[-1])
                                else:
                                    ipo['price_band_high'] = price
                                    ipo['price_band_low'] = price

                        elif 'lot' in header:
                            lot_match = re.search(r'\d+', cell_val)
                            if lot_match:
                                ipo['lot_size'] = int(lot_match.group())

                        elif ('gain' in header or 'est' in header) and '%' in cell_val:
                            pct = self._parse_percentage(cell_val)
                            if pct is not None:
                                ipo['gmp']['listing_gain_pct'] = pct

                        elif 'open' in header or 'date' in header:
                            if cell_val and cell_val not in ['-', 'NA']:
                                ipo['open_date'] = cell_val

                        elif 'close' in header:
                            if cell_val and cell_val not in ['-', 'NA']:
                                ipo['close_date'] = cell_val

                    # Calculate estimated listing price
                    if ipo.get('gmp', {}).get('gmp_value') and ipo.get('price_band_high'):
                        ipo['gmp']['estimated_listing_price'] = ipo['price_band_high'] + ipo['gmp']['gmp_value']

                    # Calculate listing gain percentage if not already set
                    if ipo.get('gmp', {}).get('gmp_value') and ipo.get('price_band_high') and not ipo['gmp'].get('listing_gain_pct'):
                        ipo['gmp']['listing_gain_pct'] = round((ipo['gmp']['gmp_value'] / ipo['price_band_high']) * 100, 2)

                    # Estimate lot size if not available (for min investment ~15000 for mainboard)
                    if not ipo.get('lot_size') and ipo.get('price_band_high'):
                        if ipo.get('ipo_type') == 'mainboard':
                            # Mainboard IPOs typically have min investment around 14000-15000
                            ipo['lot_size'] = max(1, int(15000 / ipo['price_band_high']))
                        else:
                            # SME IPOs have higher min investment (~100000-150000)
                            ipo['lot_size'] = max(1, int(120000 / ipo['price_band_high']))

                    # Calculate min investment
                    if ipo.get('lot_size') and ipo.get('price_band_high'):
                        ipo['min_investment'] = ipo['lot_size'] * ipo['price_band_high']

                    # Determine status
                    ipo['status'] = self._determine_status(row_text, ipo).value

                    # Set exchange
                    if 'nse' in row_text.lower():
                        ipo['exchange'] = 'NSE'
                    elif 'bse' in row_text.lower():
                        ipo['exchange'] = 'BSE'
                    else:
                        ipo['exchange'] = 'NSE/BSE'

                    ipos.append(ipo)

                except Exception as e:
                    print(f"[IPO] Error parsing row: {e}")
                    continue

        return ipos

    async def scrape_investorgain(self) -> List[Dict[str, Any]]:
        """Fallback: Scrape IPO data from investorgain.com."""
        try:
            async with httpx.AsyncClient(timeout=30.0, follow_redirects=True) as client:
                response = await client.get(self.FALLBACK_URL, headers=self._get_headers())
                response.raise_for_status()
                return self._parse_investorgain_html(response.text)
        except Exception as e:
            print(f"[IPO] Error scraping investorgain.com: {e}")
            return []

    def _parse_investorgain_html(self, html: str) -> List[Dict[str, Any]]:
        """Parse investorgain.com HTML table."""
        soup = BeautifulSoup(html, 'lxml')
        ipos = []

        # Find the main GMP table
        table = soup.find('table', {'id': 'mainTable'}) or soup.find('table', class_=re.compile(r'gmp|ipo'))
        if not table:
            tables = soup.find_all('table')
            for t in tables:
                if 'gmp' in t.get_text().lower():
                    table = t
                    break

        if not table:
            return []

        rows = table.find_all('tr')

        for row in rows[1:]:  # Skip header
            cells = row.find_all(['td', 'th'])
            if len(cells) < 3:
                continue

            try:
                row_data = [cell.get_text().strip() for cell in cells]
                company_name = row_data[0]

                if not company_name or len(company_name) < 3:
                    continue

                if 'company' in company_name.lower():
                    continue

                ipo = {
                    'company_name': company_name.replace(' IPO', '').strip(),
                    'slug': self._generate_slug(company_name),
                    'status': IPOStatus.UPCOMING.value,
                    'ipo_type': self._determine_ipo_type(' '.join(row_data)).value,
                    'gmp': {},
                }

                # Try to extract data from columns
                for i, cell_val in enumerate(row_data[1:], 1):
                    # GMP value
                    if i == 1 or '₹' in cell_val or 'Rs' in cell_val:
                        gmp_val = self._parse_gmp_value(cell_val)
                        if gmp_val is not None and not ipo['gmp'].get('gmp_value'):
                            ipo['gmp']['gmp_value'] = gmp_val

                    # Price
                    if 'price' in cells[0].get_text().lower() if i == 1 else False:
                        price = self._parse_price(cell_val)
                        if price:
                            ipo['price_band_high'] = price

                    # Percentage
                    if '%' in cell_val:
                        pct = self._parse_percentage(cell_val)
                        if pct is not None:
                            ipo['gmp']['listing_gain_pct'] = pct

                ipos.append(ipo)

            except Exception as e:
                print(f"[IPO] Error parsing investorgain row: {e}")
                continue

        return ipos

    async def scrape_chittorgarh_details(self) -> Dict[str, Dict[str, Any]]:
        """
        Scrape additional IPO details from chittorgarh.com.
        Returns dict keyed by normalized company name.
        """
        details = {}
        try:
            async with httpx.AsyncClient(timeout=30.0, follow_redirects=True) as client:
                response = await client.get(self.DETAIL_URL, headers=self._get_headers())
                response.raise_for_status()

                soup = BeautifulSoup(response.text, 'lxml')

                # Find IPO listing table
                tables = soup.find_all('table')
                for table in tables:
                    rows = table.find_all('tr')
                    for row in rows[1:]:  # Skip header
                        cells = row.find_all('td')
                        if len(cells) < 3:
                            continue

                        try:
                            # First cell usually has company name with link
                            name_cell = cells[0]
                            company_link = name_cell.find('a')
                            if company_link:
                                company_name = company_link.get_text().strip()
                                company_name = company_name.replace(' IPO', '').strip()
                                normalized = company_name.lower().replace(' ', '')

                                # Try to extract dates from other cells
                                for cell in cells[1:]:
                                    text = cell.get_text().strip()
                                    # Look for date patterns like "09 - 11 Feb"
                                    if re.search(r'\d{1,2}\s*[-–]\s*\d{1,2}\s+\w+', text):
                                        details[normalized] = details.get(normalized, {})
                                        details[normalized]['date_range'] = text

                        except Exception:
                            continue

                print(f"[IPO] Got details for {len(details)} IPOs from chittorgarh.com")

        except Exception as e:
            print(f"[IPO] Error scraping chittorgarh.com: {e}")

        return details

    async def scrape_subscription_data(self) -> Dict[str, Dict[str, Any]]:
        """
        Scrape live subscription data from chittorgarh.com.
        Returns dict keyed by normalized company name.
        """
        subscriptions = {}
        try:
            async with httpx.AsyncClient(timeout=30.0, follow_redirects=True) as client:
                response = await client.get(self.SUBSCRIPTION_URL, headers=self._get_headers())
                response.raise_for_status()

                soup = BeautifulSoup(response.text, 'lxml')

                # Find subscription table
                tables = soup.find_all('table')
                for table in tables:
                    header_text = table.get_text().lower()
                    if 'subscription' not in header_text and 'times' not in header_text:
                        continue

                    rows = table.find_all('tr')
                    headers = []
                    for row in rows:
                        cells = row.find_all(['td', 'th'])
                        if not headers:
                            headers = [c.get_text().strip().lower() for c in cells]
                            continue

                        if len(cells) < 4:
                            continue

                        try:
                            cell_data = [c.get_text().strip() for c in cells]
                            company_name = cell_data[0].replace(' IPO', '').strip()
                            normalized = company_name.lower().replace(' ', '')

                            sub_data = {'company_name': company_name}

                            for i, header in enumerate(headers):
                                if i >= len(cell_data):
                                    break
                                val = cell_data[i]

                                if 'total' in header or 'overall' in header:
                                    match = re.search(r'[\d.]+', val.replace('x', ''))
                                    if match:
                                        sub_data['total'] = float(match.group())

                                elif 'retail' in header or 'rii' in header:
                                    match = re.search(r'[\d.]+', val.replace('x', ''))
                                    if match:
                                        sub_data['retail'] = float(match.group())

                                elif 'nii' in header or 'hni' in header:
                                    match = re.search(r'[\d.]+', val.replace('x', ''))
                                    if match:
                                        sub_data['nii'] = float(match.group())

                                elif 'qib' in header:
                                    match = re.search(r'[\d.]+', val.replace('x', ''))
                                    if match:
                                        sub_data['qib'] = float(match.group())

                            if sub_data.get('total') or sub_data.get('retail'):
                                subscriptions[normalized] = sub_data

                        except Exception:
                            continue

                print(f"[IPO] Got subscription data for {len(subscriptions)} IPOs")

        except Exception as e:
            print(f"[IPO] Error scraping subscription data: {e}")

        return subscriptions

    def _enrich_ipo_data(self, ipos: List[Dict], subscriptions: Dict) -> List[Dict]:
        """Enrich IPO data with subscription info and estimated values."""
        current_year = datetime.now().year

        for ipo in ipos:
            company = ipo.get('company_name', '')
            normalized = company.lower().replace(' ', '')

            # Add subscription data if available
            if normalized in subscriptions:
                sub = subscriptions[normalized]
                ipo['subscription'] = {
                    'total': sub.get('total'),
                    'retail': sub.get('retail'),
                    'nii': sub.get('nii'),
                    'qib': sub.get('qib'),
                }

            # Parse and fix date format - add year if missing
            if ipo.get('open_date'):
                date_str = ipo['open_date']
                # If date doesn't have year, add current year
                if not re.search(r'20\d{2}', date_str):
                    # Parse "9-11 Feb" format and extract close date
                    match = re.match(r'(\d{1,2})\s*[-–]\s*(\d{1,2})\s+(\w+)', date_str)
                    if match:
                        open_day, close_day, month = match.groups()
                        open_day_int = int(open_day)
                        close_day_int = int(close_day)

                        # Handle month rollover (e.g., "30-3 Feb" means Jan 30 - Feb 3)
                        if open_day_int > close_day_int:
                            # Open date is in previous month
                            months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                                     'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
                            try:
                                month_idx = next(i for i, m in enumerate(months) if m.lower() == month.lower()[:3])
                                prev_month = months[month_idx - 1] if month_idx > 0 else 'Dec'
                                prev_year = current_year if month_idx > 0 else current_year - 1
                                ipo['open_date'] = f"{open_day} {prev_month} {prev_year}"
                            except (StopIteration, IndexError):
                                ipo['open_date'] = f"{open_day} {month} {current_year}"
                        else:
                            ipo['open_date'] = f"{open_day} {month} {current_year}"

                        ipo['close_date'] = f"{close_day} {month} {current_year}"

            # Estimate issue size if not available (based on typical mainboard/SME sizes)
            if not ipo.get('issue_size_cr'):
                if ipo.get('ipo_type') == 'mainboard':
                    ipo['issue_size_cr'] = "500-2000"  # Typical mainboard range
                else:
                    ipo['issue_size_cr'] = "20-100"  # Typical SME range

            # Recalculate status now that dates have year
            ipo['status'] = self._determine_status_from_dates(ipo).value

        return ipos

    async def scrape_ipo_data(self) -> List[Dict[str, Any]]:
        """
        Scrape IPO data from primary source with fallback.
        Enriches with subscription data from secondary sources.
        Returns list of IPO dictionaries.
        """
        # Try primary source
        ipos = await self.scrape_ipowatch()

        if ipos:
            print(f"[IPO] Scraped {len(ipos)} IPOs from ipowatch.in")

            # Try to get subscription data to enrich
            subscriptions = await self.scrape_subscription_data()
            ipos = self._enrich_ipo_data(ipos, subscriptions)

            return ipos

        print("[IPO] Primary source failed")
        return []

    async def get_upcoming_ipos(
        self,
        status_filter: Optional[str] = None,
        type_filter: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Get IPO listings with GMP data.
        Uses cache with 1-hour TTL.
        """
        cache_key = "ipo:listings"
        fallback_key = "ipo:listings:fallback"

        # Try cache first
        cached = await cache.get(cache_key)
        if cached:
            return self._filter_ipos(cached, status_filter, type_filter)

        # Scrape fresh data
        ipos = await self.scrape_ipo_data()

        if ipos:
            result = {
                'ipos': ipos,
                'total': len(ipos),
                'last_updated': datetime.now().isoformat(),
                'source': 'ipowatch.in',
            }

            # Cache with normal TTL
            await cache.set(cache_key, result, self.LISTINGS_TTL)

            # Also cache as fallback with longer TTL
            await cache.set(fallback_key, result, self.FALLBACK_TTL)

            return self._filter_ipos(result, status_filter, type_filter)

        # Try fallback cache if scraping failed
        fallback = await cache.get(fallback_key)
        if fallback:
            print("[IPO] Using fallback cached data")
            return self._filter_ipos(fallback, status_filter, type_filter)

        # Return empty result
        return {
            'ipos': [],
            'total': 0,
            'last_updated': None,
            'source': None,
        }

    def _filter_ipos(
        self,
        data: Dict[str, Any],
        status_filter: Optional[str] = None,
        type_filter: Optional[str] = None
    ) -> Dict[str, Any]:
        """Filter IPOs by status and type."""
        ipos = data.get('ipos', [])

        if status_filter:
            ipos = [ipo for ipo in ipos if ipo.get('status') == status_filter]

        if type_filter:
            ipos = [ipo for ipo in ipos if ipo.get('ipo_type') == type_filter]

        return {
            **data,
            'ipos': ipos,
            'total': len(ipos),
        }

    async def get_ipo_detail(self, slug: str) -> Optional[Dict[str, Any]]:
        """Get specific IPO by slug from cached listing data."""
        data = await self.get_upcoming_ipos()
        for ipo in data.get('ipos', []):
            if ipo.get('slug') == slug:
                return ipo
        return None

    async def get_ai_analysis(self, ipo_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Get AI-powered analysis for an IPO using Google Gemini 2.5 Flash (FREE).
        Uses cache with 2-hour TTL.
        """
        slug = ipo_data.get('slug', '')
        cache_key = f"ipo:analysis:{slug}"

        # Check cache
        cached = await cache.get(cache_key)
        if cached:
            return cached

        # Build analysis prompt
        prompt = self._build_analysis_prompt(ipo_data)

        try:
            if not self._gemini_api_key:
                return {
                    'verdict': 'Neutral',
                    'confidence': 0,
                    'analysis': 'AI analysis not available. Please configure GEMINI_API_KEY in environment.',
                    'key_positives': [],
                    'key_risks': [],
                    'recommendation_summary': 'Unable to analyze - Gemini API key not configured',
                    'disclaimer': 'This is AI-generated analysis for informational purposes only.',
                }

            # Call Gemini API
            response = await self._call_gemini(prompt)

            # Parse JSON response
            analysis = self._parse_ai_response(response)

            # Cache the result
            await cache.set(cache_key, analysis, self.ANALYSIS_TTL)

            return analysis

        except Exception as e:
            print(f"[IPO] Gemini AI analysis error: {e}")
            return {
                'verdict': 'Neutral',
                'confidence': 0,
                'analysis': f'AI analysis failed: {str(e)}',
                'key_positives': [],
                'key_risks': [],
                'recommendation_summary': 'Unable to generate analysis',
                'disclaimer': 'This is AI-generated analysis for informational purposes only.',
            }

    async def _call_gemini(self, prompt: str) -> str:
        """Call Google Gemini 2.5 Flash API."""
        url = f"{GEMINI_API_URL}?key={self._gemini_api_key}"

        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt}
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.7,
                "maxOutputTokens": 8192,
                "topP": 0.95,
                "topK": 40
            }
        }

        async with httpx.AsyncClient(timeout=60.0) as client:
            response = await client.post(
                url,
                headers={"Content-Type": "application/json"},
                json=payload
            )

            if response.status_code != 200:
                error_data = response.json()
                error_msg = error_data.get('error', {}).get('message', 'Unknown error')
                raise Exception(f"Gemini API error ({response.status_code}): {error_msg}")

            data = response.json()

            # Extract text from Gemini response
            candidates = data.get('candidates', [])
            if not candidates:
                raise Exception("No response from Gemini")

            content = candidates[0].get('content', {})
            parts = content.get('parts', [])
            if not parts:
                raise Exception("Empty response from Gemini")

            return parts[0].get('text', '')

    def _build_analysis_prompt(self, ipo_data: Dict[str, Any]) -> str:
        """Build structured prompt for IPO analysis."""
        company = ipo_data.get('company_name', 'Unknown')
        price_low = ipo_data.get('price_band_low', 'N/A')
        price_high = ipo_data.get('price_band_high', 'N/A')
        lot_size = ipo_data.get('lot_size', 'N/A')
        min_invest = ipo_data.get('min_investment', 'N/A')
        issue_size = ipo_data.get('issue_size_cr', 'N/A')

        gmp = ipo_data.get('gmp', {})
        gmp_value = gmp.get('gmp_value', 'N/A')
        listing_gain = gmp.get('listing_gain_pct', 'N/A')

        open_date = ipo_data.get('open_date', 'N/A')
        close_date = ipo_data.get('close_date', 'N/A')
        listing_date = ipo_data.get('listing_date', 'N/A')

        return f"""Analyze this IPO for retail investors. Be concise.

**IPO Details:**
- Company: {company}
- Price: Rs {price_high}
- Lot Size: {lot_size} shares
- Min Investment: Rs {min_invest}
- GMP: Rs {gmp_value} (Expected Gain: {listing_gain}%)
- Dates: {open_date} to {close_date}

Respond ONLY with this JSON (no markdown, no explanation):
{{"verdict":"Subscribe" or "Avoid" or "Neutral","confidence":0-100,"analysis":"2-3 sentences","key_positives":["point1","point2"],"key_risks":["risk1","risk2"],"expected_listing_gain":"X%","recommendation_summary":"one sentence"}}"""

    def _parse_ai_response(self, response: str) -> Dict[str, Any]:
        """Parse AI response JSON."""
        try:
            # Try to extract JSON from response
            json_match = re.search(r'\{[\s\S]*\}', response)
            if json_match:
                parsed = json.loads(json_match.group())
                return {
                    'verdict': parsed.get('verdict', 'Neutral'),
                    'confidence': parsed.get('confidence', 50),
                    'analysis': parsed.get('analysis', ''),
                    'key_positives': parsed.get('key_positives', []),
                    'key_risks': parsed.get('key_risks', []),
                    'expected_listing_gain': parsed.get('expected_listing_gain', 'Unknown'),
                    'recommendation_summary': parsed.get('recommendation_summary', ''),
                    'disclaimer': 'This is AI-generated analysis for informational purposes only. Not financial advice. Do your own research before investing.',
                }
        except json.JSONDecodeError:
            pass

        # Fallback: return the raw response as analysis
        return {
            'verdict': 'Neutral',
            'confidence': 50,
            'analysis': response,
            'key_positives': [],
            'key_risks': [],
            'expected_listing_gain': 'Unknown',
            'recommendation_summary': 'See analysis above',
            'disclaimer': 'This is AI-generated analysis for informational purposes only.',
        }

    async def refresh(self) -> Dict[str, Any]:
        """Force refresh by clearing cache and re-scraping."""
        await cache.delete("ipo:listings")
        return await self.get_upcoming_ipos()

    async def get_gmp_data(self) -> Dict[str, Any]:
        """Get GMP-only data for all active IPOs."""
        data = await self.get_upcoming_ipos()
        gmp_list = []

        for ipo in data.get('ipos', []):
            gmp = ipo.get('gmp', {})
            if gmp.get('gmp_value') is not None:
                gmp_list.append({
                    'company_name': ipo.get('company_name'),
                    'slug': ipo.get('slug'),
                    'status': ipo.get('status'),
                    'price_band_high': ipo.get('price_band_high'),
                    'gmp_value': gmp.get('gmp_value'),
                    'listing_gain_pct': gmp.get('listing_gain_pct'),
                    'estimated_listing_price': gmp.get('estimated_listing_price'),
                })

        return {
            'ipos': gmp_list,
            'last_updated': data.get('last_updated'),
        }


# Singleton instance
ipo_service = IPOService()
