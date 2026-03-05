"""
Tests for greeks_service.py — Black-Scholes IV solver and Greeks computation.
"""

import pytest
from app.services.greeks_service import (
    BlackScholes,
    compute_greeks,
    enrich_option_chain,
    RISK_FREE_RATE,
)


# ==================== BlackScholes Unit Tests ====================


class TestBlackScholesCallPrice:
    """Test BS call option pricing."""

    def test_atm_call_has_positive_price(self):
        """ATM call should have significant time value."""
        price = BlackScholes.call_price(S=23000, K=23000, T=7/365, r=0.065, sigma=0.15)
        assert price > 0
        assert price < 500  # Shouldn't be more than ~2% of spot for 7 DTE

    def test_deep_itm_call_near_intrinsic(self):
        """Deep ITM call should be close to intrinsic value."""
        price = BlackScholes.call_price(S=23500, K=23000, T=7/365, r=0.065, sigma=0.15)
        intrinsic = 23500 - 23000  # 500
        assert price >= intrinsic * 0.95
        # Time value adds ~10-15% above intrinsic for 7 DTE
        assert price <= intrinsic * 1.15

    def test_deep_otm_call_is_cheap(self):
        """Deep OTM call should be very cheap."""
        price = BlackScholes.call_price(S=23000, K=24000, T=7/365, r=0.065, sigma=0.15)
        assert price < 10  # Nearly worthless

    def test_expired_call_returns_intrinsic(self):
        """At T=0, call should return max(0, S-K)."""
        assert BlackScholes.call_price(S=23500, K=23000, T=0, r=0.065, sigma=0.15) == 500
        assert BlackScholes.call_price(S=22500, K=23000, T=0, r=0.065, sigma=0.15) == 0

    def test_higher_vol_means_higher_price(self):
        """Higher IV should give higher option price."""
        low_vol = BlackScholes.call_price(S=23000, K=23000, T=7/365, r=0.065, sigma=0.10)
        high_vol = BlackScholes.call_price(S=23000, K=23000, T=7/365, r=0.065, sigma=0.30)
        assert high_vol > low_vol

    def test_more_time_means_higher_price(self):
        """More time to expiry should give higher price."""
        short_dte = BlackScholes.call_price(S=23000, K=23000, T=1/365, r=0.065, sigma=0.15)
        long_dte = BlackScholes.call_price(S=23000, K=23000, T=30/365, r=0.065, sigma=0.15)
        assert long_dte > short_dte


class TestBlackScholesPutPrice:
    """Test BS put option pricing."""

    def test_atm_put_has_positive_price(self):
        price = BlackScholes.put_price(S=23000, K=23000, T=7/365, r=0.065, sigma=0.15)
        assert price > 0

    def test_deep_itm_put_near_intrinsic(self):
        price = BlackScholes.put_price(S=22500, K=23000, T=7/365, r=0.065, sigma=0.15)
        intrinsic = 23000 - 22500  # 500
        assert price >= intrinsic * 0.95

    def test_expired_put_returns_intrinsic(self):
        assert BlackScholes.put_price(S=22500, K=23000, T=0, r=0.065, sigma=0.15) == 500
        assert BlackScholes.put_price(S=23500, K=23000, T=0, r=0.065, sigma=0.15) == 0


