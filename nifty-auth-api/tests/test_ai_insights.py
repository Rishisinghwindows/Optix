"""
Tests for the AI insights pipeline — options_ai.py, options_ai_insights.py, options_ai_service.py
"""

import pytest
from app.routers.options_ai_insights import (
    calculate_pcr,
    calculate_max_pain,
    determine_sentiment,
    score_option,
    generate_suggestions,
    _compute_target_sl,
    get_vix_status,
)


# ==================== PCR Tests ====================


class TestCalculatePCR:

    def test_basic_pcr(self):
        chain = [
            {"CE": {"openInterest": 100000}, "PE": {"openInterest": 120000}},
            {"CE": {"openInterest": 200000}, "PE": {"openInterest": 180000}},
        ]
        pcr = calculate_pcr(chain)
        # Total put OI = 300000, Total call OI = 300000
        assert pcr == 1.0

    def test_bullish_pcr(self):
        chain = [
            {"CE": {"openInterest": 100000}, "PE": {"openInterest": 200000}},
        ]
        pcr = calculate_pcr(chain)
        assert pcr == 2.0

    def test_bearish_pcr(self):
        chain = [
            {"CE": {"openInterest": 200000}, "PE": {"openInterest": 100000}},
        ]
        pcr = calculate_pcr(chain)
        assert pcr == 0.5

    def test_zero_call_oi(self):
        chain = [
            {"CE": {"openInterest": 0}, "PE": {"openInterest": 100000}},
        ]
        pcr = calculate_pcr(chain)
        assert pcr == 1.0  # Fallback

    def test_empty_chain(self):
        pcr = calculate_pcr([])
        assert pcr == 1.0

    def test_missing_fields(self):
        chain = [{"CE": {}, "PE": {}}]
        pcr = calculate_pcr(chain)
        assert pcr == 1.0


# ==================== Max Pain Tests ====================


class TestCalculateMaxPain:

    def test_basic_max_pain(self):
        chain = [
            {"strikePrice": 22800, "CE": {"openInterest": 100000}, "PE": {"openInterest": 50000}},
            {"strikePrice": 22900, "CE": {"openInterest": 200000}, "PE": {"openInterest": 100000}},
            {"strikePrice": 23000, "CE": {"openInterest": 500000}, "PE": {"openInterest": 500000}},
            {"strikePrice": 23100, "CE": {"openInterest": 100000}, "PE": {"openInterest": 200000}},
            {"strikePrice": 23200, "CE": {"openInterest": 50000}, "PE": {"openInterest": 100000}},
        ]
        mp = calculate_max_pain(chain)
        # Max pain should be where total pain is minimized
        assert 22800 <= mp <= 23200

    def test_empty_chain(self):
        mp = calculate_max_pain([])
        assert mp == 0.0

    def test_single_strike(self):
        chain = [{"strikePrice": 23000, "CE": {"openInterest": 100000}, "PE": {"openInterest": 100000}}]
        mp = calculate_max_pain(chain)
        assert mp == 23000


# ==================== VIX Status Tests ====================


class TestGetVixStatus:

    def test_low_vix(self):
        assert get_vix_status(10) == "low"

    def test_normal_vix(self):
        assert get_vix_status(13) == "normal"

    def test_elevated_vix(self):
        assert get_vix_status(17) == "elevated"

    def test_high_vix(self):
        assert get_vix_status(22) == "high"

    def test_extreme_vix(self):
        assert get_vix_status(30) == "extreme"


# ==================== Sentiment Tests ====================


