"""API routes for IPO Dashboard - Grey Market Premium and AI Analysis."""

from typing import Optional
from fastapi import APIRouter, HTTPException, status

from app.services.ipo_service import ipo_service
from app.schemas.ipo import (
    IPOListResponse,
    IPOItem,
    IPOAnalysisRequest,
    IPOAnalysisResponse,
    GMPListResponse,
    GMPData,
)


router = APIRouter(prefix="/api/v1/ipo", tags=["IPO"])


@router.get("/list", response_model=IPOListResponse)
async def get_ipo_list(
    status: Optional[str] = None,
    ipo_type: Optional[str] = None,
):
    """
    Get list of IPOs with Grey Market Premium data.

    - **status**: Filter by status (upcoming, open, closed, listed, allotment)
    - **ipo_type**: Filter by type (mainboard, sme)
    """
    result = await ipo_service.get_upcoming_ipos(
        status_filter=status,
        type_filter=ipo_type,
    )

    # Convert to response model
    ipos = []
    for ipo_data in result.get('ipos', []):
        gmp_data = ipo_data.get('gmp', {})
        ipo = IPOItem(
            company_name=ipo_data.get('company_name', ''),
            slug=ipo_data.get('slug', ''),
            status=ipo_data.get('status', 'upcoming'),
            ipo_type=ipo_data.get('ipo_type', 'mainboard'),
            open_date=ipo_data.get('open_date'),
            close_date=ipo_data.get('close_date'),
            listing_date=ipo_data.get('listing_date'),
            price_band_low=ipo_data.get('price_band_low'),
            price_band_high=ipo_data.get('price_band_high'),
            lot_size=ipo_data.get('lot_size'),
            issue_size_cr=ipo_data.get('issue_size_cr'),
            min_investment=ipo_data.get('min_investment'),
            exchange=ipo_data.get('exchange'),
            gmp=GMPData(**gmp_data) if gmp_data else None,
            ai_verdict=ipo_data.get('ai_verdict'),
            ai_analysis=ipo_data.get('ai_analysis'),
        )
        ipos.append(ipo)

    return IPOListResponse(
        ipos=ipos,
        total=result.get('total', 0),
        last_updated=result.get('last_updated'),
        source=result.get('source'),
    )


@router.get("/detail/{slug}")
async def get_ipo_detail(slug: str):
    """Get detailed information for a specific IPO by slug."""
    ipo = await ipo_service.get_ipo_detail(slug)

    if not ipo:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"IPO with slug '{slug}' not found",
        )

    gmp_data = ipo.get('gmp', {})
    return IPOItem(
        company_name=ipo.get('company_name', ''),
        slug=ipo.get('slug', ''),
        status=ipo.get('status', 'upcoming'),
        ipo_type=ipo.get('ipo_type', 'mainboard'),
        open_date=ipo.get('open_date'),
        close_date=ipo.get('close_date'),
        listing_date=ipo.get('listing_date'),
        price_band_low=ipo.get('price_band_low'),
        price_band_high=ipo.get('price_band_high'),
        lot_size=ipo.get('lot_size'),
        issue_size_cr=ipo.get('issue_size_cr'),
        min_investment=ipo.get('min_investment'),
        exchange=ipo.get('exchange'),
        gmp=GMPData(**gmp_data) if gmp_data else None,
        ai_verdict=ipo.get('ai_verdict'),
        ai_analysis=ipo.get('ai_analysis'),
    )


@router.post("/analyze", response_model=IPOAnalysisResponse)
async def analyze_ipo(request: IPOAnalysisRequest):
    """
    Get AI-powered analysis for an IPO.

    Returns verdict (Subscribe/Avoid/Neutral), confidence score,
    detailed analysis, key positives, and risks.
    """
    # Find the IPO by company name
    data = await ipo_service.get_upcoming_ipos()
    ipo = None

    # Try exact match first
    for item in data.get('ipos', []):
        if item.get('company_name', '').lower() == request.company_name.lower():
            ipo = item
            break

    # Try partial match
    if not ipo:
        for item in data.get('ipos', []):
            if request.company_name.lower() in item.get('company_name', '').lower():
                ipo = item
                break

    if not ipo:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"IPO '{request.company_name}' not found in current listings",
        )

    # Get AI analysis
    analysis = await ipo_service.get_ai_analysis(ipo)

    return IPOAnalysisResponse(
        verdict=analysis.get('verdict', 'Neutral'),
        confidence=analysis.get('confidence', 50),
        analysis=analysis.get('analysis', ''),
        key_positives=analysis.get('key_positives', []),
        key_risks=analysis.get('key_risks', []),
        expected_listing_gain=analysis.get('expected_listing_gain'),
        recommendation_summary=analysis.get('recommendation_summary', ''),
        disclaimer=analysis.get('disclaimer', 'This is AI-generated analysis for informational purposes only.'),
    )


@router.get("/gmp", response_model=GMPListResponse)
async def get_gmp_data():
    """Get Grey Market Premium data for all active IPOs."""
    result = await ipo_service.get_gmp_data()

    return GMPListResponse(
        ipos=result.get('ipos', []),
        last_updated=result.get('last_updated'),
    )


@router.post("/refresh")
async def refresh_ipo_data():
    """Force refresh IPO data by clearing cache and re-scraping."""
    result = await ipo_service.refresh()

    return {
        "message": "IPO data refreshed",
        "total": result.get('total', 0),
        "last_updated": result.get('last_updated'),
    }