class TestImpliedVolatility:
    """Test IV solver (the critical fix)."""

    def test_iv_recovery_atm_call(self):
        """Compute a call price from known IV, then recover that IV."""
        known_iv = 0.18  # 18%
        price = BlackScholes.call_price(S=23000, K=23000, T=7/365, r=0.065, sigma=known_iv)
        recovered_iv = BlackScholes.implied_volatility(price, S=23000, K=23000, T=7/365, r=0.065, option_type="CE")
        assert recovered_iv is not None
        assert abs(recovered_iv - known_iv) < 0.001

    def test_iv_recovery_atm_put(self):
        """Same for ATM put."""
        known_iv = 0.20
        price = BlackScholes.put_price(S=23000, K=23000, T=7/365, r=0.065, sigma=known_iv)
        recovered_iv = BlackScholes.implied_volatility(price, S=23000, K=23000, T=7/365, r=0.065, option_type="PE")
        assert recovered_iv is not None
        assert abs(recovered_iv - known_iv) < 0.001

    def test_iv_recovery_otm_call(self):
        """OTM call IV recovery."""
        known_iv = 0.22
        price = BlackScholes.call_price(S=23000, K=23200, T=7/365, r=0.065, sigma=known_iv)
        recovered_iv = BlackScholes.implied_volatility(price, S=23000, K=23200, T=7/365, r=0.065, option_type="CE")
        assert recovered_iv is not None
        assert abs(recovered_iv - known_iv) < 0.005

    def test_iv_recovery_itm_put(self):
        """ITM put IV recovery."""
        known_iv = 0.25
        price = BlackScholes.put_price(S=23000, K=23200, T=7/365, r=0.065, sigma=known_iv)
        recovered_iv = BlackScholes.implied_volatility(price, S=23000, K=23200, T=7/365, r=0.065, option_type="PE")
        assert recovered_iv is not None
        assert abs(recovered_iv - known_iv) < 0.005

    def test_iv_returns_none_for_zero_price(self):
        iv = BlackScholes.implied_volatility(0, S=23000, K=23000, T=7/365, r=0.065, option_type="CE")
        assert iv is None

    def test_iv_returns_none_for_expired(self):
        iv = BlackScholes.implied_volatility(100, S=23000, K=23000, T=0, r=0.065, option_type="CE")
        assert iv is None

    def test_iv_returns_none_for_below_intrinsic(self):
        """If price is below intrinsic, IV can't be solved."""
        # Intrinsic = max(0, 23500-23000) = 500, but price is 400
        iv = BlackScholes.implied_volatility(400, S=23500, K=23000, T=7/365, r=0.065, option_type="CE")
        assert iv is None

    def test_iv_realistic_nifty_values(self):
        """Test with realistic NIFTY option prices."""
        # NIFTY ~23000, ATM CE ~200 for weekly, expect IV around 12-25%
        iv = BlackScholes.implied_volatility(200, S=23000, K=23000, T=7/365, r=0.065, option_type="CE")
        assert iv is not None
        assert 0.08 < iv < 0.40  # 8% to 40% is reasonable

    def test_iv_realistic_banknifty_values(self):
        """BANKNIFTY is more volatile."""
        iv = BlackScholes.implied_volatility(350, S=49000, K=49000, T=7/365, r=0.065, option_type="CE")
        assert iv is not None
        assert 0.05 < iv < 0.50


class TestGreeks:
    """Test individual Greeks computation."""

    def test_atm_call_delta_near_half(self):
        delta = BlackScholes.delta(S=23000, K=23000, T=7/365, r=0.065, sigma=0.15, option_type="CE")
        assert 0.45 < delta < 0.60  # ATM call delta ≈ 0.5

    def test_atm_put_delta_near_minus_half(self):
        delta = BlackScholes.delta(S=23000, K=23000, T=7/365, r=0.065, sigma=0.15, option_type="PE")
        assert -0.60 < delta < -0.40  # ATM put delta ≈ -0.5

    def test_deep_itm_call_delta_near_one(self):
        delta = BlackScholes.delta(S=24000, K=23000, T=7/365, r=0.065, sigma=0.15, option_type="CE")
        assert delta > 0.90

    def test_deep_otm_call_delta_near_zero(self):
        delta = BlackScholes.delta(S=23000, K=24000, T=7/365, r=0.065, sigma=0.15, option_type="CE")
        assert delta < 0.10

    def test_gamma_positive_and_peaks_at_atm(self):
        gamma_atm = BlackScholes.gamma(S=23000, K=23000, T=7/365, r=0.065, sigma=0.15)
        gamma_otm = BlackScholes.gamma(S=23000, K=23500, T=7/365, r=0.065, sigma=0.15)
        assert gamma_atm > 0
        assert gamma_atm > gamma_otm

    def test_theta_is_negative_for_long_options(self):
        """Theta should be negative (options lose value over time)."""
        theta_call = BlackScholes.theta(S=23000, K=23000, T=7/365, r=0.065, sigma=0.15, option_type="CE")
        theta_put = BlackScholes.theta(S=23000, K=23000, T=7/365, r=0.065, sigma=0.15, option_type="PE")
        assert theta_call < 0
        assert theta_put < 0

    def test_vega_positive(self):
        vega = BlackScholes.vega(S=23000, K=23000, T=7/365, r=0.065, sigma=0.15)
        assert vega > 0

    def test_expired_delta(self):
        assert BlackScholes.delta(S=23500, K=23000, T=0, r=0.065, sigma=0.15, option_type="CE") == 1.0
        assert BlackScholes.delta(S=22500, K=23000, T=0, r=0.065, sigma=0.15, option_type="CE") == 0.0