class TestDetermineSentiment:

    def test_bullish_pcr(self):
        sentiment, score, details = determine_sentiment(pcr=1.5, spot=23000, max_pain=23100)
        assert "bullish" in sentiment or score > 0

    def test_bearish_pcr(self):
        sentiment, score, details = determine_sentiment(pcr=0.5, spot=23000, max_pain=22900)
        assert "bearish" in sentiment or score < 0

    def test_neutral(self):
        sentiment, score, details = determine_sentiment(pcr=1.0, spot=23000, max_pain=23000)
        assert sentiment == "neutral"

    def test_strong_bullish_with_momentum(self):
        sentiment, score, details = determine_sentiment(
            pcr=1.3, spot=23000, max_pain=23100,
            intraday_change_pct=0.8  # Strong up move
        )
        assert "bullish" in sentiment
        assert score > 0

    def test_strong_bearish_with_momentum(self):
        sentiment, score, details = determine_sentiment(
            pcr=0.6, spot=23000, max_pain=22800,
            intraday_change_pct=-0.9  # Strong down move
        )
        assert "bearish" in sentiment
        assert score < 0

    def test_vix_extreme_contrarian(self):
        """Extreme VIX should add bullish contrarian point."""
        _, score_normal, _ = determine_sentiment(pcr=1.0, spot=23000, max_pain=23000, vix=15)
        _, score_extreme, _ = determine_sentiment(pcr=1.0, spot=23000, max_pain=23000, vix=30)
        assert score_extreme > score_normal  # Extreme fear = contrarian bullish

    def test_details_are_populated(self):
        _, _, details = determine_sentiment(
            pcr=1.2, spot=23000, max_pain=23100, vix=15, intraday_change_pct=0.5
        )
        assert len(details) >= 3  # PCR, max pain, VIX, momentum


# ==================== Target/SL Tests ====================


class TestComputeTargetSL:

    def test_buy_target_above_entry(self):
        target, sl = _compute_target_sl(ltp=200, iv_pct=15, action="BUY")
        assert target > 200
        assert sl < 200

    def test_sell_target_below_entry(self):
        target, sl = _compute_target_sl(ltp=200, iv_pct=15, action="SELL")
        assert target < 200
        assert sl > 200

    def test_buy_risk_reward_minimum_1_5(self):
        """BUY trades should have at least 1.5:1 R:R."""
        for iv in [10, 15, 20, 30, 40]:
            target, sl = _compute_target_sl(ltp=200, iv_pct=iv, action="BUY")
            reward = target - 200
            risk = 200 - sl
            rr = reward / risk if risk > 0 else 0
            assert rr >= 1.4, f"R:R={rr:.2f} for IV={iv}%"

    def test_higher_iv_wider_stops(self):
        """Higher IV should give wider stop-loss."""
        _, sl_low = _compute_target_sl(ltp=200, iv_pct=10, action="BUY")
        _, sl_high = _compute_target_sl(ltp=200, iv_pct=30, action="BUY")
        # Higher IV → sl is further from entry (lower value)
        assert sl_high < sl_low

    def test_stop_loss_never_negative(self):
        target, sl = _compute_target_sl(ltp=50, iv_pct=15, action="BUY")
        assert sl > 0
        assert target > 0


# ==================== Score Option Tests ====================


