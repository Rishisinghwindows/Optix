"""Service for calculating backtest performance metrics."""

import math
from datetime import date, timedelta
from typing import List, Dict, Any, Optional
from decimal import Decimal
from collections import defaultdict


class MetricsService:
    """Service for calculating trading performance metrics."""

    TRADING_DAYS_PER_YEAR = 252
    RISK_FREE_RATE = 0.05  # 5% annual risk-free rate

    @staticmethod
    def calculate_returns(equity_curve: List[Dict[str, Any]]) -> List[float]:
        """Calculate daily returns from equity curve."""
        if len(equity_curve) < 2:
            return []

        returns = []
        for i in range(1, len(equity_curve)):
            prev_equity = equity_curve[i - 1]["equity"]
            curr_equity = equity_curve[i]["equity"]
            if prev_equity > 0:
                daily_return = (curr_equity - prev_equity) / prev_equity
                returns.append(daily_return)
        return returns

    @staticmethod
    def calculate_total_return(
        initial_capital: float,
        final_equity: float,
    ) -> float:
        """Calculate total return percentage."""
        if initial_capital <= 0:
            return 0.0
        return ((final_equity - initial_capital) / initial_capital) * 100

    @staticmethod
    def calculate_cagr(
        initial_capital: float,
        final_equity: float,
        years: float,
    ) -> float:
        """Calculate Compound Annual Growth Rate."""
        if initial_capital <= 0 or years <= 0 or final_equity <= 0:
            return 0.0

        return ((final_equity / initial_capital) ** (1 / years) - 1) * 100

    @staticmethod
    def calculate_max_drawdown(equity_curve: List[Dict[str, Any]]) -> tuple[float, float]:
        """
        Calculate maximum drawdown.
        Returns (max_drawdown_amount, max_drawdown_percentage).
        """
        if not equity_curve:
            return 0.0, 0.0

        peak = equity_curve[0]["equity"]
        max_dd = 0.0
        max_dd_pct = 0.0

        for point in equity_curve:
            equity = point["equity"]
            if equity > peak:
                peak = equity

            drawdown = peak - equity
            drawdown_pct = (drawdown / peak * 100) if peak > 0 else 0

            if drawdown > max_dd:
                max_dd = drawdown
                max_dd_pct = drawdown_pct

        return max_dd, max_dd_pct

    @staticmethod
    def calculate_sharpe_ratio(
        returns: List[float],
        risk_free_rate: float = None,
    ) -> float:
        """
        Calculate Sharpe Ratio.
        Sharpe = (Mean Return - Risk Free Rate) / Std Dev of Returns
        Annualized.
        """
        if not returns or len(returns) < 2:
            return 0.0

        if risk_free_rate is None:
            risk_free_rate = MetricsService.RISK_FREE_RATE

        daily_rf = risk_free_rate / MetricsService.TRADING_DAYS_PER_YEAR
        mean_return = sum(returns) / len(returns)
        excess_return = mean_return - daily_rf

        # Calculate standard deviation
        variance = sum((r - mean_return) ** 2 for r in returns) / len(returns)
        std_dev = math.sqrt(variance)

        if std_dev == 0:
            return 0.0

        # Annualize
        sharpe = (excess_return / std_dev) * math.sqrt(MetricsService.TRADING_DAYS_PER_YEAR)
        return round(sharpe, 2)

    @staticmethod
    def calculate_sortino_ratio(
        returns: List[float],
        risk_free_rate: float = None,
    ) -> float:
        """
        Calculate Sortino Ratio.
        Like Sharpe but only considers downside volatility.
        """
        if not returns or len(returns) < 2:
            return 0.0

        if risk_free_rate is None:
            risk_free_rate = MetricsService.RISK_FREE_RATE

        daily_rf = risk_free_rate / MetricsService.TRADING_DAYS_PER_YEAR
        mean_return = sum(returns) / len(returns)
        excess_return = mean_return - daily_rf

        # Calculate downside deviation (only negative returns)
        negative_returns = [r for r in returns if r < 0]
        if not negative_returns:
            return float('inf') if excess_return > 0 else 0.0

        downside_variance = sum(r ** 2 for r in negative_returns) / len(returns)
        downside_deviation = math.sqrt(downside_variance)

        if downside_deviation == 0:
            return 0.0

        # Annualize
        sortino = (excess_return / downside_deviation) * math.sqrt(MetricsService.TRADING_DAYS_PER_YEAR)
        return round(sortino, 2)

    @staticmethod
    def calculate_calmar_ratio(
        cagr: float,
        max_drawdown_pct: float,
    ) -> float:
        """
        Calculate Calmar Ratio.
        Calmar = CAGR / Max Drawdown
        """
        if max_drawdown_pct <= 0:
            return 0.0
        return round(cagr / max_drawdown_pct, 2)

    @staticmethod
    def calculate_profit_factor(
        winning_pnl: float,
        losing_pnl: float,
    ) -> float:
        """
        Calculate Profit Factor.
        Profit Factor = Gross Profits / Gross Losses
        """
        if losing_pnl == 0:
            return float('inf') if winning_pnl > 0 else 0.0
        return round(abs(winning_pnl / losing_pnl), 2)

    @staticmethod
    def calculate_win_rate(
        winning_trades: int,
        total_trades: int,
    ) -> float:
        """Calculate win rate percentage."""
        if total_trades == 0:
            return 0.0
        return round((winning_trades / total_trades) * 100, 2)

    @staticmethod
    def calculate_average_trade(
        trades: List[Dict[str, Any]],
    ) -> Dict[str, float]:
        """Calculate average winning and losing trade amounts."""
        winning_trades = [t for t in trades if t.get("realized_pnl", 0) > 0]
        losing_trades = [t for t in trades if t.get("realized_pnl", 0) < 0]

        avg_profit = 0.0
        if winning_trades:
            avg_profit = sum(t["realized_pnl"] for t in winning_trades) / len(winning_trades)

        avg_loss = 0.0
        if losing_trades:
            avg_loss = sum(t["realized_pnl"] for t in losing_trades) / len(losing_trades)

        return {
            "avg_profit": round(avg_profit, 2),
            "avg_loss": round(avg_loss, 2),
        }

    @staticmethod
    def calculate_average_holding_period(
        trades: List[Dict[str, Any]],
    ) -> float:
        """Calculate average holding period in days."""
        closed_trades = [
            t for t in trades
            if t.get("entry_date") and t.get("exit_date")
        ]

        if not closed_trades:
            return 0.0

        total_days = sum(
            (t["exit_date"] - t["entry_date"]).days
            for t in closed_trades
        )
        return round(total_days / len(closed_trades), 2)

    @staticmethod
    def calculate_monthly_returns(
        equity_curve: List[Dict[str, Any]],
    ) -> List[Dict[str, Any]]:
        """Calculate monthly returns from equity curve."""
        if not equity_curve:
            return []

        monthly_data = defaultdict(list)
        for point in equity_curve:
            date_str = point["date"]
            if isinstance(date_str, str):
                month_key = date_str[:7]  # YYYY-MM
            else:
                month_key = date_str.strftime("%Y-%m")
            monthly_data[month_key].append(point["equity"])

        monthly_returns = []
        sorted_months = sorted(monthly_data.keys())

        for i, month in enumerate(sorted_months):
            equities = monthly_data[month]
            if i == 0:
                # First month: calculate from first to last day of month
                start_equity = equities[0]
            else:
                # Use last equity of previous month
                prev_month = sorted_months[i - 1]
                start_equity = monthly_data[prev_month][-1]

            end_equity = equities[-1]

            if start_equity > 0:
                return_pct = ((end_equity - start_equity) / start_equity) * 100
            else:
                return_pct = 0.0

            monthly_returns.append({
                "month": month,
                "return_pct": round(return_pct, 2),
            })

        return monthly_returns

    @staticmethod
    def calculate_drawdown_curve(
        equity_curve: List[Dict[str, Any]],
    ) -> List[Dict[str, Any]]:
        """Add drawdown to each point in equity curve."""
        if not equity_curve:
            return []

        result = []
        peak = equity_curve[0]["equity"]

        for point in equity_curve:
            equity = point["equity"]
            if equity > peak:
                peak = equity

            drawdown = peak - equity
            drawdown_pct = (drawdown / peak * 100) if peak > 0 else 0

            result.append({
                **point,
                "drawdown": round(drawdown, 2),
                "drawdown_pct": round(drawdown_pct, 2),
            })

        return result

    @staticmethod
    def calculate_all_metrics(
        trades: List[Dict[str, Any]],
        equity_curve: List[Dict[str, Any]],
        initial_capital: float,
        start_date: date,
        end_date: date,
    ) -> Dict[str, Any]:
        """Calculate all backtest metrics."""
        # Basic counts
        total_trades = len(trades)
        winning_trades = len([t for t in trades if t.get("realized_pnl", 0) > 0])
        losing_trades = len([t for t in trades if t.get("realized_pnl", 0) < 0])

        # P&L
        total_pnl = sum(t.get("realized_pnl", 0) for t in trades)
        winning_pnl = sum(
            t.get("realized_pnl", 0) for t in trades
            if t.get("realized_pnl", 0) > 0
        )
        losing_pnl = sum(
            t.get("realized_pnl", 0) for t in trades
            if t.get("realized_pnl", 0) < 0
        )

        # Final equity
        final_equity = equity_curve[-1]["equity"] if equity_curve else initial_capital

        # Time period in years
        years = (end_date - start_date).days / 365.25

        # Calculate metrics
        total_return_pct = MetricsService.calculate_total_return(
            initial_capital, final_equity
        )
        cagr = MetricsService.calculate_cagr(initial_capital, final_equity, years)
        max_dd, max_dd_pct = MetricsService.calculate_max_drawdown(equity_curve)

        returns = MetricsService.calculate_returns(equity_curve)
        sharpe = MetricsService.calculate_sharpe_ratio(returns)
        sortino = MetricsService.calculate_sortino_ratio(returns)
        calmar = MetricsService.calculate_calmar_ratio(cagr, max_dd_pct)

        win_rate = MetricsService.calculate_win_rate(winning_trades, total_trades)
        profit_factor = MetricsService.calculate_profit_factor(winning_pnl, losing_pnl)
        avg_trade = MetricsService.calculate_average_trade(trades)
        avg_holding = MetricsService.calculate_average_holding_period(trades)

        monthly_returns = MetricsService.calculate_monthly_returns(equity_curve)
        equity_with_dd = MetricsService.calculate_drawdown_curve(equity_curve)

        return {
            "total_trades": total_trades,
            "winning_trades": winning_trades,
            "losing_trades": losing_trades,
            "win_rate": win_rate,
            "total_pnl": round(total_pnl, 2),
            "total_return_pct": round(total_return_pct, 2),
            "cagr": round(cagr, 2),
            "max_drawdown": round(max_dd, 2),
            "max_drawdown_pct": round(max_dd_pct, 2),
            "sharpe_ratio": sharpe,
            "sortino_ratio": sortino,
            "calmar_ratio": calmar,
            "avg_profit": avg_trade["avg_profit"],
            "avg_loss": avg_trade["avg_loss"],
            "profit_factor": profit_factor,
            "avg_holding_days": avg_holding,
            "equity_curve": equity_with_dd,
            "monthly_returns": monthly_returns,
        }


metrics_service = MetricsService()