# ==================== compute_greeks Integration Tests ====================


class TestComputeGreeks:
    """Test the convenience function."""

    def test_returns_all_greeks_for_valid_input(self):
        result = compute_greeks(
            market_price=200, spot=23000, strike=23000, T=7/365, option_type="CE"
        )
        assert result["iv"] is not None
        assert result["delta"] is not None
        assert result["gamma"] is not None
        assert result["theta"] is not None
        assert result["vega"] is not None

    def test_returns_none_for_zero_price(self):
        result = compute_greeks(
            market_price=0, spot=23000, strike=23000, T=7/365, option_type="CE"
        )
        assert result["iv"] is None

    def test_ce_delta_is_positive(self):
        result = compute_greeks(
            market_price=200, spot=23000, strike=23000, T=7/365, option_type="CE"
        )
        assert result["delta"] > 0

    def test_pe_delta_is_negative(self):
        result = compute_greeks(
            market_price=200, spot=23000, strike=23000, T=7/365, option_type="PE"
        )
        assert result["delta"] < 0


# ==================== enrich_option_chain Tests ====================


class TestEnrichOptionChain:
    """Test the chain enrichment function (the core fix)."""

    def _make_chain(self):
        """Create a realistic mini option chain."""
        return [
            {
                "strikePrice": 22900,
                "expiryDate": "06-Mar-2026",
                "CE": {
                    "openInterest": 500000,
                    "changeinOpenInterest": 25000,
                    "totalTradedVolume": 120000,
                    "impliedVolatility": 0,  # This is what Upstox returns — always 0
                    "lastPrice": 350,
                    "change": 15,
                },
                "PE": {
                    "openInterest": 300000,
                    "changeinOpenInterest": -10000,
                    "totalTradedVolume": 80000,
                    "impliedVolatility": 0,
                    "lastPrice": 80,
                    "change": -5,
                },
            },
            {
                "strikePrice": 23000,
                "expiryDate": "06-Mar-2026",
                "CE": {
                    "openInterest": 800000,
                    "changeinOpenInterest": 50000,
                    "totalTradedVolume": 200000,
                    "impliedVolatility": 0,
                    "lastPrice": 200,
                    "change": 10,
                },
                "PE": {
                    "openInterest": 600000,
                    "changeinOpenInterest": 30000,
                    "totalTradedVolume": 150000,
                    "impliedVolatility": 0,
                    "lastPrice": 180,
                    "change": -8,
                },
            },
            {
                "strikePrice": 23100,
                "expiryDate": "06-Mar-2026",
                "CE": {
                    "openInterest": 400000,
                    "changeinOpenInterest": 15000,
                    "totalTradedVolume": 90000,
                    "impliedVolatility": 0,
                    "lastPrice": 100,
                    "change": 5,
                },
                "PE": {
                    "openInterest": 350000,
                    "changeinOpenInterest": 20000,
                    "totalTradedVolume": 70000,
                    "impliedVolatility": 0,
                    "lastPrice": 280,
                    "change": -12,
                },
            },
        ]

    def test_enrichment_fills_iv(self):
        """The main bug fix: IV should no longer be 0 after enrichment."""
        chain = self._make_chain()
        enrich_option_chain(chain, spot_price=23000, expiry_str="06-Mar-2026")

        for row in chain:
            for opt_type in ("CE", "PE"):
                opt = row[opt_type]
                if opt and opt.get("lastPrice", 0) > 0:
                    assert opt["impliedVolatility"] > 0, (
                        f"IV still 0 for {opt_type} at strike {row['strikePrice']}"
                    )

    def test_enrichment_fills_greeks(self):
        """Delta, gamma, theta, vega should be populated."""
        chain = self._make_chain()
        enrich_option_chain(chain, spot_price=23000, expiry_str="06-Mar-2026")

        atm_row = chain[1]  # 23000 strike
        for opt_type in ("CE", "PE"):
            opt = atm_row[opt_type]
            assert "delta" in opt, f"delta missing for {opt_type}"
            assert "gamma" in opt, f"gamma missing for {opt_type}"
            assert "theta" in opt, f"theta missing for {opt_type}"
            assert "vega" in opt, f"vega missing for {opt_type}"
            assert opt["delta"] is not None

    def test_atm_call_delta_reasonable(self):
        chain = self._make_chain()
        enrich_option_chain(chain, spot_price=23000, expiry_str="06-Mar-2026")

        atm_ce = chain[1]["CE"]
        assert 0.3 < atm_ce["delta"] < 0.8, f"ATM CE delta={atm_ce['delta']} not reasonable"

    def test_atm_put_delta_reasonable(self):
        chain = self._make_chain()
        enrich_option_chain(chain, spot_price=23000, expiry_str="06-Mar-2026")

        atm_pe = chain[1]["PE"]
        assert -0.8 < atm_pe["delta"] < -0.2, f"ATM PE delta={atm_pe['delta']} not reasonable"

    def test_iv_is_in_percentage(self):
        """IV should be stored as percentage (e.g., 15.5 for 15.5%)."""
        chain = self._make_chain()
        enrich_option_chain(chain, spot_price=23000, expiry_str="06-Mar-2026")

        atm_ce = chain[1]["CE"]
        # Reasonable IV range for NIFTY: 5% to 50%
        assert 5 < atm_ce["impliedVolatility"] < 50, (
            f"IV={atm_ce['impliedVolatility']} doesn't look like a percentage"
        )

    def test_skips_zero_price_options(self):
        chain = [{
            "strikePrice": 25000,
            "CE": {"lastPrice": 0, "impliedVolatility": 0},
            "PE": {"lastPrice": 0, "impliedVolatility": 0},
        }]
        enrich_option_chain(chain, spot_price=23000, expiry_str="06-Mar-2026")
        assert chain[0]["CE"]["impliedVolatility"] == 0  # Should stay 0

    def test_handles_bad_expiry_gracefully(self):
        """Should not crash on unparseable expiry date."""
        chain = self._make_chain()
        enrich_option_chain(chain, spot_price=23000, expiry_str="invalid-date")
        # Should not crash, IV stays 0
        assert chain[0]["CE"]["impliedVolatility"] == 0

    def test_preserves_existing_data(self):
        """Enrichment should not overwrite OI, volume, etc."""
        chain = self._make_chain()
        original_oi = chain[1]["CE"]["openInterest"]
        original_vol = chain[1]["CE"]["totalTradedVolume"]

        enrich_option_chain(chain, spot_price=23000, expiry_str="06-Mar-2026")

        assert chain[1]["CE"]["openInterest"] == original_oi
        assert chain[1]["CE"]["totalTradedVolume"] == original_vol

    def test_itm_option_iv(self):
        """ITM options should also get valid IV."""
        chain = self._make_chain()
        enrich_option_chain(chain, spot_price=23000, expiry_str="06-Mar-2026")

        # 22900 CE is ITM (spot 23000 > strike 22900)
        itm_ce = chain[0]["CE"]
        assert itm_ce["impliedVolatility"] > 0
        assert itm_ce["delta"] > 0.5  # ITM call delta > 0.5

    def test_otm_option_iv(self):
        """OTM options should also get valid IV."""
        chain = self._make_chain()
        enrich_option_chain(chain, spot_price=23000, expiry_str="06-Mar-2026")

        # 23100 CE is OTM (spot 23000 < strike 23100)
        otm_ce = chain[2]["CE"]
        assert otm_ce["impliedVolatility"] > 0
        assert otm_ce["delta"] < 0.5  # OTM call delta < 0.5