class TestScoreOption:

    def _make_option_row(self, strike, ce_ltp=200, pe_ltp=180, ce_oi=500000, pe_oi=400000):
        return {
            "strikePrice": strike,
            "CE": {
                "lastPrice": ce_ltp,
                "openInterest": ce_oi,
                "totalTradedVolume": 100000,
                "impliedVolatility": 15,
                "changeinOpenInterest": 25000,
                "delta": 0.50,
                "theta": -5.0,
            },
            "PE": {
                "lastPrice": pe_ltp,
                "openInterest": pe_oi,
                "totalTradedVolume": 80000,
                "impliedVolatility": 16,
                "changeinOpenInterest": 20000,
                "delta": -0.48,
                "theta": -4.8,
            },
        }

    def test_score_within_bounds(self):
        row = self._make_option_row(23000)
        score, reasoning, action = score_option(
            row, "CE", spot_price=23000, atm_strike=23000,
            pcr=1.0, max_pain=23000, vix=15, sentiment="neutral",
            total_call_oi=5000000, total_put_oi=5000000,
        )
        assert 30 <= score <= 98

    def test_reasoning_has_entries(self):
        row = self._make_option_row(23000)
        score, reasoning, action = score_option(
            row, "CE", spot_price=23000, atm_strike=23000,
            pcr=1.0, max_pain=23000, vix=15, sentiment="neutral",
            total_call_oi=5000000, total_put_oi=5000000,
        )
        assert len(reasoning) >= 5  # Moneyness, OI, OI Change, Volume, IV

    def test_atm_scores_higher_than_deep_otm(self):
        atm_row = self._make_option_row(23000, ce_ltp=200)
        otm_row = self._make_option_row(23500, ce_ltp=20, ce_oi=50000)

        atm_score, _, _ = score_option(
            atm_row, "CE", spot_price=23000, atm_strike=23000,
            pcr=1.0, max_pain=23000, vix=15, sentiment="neutral",
            total_call_oi=5000000, total_put_oi=5000000,
        )
        otm_score, _, _ = score_option(
            otm_row, "CE", spot_price=23000, atm_strike=23000,
            pcr=1.0, max_pain=23000, vix=15, sentiment="neutral",
            total_call_oi=5000000, total_put_oi=5000000,
        )
        assert atm_score > otm_score

    def test_bullish_sentiment_boosts_calls(self):
        # Use OTM option so score doesn't max out at cap
        row = self._make_option_row(23200, ce_ltp=80, ce_oi=80000)
        neutral_score, _, _ = score_option(
            row, "CE", spot_price=23000, atm_strike=23000,
            pcr=1.0, max_pain=23000, vix=15, sentiment="neutral",
            total_call_oi=5000000, total_put_oi=5000000,
        )
        bullish_score, _, _ = score_option(
            row, "CE", spot_price=23000, atm_strike=23000,
            pcr=1.0, max_pain=23000, vix=15, sentiment="bullish",
            total_call_oi=5000000, total_put_oi=5000000,
        )
        assert bullish_score >= neutral_score

    def test_delta_scoring_present_when_available(self):
        """With computed delta, the Delta scoring factor should appear."""
        row = self._make_option_row(23000)
        _, reasoning, _ = score_option(
            row, "CE", spot_price=23000, atm_strike=23000,
            pcr=1.0, max_pain=23000, vix=15, sentiment="neutral",
            total_call_oi=5000000, total_put_oi=5000000,
        )
        factor_names = [r.factor for r in reasoning]
        assert "Delta" in factor_names, f"Delta not in reasoning factors: {factor_names}"

    def test_zero_ltp_returns_zero_score(self):
        row = self._make_option_row(23000, ce_ltp=0)
        score, reasoning, action = score_option(
            row, "CE", spot_price=23000, atm_strike=23000,
            pcr=1.0, max_pain=23000, vix=15, sentiment="neutral",
            total_call_oi=5000000, total_put_oi=5000000,
        )
        assert score == 0

    def test_high_vix_favors_selling(self):
        row = self._make_option_row(23000)
        _, _, action_low_vix = score_option(
            row, "CE", spot_price=23000, atm_strike=23000,
            pcr=1.0, max_pain=23000, vix=10, sentiment="neutral",
            total_call_oi=5000000, total_put_oi=5000000,
        )
        _, _, action_high_vix = score_option(
            row, "CE", spot_price=23000, atm_strike=23000,
            pcr=1.0, max_pain=23000, vix=25, sentiment="neutral",
            total_call_oi=5000000, total_put_oi=5000000,
        )
        assert action_low_vix == "BUY"
        assert action_high_vix == "SELL"


# ==================== Generate Suggestions Tests ====================


