"""
Test Cases for Algo Trading System
"""
import pytest
import asyncio
import httpx
from datetime import datetime

BASE_URL = "http://localhost:8888/api/v1/algo"

# Test configuration
TEST_CONFIG = {
    "name": "Test Strategy",
    "is_paper_mode": True,
    "capital": 100000,
    "risk_per_trade": 0.02,
    "max_daily_loss": 0.05,
    "max_positions": 2,
    "stop_loss_pct": 0.30,
    "target_pct": 0.50,
    "indices": ["NIFTY", "BANKNIFTY"]
}


class TestAlgoTrading:
    """Test suite for Algo Trading API"""

    @pytest.fixture
    def client(self):
        return httpx.Client(base_url=BASE_URL, timeout=30.0)

    # ==================== Status Tests ====================

    def test_01_get_status(self, client):
        """Test: Get algo status endpoint"""
        response = client.get("/status")
        assert response.status_code == 200
        data = response.json()

        # Verify required fields
        assert "is_running" in data
        assert "is_paper_mode" in data
        assert "current_time" in data
        assert "open_positions" in data
        assert "daily_pnl" in data
        print(f"✓ Status: Running={data['is_running']}, Paper={data['is_paper_mode']}")

    def test_02_get_risk_status(self, client):
        """Test: Get risk management status"""
        response = client.get("/risk/status")
        assert response.status_code == 200
        data = response.json()

        assert "daily_pnl" in data
        assert "daily_trades" in data
        assert "risk_per_trade" in data
        assert "stop_loss_pct" in data
        print(f"✓ Risk Status: Daily P&L={data['daily_pnl']}, Trades={data['daily_trades']}")

    # ==================== Config Tests ====================

    def test_03_create_config(self, client):
        """Test: Create new algo configuration"""
        response = client.post("/configs", json=TEST_CONFIG)
        assert response.status_code == 200
        data = response.json()

        assert "id" in data
        assert data["name"] == TEST_CONFIG["name"]
        assert data["capital"] == TEST_CONFIG["capital"]
        assert data["is_paper_mode"] == True

        # Store config ID for later tests
        TestAlgoTrading.config_id = data["id"]
        print(f"✓ Config created: {data['id']}")

    def test_04_list_configs(self, client):
        """Test: List all configurations"""
        response = client.get("/configs")
        assert response.status_code == 200
        configs = response.json()

        assert isinstance(configs, list)
        assert len(configs) >= 1
        print(f"✓ Found {len(configs)} config(s)")

    def test_05_get_config(self, client):
        """Test: Get specific configuration"""
        config_id = getattr(TestAlgoTrading, 'config_id', None)
        if not config_id:
            pytest.skip("No config ID available")

        response = client.get(f"/configs/{config_id}")
        assert response.status_code == 200
        data = response.json()

        assert data["id"] == config_id
        print(f"✓ Config retrieved: {data['name']}")

    def test_06_update_config(self, client):
        """Test: Update configuration"""
        config_id = getattr(TestAlgoTrading, 'config_id', None)
        if not config_id:
            pytest.skip("No config ID available")

        update_data = {"capital": 150000, "max_positions": 3}
        response = client.put(f"/configs/{config_id}", json=update_data)
        assert response.status_code == 200
        data = response.json()

        assert data["capital"] == 150000
        assert data["max_positions"] == 3
        print(f"✓ Config updated: Capital={data['capital']}")

    # ==================== Engine Control Tests ====================

    def test_07_start_engine(self, client):
        """Test: Start algo trading engine"""
        config_id = getattr(TestAlgoTrading, 'config_id', None)

        response = client.post("/start", json={
            "config_id": config_id,
            "paper_mode": True
        })
        assert response.status_code == 200
        data = response.json()

        assert data["success"] == True
        assert data["status"]["is_running"] == True
        assert data["status"]["is_paper_mode"] == True
        print(f"✓ Engine started in paper mode")

    def test_08_verify_engine_running(self, client):
        """Test: Verify engine is running"""
        response = client.get("/status")
        assert response.status_code == 200
        data = response.json()

        assert data["is_running"] == True
        print(f"✓ Engine running: Capital={data['total_capital']}")

    # ==================== Signal Tests ====================

    def test_09_generate_signal_nifty(self, client):
        """Test: Generate signal for NIFTY"""
        response = client.post("/signals/generate?symbol=NIFTY")
        assert response.status_code == 200
        data = response.json()

        assert data["success"] == True
        signal = data["signal"]

        assert signal["symbol"] == "NIFTY"
        assert signal["signal_type"] in ["STRONG_BUY", "BUY", "HOLD", "SELL", "STRONG_SELL"]
        assert -1 <= signal["signal_score"] <= 1
        assert signal["pcr"] > 0
        assert signal["spot_price"] > 0

        print(f"✓ NIFTY Signal: {signal['signal_type']} (Score: {signal['signal_score']:.3f})")
        print(f"  PCR: {signal['pcr']:.2f}, Max Pain: {signal['max_pain']}, VIX: {signal['vix']}")

    def test_10_generate_signal_banknifty(self, client):
        """Test: Generate signal for BANKNIFTY"""
        response = client.post("/signals/generate?symbol=BANKNIFTY")
        assert response.status_code == 200
        data = response.json()

        assert data["success"] == True
        signal = data["signal"]

        assert signal["symbol"] == "BANKNIFTY"
        print(f"✓ BANKNIFTY Signal: {signal['signal_type']} (Score: {signal['signal_score']:.3f})")

    def test_11_get_current_signal(self, client):
        """Test: Get current signal"""
        response = client.get("/signals/current")
        assert response.status_code == 200
        data = response.json()

        # Should have signal from previous test
        assert "symbol" in data or "signal" in data
        print(f"✓ Current signal retrieved")

    # ==================== Manual Trade Tests ====================

    def test_12_manual_entry(self, client):
        """Test: Place manual trade entry"""
        # First get current NIFTY data to get valid expiry
        signal_response = client.post("/signals/generate?symbol=NIFTY")
        signal_data = signal_response.json()

        if not signal_data.get("success"):
            pytest.skip("Could not get signal data")

        signal = signal_data["signal"]

        trade_request = {
            "symbol": "NIFTY",
            "strike_price": signal["recommended_strike"],
            "option_type": signal["recommended_option_type"],
            "expiry_date": signal["recommended_expiry"],
            "quantity": 1,
            "paper_mode": True
        }

        response = client.post("/manual/entry", json=trade_request)
        assert response.status_code == 200
        data = response.json()

        assert data["success"] == True
        assert "position_id" in data
        assert "order_id" in data
        assert data["entry_price"] > 0
        assert data["is_paper"] == True

        TestAlgoTrading.position_id = data["position_id"]
        print(f"✓ Manual entry: {trade_request['strike_price']} {trade_request['option_type']}")
        print(f"  Entry: ₹{data['entry_price']}, Invested: ₹{data['invested_amount']}")

    def test_13_get_open_positions(self, client):
        """Test: Get open positions"""
        response = client.get("/positions?status=open")
        assert response.status_code == 200
        positions = response.json()

        assert isinstance(positions, list)
        assert len(positions) >= 1

        position = positions[0]
        assert position["status"] == "open"
        assert position["entry_price"] > 0
        assert position["stop_loss_price"] > 0
        assert position["target_price"] > 0

        print(f"✓ Open positions: {len(positions)}")
        for p in positions:
            print(f"  {p['symbol']} {p['strike_price']} {p['option_type']} - Entry: ₹{p['entry_price']}")

    def test_14_get_open_positions_engine(self, client):
        """Test: Get open positions from engine"""
        response = client.get("/positions/open")
        assert response.status_code == 200
        positions = response.json()

        assert isinstance(positions, list)
        print(f"✓ Engine positions: {len(positions)}")

    def test_15_get_trades(self, client):
        """Test: Get trade history"""
        response = client.get("/trades?limit=10")
        assert response.status_code == 200
        trades = response.json()

        assert isinstance(trades, list)
        assert len(trades) >= 1

        trade = trades[0]
        assert trade["trade_type"] == "entry"
        assert trade["order_status"] == "executed"

        print(f"✓ Trades found: {len(trades)}")

    def test_16_get_trade_summary(self, client):
        """Test: Get trade summary"""
        response = client.get("/trades/summary")
        assert response.status_code == 200
        data = response.json()

        assert "total_trades" in data
        assert "winning_trades" in data
        assert "total_pnl" in data

        print(f"✓ Summary: {data['total_trades']} trades, P&L: ₹{data['total_pnl']}")

    # ==================== Position Management Tests ====================

    def test_17_close_position(self, client):
        """Test: Close position manually"""
        position_id = getattr(TestAlgoTrading, 'position_id', None)
        if not position_id:
            pytest.skip("No position ID available")

        response = client.post(f"/positions/close/{position_id}", json={
            "position_id": position_id,
            "reason": "test_close"
        })
        assert response.status_code == 200
        data = response.json()

        assert data["success"] == True
        print(f"✓ Position closed at ₹{data['exit_price']}")

    def test_18_verify_position_closed(self, client):
        """Test: Verify position is closed"""
        response = client.get("/positions?status=closed")
        assert response.status_code == 200
        positions = response.json()

        assert isinstance(positions, list)

        # Find our closed position
        position_id = getattr(TestAlgoTrading, 'position_id', None)
        closed_position = next((p for p in positions if p["id"] == position_id), None)

        if closed_position:
            assert closed_position["status"] == "closed"
            assert closed_position["exit_reason"] == "manual"
            print(f"✓ Position verified closed: {closed_position['symbol']}")

    # ==================== Logs Tests ====================

    def test_19_get_logs(self, client):
        """Test: Get activity logs"""
        response = client.get("/logs?limit=20")
        assert response.status_code == 200
        logs = response.json()

        assert isinstance(logs, list)
        print(f"✓ Logs: {len(logs)} entries")

    def test_20_get_daily_stats(self, client):
        """Test: Get daily statistics"""
        response = client.get("/stats/daily")
        assert response.status_code == 200
        data = response.json()

        assert "trading_date" in data
        print(f"✓ Daily stats for {data['trading_date']}")

    # ==================== Engine Stop Tests ====================

    def test_21_stop_engine(self, client):
        """Test: Stop algo trading engine"""
        response = client.post("/stop", json={
            "close_all_positions": False,
            "reason": "Test complete"
        })
        assert response.status_code == 200
        data = response.json()

        assert data["success"] == True
        print(f"✓ Engine stopped")

    def test_22_verify_engine_stopped(self, client):
        """Test: Verify engine is stopped"""
        response = client.get("/status")
        assert response.status_code == 200
        data = response.json()

        assert data["is_running"] == False
        print(f"✓ Engine verified stopped")

    # ==================== Kill Switch Test ====================

    def test_23_start_and_kill_switch(self, client):
        """Test: Start engine and activate kill switch"""
        # Start engine
        config_id = getattr(TestAlgoTrading, 'config_id', None)
        start_response = client.post("/start", json={
            "config_id": config_id,
            "paper_mode": True
        })
        assert start_response.status_code == 200

        # Activate kill switch
        response = client.post("/kill-switch")
        assert response.status_code == 200
        data = response.json()

        assert data["success"] == True
        print(f"✓ Kill switch activated")

        # Verify stopped
        status_response = client.get("/status")
        status = status_response.json()
        assert status["is_running"] == False
        print(f"✓ Engine stopped after kill switch")

    # ==================== Cleanup ====================

    def test_24_delete_config(self, client):
        """Test: Delete test configuration"""
        config_id = getattr(TestAlgoTrading, 'config_id', None)
        if not config_id:
            pytest.skip("No config ID available")

        response = client.delete(f"/configs/{config_id}")
        assert response.status_code == 200
        data = response.json()

        assert data["success"] == True
        print(f"✓ Config deleted: {config_id}")