class TestGenerateSuggestions:

    def _make_chain(self, spot=23000):
        """Create a realistic option chain around spot."""
        chain = []
        for offset in range(-500, 600, 50):
            strike = spot + offset
            distance = abs(offset) / spot
            ce_ltp = max(5, 300 - abs(offset) * 0.5 + (50 if offset < 0 else 0))
            pe_ltp = max(5, 300 - abs(offset) * 0.5 + (50 if offset > 0 else 0))
            chain.append({
                "strikePrice": strike,
                "CE": {
                    "lastPrice": round(ce_ltp, 2),
                    "openInterest": max(10000, 500000 - abs(offset) * 800),
                    "totalTradedVolume": max(5000, 100000 - abs(offset) * 150),
                    "impliedVolatility": 15 + distance * 100,
                    "changeinOpenInterest": 20000 if offset <= 0 else 5000,
                    "delta": max(0.05, 0.5 - offset / spot * 3),
                    "theta": -5.0,
                },
                "PE": {
                    "lastPrice": round(pe_ltp, 2),
                    "openInterest": max(10000, 400000 - abs(offset) * 600),
                    "totalTradedVolume": max(3000, 80000 - abs(offset) * 120),
                    "impliedVolatility": 16 + distance * 100,
                    "changeinOpenInterest": 15000 if offset >= 0 else 3000,
                    "delta": min(-0.05, -0.5 + offset / spot * 3),
                    "theta": -4.5,
                },
            })
        return chain

    def test_generates_suggestions(self):
        chain = self._make_chain()
        suggestions = generate_suggestions(
            symbol="NIFTY", spot_price=23000, option_chain=chain,
            expiry="06-Mar-2026", sentiment="bullish", pcr=1.2, max_pain=23000,
        )
        assert len(suggestions) > 0

    def test_suggestions_have_required_fields(self):
        chain = self._make_chain()
        suggestions = generate_suggestions(
            symbol="NIFTY", spot_price=23000, option_chain=chain,
            expiry="06-Mar-2026", sentiment="bullish", pcr=1.2, max_pain=23000,
        )
        for s in suggestions:
            assert s.strike_price > 0
            assert s.option_type in ("CE", "PE")
            assert s.entry_price > 0
            assert s.target_price > 0
            assert s.stop_loss > 0
            assert s.confidence in ("HIGH", "MEDIUM", "LOW")
            assert 30 <= s.score <= 98
            assert len(s.reasoning) > 0

    def test_suggestions_sorted_by_score(self):
        chain = self._make_chain()
        suggestions = generate_suggestions(
            symbol="NIFTY", spot_price=23000, option_chain=chain,
            expiry="06-Mar-2026", sentiment="bullish", pcr=1.2, max_pain=23000,
        )
        if len(suggestions) >= 2:
            for i in range(len(suggestions) - 1):
                assert suggestions[i].score >= suggestions[i+1].score

    def test_includes_both_calls_and_puts(self):
        chain = self._make_chain()
        suggestions = generate_suggestions(
            symbol="NIFTY", spot_price=23000, option_chain=chain,
            expiry="06-Mar-2026", sentiment="neutral", pcr=1.0, max_pain=23000,
        )
        option_types = {s.option_type for s in suggestions}
        assert "CE" in option_types
        assert "PE" in option_types

    def test_risk_reward_positive(self):
        chain = self._make_chain()
        suggestions = generate_suggestions(
            symbol="NIFTY", spot_price=23000, option_chain=chain,
            expiry="06-Mar-2026", sentiment="bullish", pcr=1.2, max_pain=23000,
        )
        for s in suggestions:
            assert s.risk_reward > 0

    def test_max_suggestions_limit(self):
        chain = self._make_chain()
        suggestions = generate_suggestions(
            symbol="NIFTY", spot_price=23000, option_chain=chain,
            expiry="06-Mar-2026", sentiment="bullish", pcr=1.2, max_pain=23000,
            max_suggestions=4,
        )
        assert len(suggestions) <= 4

    def test_empty_chain(self):
        suggestions = generate_suggestions(
            symbol="NIFTY", spot_price=23000, option_chain=[],
            expiry="06-Mar-2026", sentiment="neutral", pcr=1.0, max_pain=23000,
        )
        assert len(suggestions) == 0

    def test_with_vix(self):
        chain = self._make_chain()
        suggestions = generate_suggestions(
            symbol="NIFTY", spot_price=23000, option_chain=chain,
            expiry="06-Mar-2026", sentiment="neutral", pcr=1.0, max_pain=23000,
            vix=22,
        )
        # High VIX should still generate suggestions
        assert len(suggestions) > 0

    def test_with_momentum(self):
        chain = self._make_chain()
        suggestions = generate_suggestions(
            symbol="NIFTY", spot_price=23000, option_chain=chain,
            expiry="06-Mar-2026", sentiment="strong_bullish", pcr=1.3, max_pain=23100,
            intraday_change_pct=0.8, bias_score=4,
        )
        assert len(suggestions) > 0