def run_tests():
    """Run all tests and print summary"""
    print("\n" + "="*60)
    print("      ALGO TRADING SYSTEM - TEST SUITE")
    print("="*60 + "\n")

    client = httpx.Client(base_url=BASE_URL, timeout=30.0)
    test_instance = TestAlgoTrading()

    tests = [
        ("Get Status", test_instance.test_01_get_status),
        ("Get Risk Status", test_instance.test_02_get_risk_status),
        ("Create Config", test_instance.test_03_create_config),
        ("List Configs", test_instance.test_04_list_configs),
        ("Get Config", test_instance.test_05_get_config),
        ("Update Config", test_instance.test_06_update_config),
        ("Start Engine", test_instance.test_07_start_engine),
        ("Verify Running", test_instance.test_08_verify_engine_running),
        ("Generate NIFTY Signal", test_instance.test_09_generate_signal_nifty),
        ("Generate BANKNIFTY Signal", test_instance.test_10_generate_signal_banknifty),
        ("Get Current Signal", test_instance.test_11_get_current_signal),
        ("Manual Entry", test_instance.test_12_manual_entry),
        ("Get Open Positions", test_instance.test_13_get_open_positions),
        ("Get Engine Positions", test_instance.test_14_get_open_positions_engine),
        ("Get Trades", test_instance.test_15_get_trades),
        ("Get Trade Summary", test_instance.test_16_get_trade_summary),
        ("Close Position", test_instance.test_17_close_position),
        ("Verify Closed", test_instance.test_18_verify_position_closed),
        ("Get Logs", test_instance.test_19_get_logs),
        ("Get Daily Stats", test_instance.test_20_get_daily_stats),
        ("Stop Engine", test_instance.test_21_stop_engine),
        ("Verify Stopped", test_instance.test_22_verify_engine_stopped),
        ("Kill Switch", test_instance.test_23_start_and_kill_switch),
        ("Delete Config", test_instance.test_24_delete_config),
    ]

    passed = 0
    failed = 0
    errors = []

    class MockClient:
        def __init__(self, c):
            self.c = c
        def get(self, *args, **kwargs):
            return self.c.get(*args, **kwargs)
        def post(self, *args, **kwargs):
            return self.c.post(*args, **kwargs)
        def put(self, *args, **kwargs):
            return self.c.put(*args, **kwargs)
        def delete(self, *args, **kwargs):
            return self.c.delete(*args, **kwargs)

    mock_client = MockClient(client)

    for name, test_func in tests:
        try:
            print(f"\n[TEST] {name}")
            test_func(mock_client)
            passed += 1
        except AssertionError as e:
            failed += 1
            errors.append((name, str(e)))
            print(f"✗ FAILED: {e}")
        except Exception as e:
            failed += 1
            errors.append((name, str(e)))
            print(f"✗ ERROR: {e}")

    print("\n" + "="*60)
    print(f"      RESULTS: {passed} passed, {failed} failed")
    print("="*60)

    if errors:
        print("\nFailed Tests:")
        for name, error in errors:
            print(f"  - {name}: {error}")

    print()
    return failed == 0


if __name__ == "__main__":
    import sys
    success = run_tests()
    sys.exit(0 if success else 1)
