"""Add backtest tables for historical data and strategy backtesting

Revision ID: 002
Revises: 001
Create Date: 2024-01-15 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = '002'
down_revision: Union[str, None] = '001'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Create indices table
    op.create_table(
        'indices',
        sa.Column('id', sa.String(36), nullable=False),
        sa.Column('symbol', sa.String(20), nullable=False),
        sa.Column('name', sa.String(100), nullable=False),
        sa.Column('exchange', sa.String(10), nullable=False),
        sa.Column('lot_size', sa.Integer(), nullable=False),
        sa.Column('data_available_from', sa.Date(), nullable=True),
        sa.Column('is_active', sa.Boolean(), nullable=False, server_default='true'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('symbol')
    )

    # Create historical_spot_prices table
    op.create_table(
        'historical_spot_prices',
        sa.Column('id', sa.String(36), nullable=False),
        sa.Column('symbol', sa.String(20), nullable=False),
        sa.Column('exchange', sa.String(10), nullable=False),
        sa.Column('date', sa.Date(), nullable=False),
        sa.Column('open', sa.DECIMAL(12, 2), nullable=True),
        sa.Column('high', sa.DECIMAL(12, 2), nullable=True),
        sa.Column('low', sa.DECIMAL(12, 2), nullable=True),
        sa.Column('close', sa.DECIMAL(12, 2), nullable=True),
        sa.Column('volume', sa.BigInteger(), nullable=True),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('symbol', 'date', name='uq_spot_symbol_date')
    )
    op.create_index('idx_spot_symbol_date', 'historical_spot_prices', ['symbol', 'date'])
    op.create_index('idx_spot_exchange', 'historical_spot_prices', ['exchange'])

    # Create historical_options table
    op.create_table(
        'historical_options',
        sa.Column('id', sa.String(36), nullable=False),
        sa.Column('symbol', sa.String(20), nullable=False),
        sa.Column('exchange', sa.String(10), nullable=False),
        sa.Column('date', sa.Date(), nullable=False),
        sa.Column('expiry', sa.Date(), nullable=False),
        sa.Column('strike', sa.DECIMAL(10, 2), nullable=False),
        sa.Column('option_type', sa.String(2), nullable=False),
        sa.Column('open', sa.DECIMAL(10, 2), nullable=True),
        sa.Column('high', sa.DECIMAL(10, 2), nullable=True),
        sa.Column('low', sa.DECIMAL(10, 2), nullable=True),
        sa.Column('close', sa.DECIMAL(10, 2), nullable=True),
        sa.Column('settle_price', sa.DECIMAL(10, 2), nullable=True),
        sa.Column('volume', sa.BigInteger(), nullable=True),
        sa.Column('open_interest', sa.BigInteger(), nullable=True),
        sa.Column('iv', sa.DECIMAL(8, 4), nullable=True),
        sa.Column('delta', sa.DECIMAL(8, 4), nullable=True),
        sa.Column('gamma', sa.DECIMAL(8, 4), nullable=True),
        sa.Column('theta', sa.DECIMAL(8, 4), nullable=True),
        sa.Column('vega', sa.DECIMAL(8, 4), nullable=True),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('symbol', 'date', 'expiry', 'strike', 'option_type', name='uq_option_unique')
    )
    op.create_index('idx_options_symbol_date', 'historical_options', ['symbol', 'date'])
    op.create_index('idx_options_exchange', 'historical_options', ['exchange'])
    op.create_index('idx_options_expiry', 'historical_options', ['expiry'])
    op.create_index('idx_options_strike', 'historical_options', ['strike'])

    # Create backtest_strategies table
    op.create_table(
        'backtest_strategies',
        sa.Column('id', sa.String(36), nullable=False),
        sa.Column('user_id', sa.String(36), nullable=True),
        sa.Column('name', sa.String(100), nullable=False),
        sa.Column('description', sa.Text(), nullable=True),
        sa.Column('strategy_type', sa.String(50), nullable=False),
        sa.Column('entry_rules', sa.Text(), nullable=False),  # JSON stored as text
        sa.Column('exit_rules', sa.Text(), nullable=False),   # JSON stored as text
        sa.Column('position_sizing', sa.Text(), nullable=True),  # JSON stored as text
        sa.Column('is_template', sa.Boolean(), nullable=False, server_default='false'),
        sa.Column('created_at', sa.DateTime(), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('updated_at', sa.DateTime(), nullable=False, server_default=sa.text('NOW()')),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index('idx_strategies_user', 'backtest_strategies', ['user_id'])

    # Create backtest_runs table
    op.create_table(
        'backtest_runs',
        sa.Column('id', sa.String(36), nullable=False),
        sa.Column('user_id', sa.String(36), nullable=False),
        sa.Column('strategy_id', sa.String(36), nullable=True),
        sa.Column('symbol', sa.String(20), nullable=False),
        sa.Column('start_date', sa.Date(), nullable=False),
        sa.Column('end_date', sa.Date(), nullable=False),
        sa.Column('initial_capital', sa.DECIMAL(15, 2), nullable=False),
        sa.Column('lot_size', sa.Integer(), nullable=False, server_default='25'),
        sa.Column('max_positions', sa.Integer(), nullable=False, server_default='5'),
        sa.Column('position_size_pct', sa.DECIMAL(5, 2), nullable=True),
        sa.Column('max_loss_per_trade', sa.DECIMAL(10, 2), nullable=True),
        sa.Column('max_daily_loss', sa.DECIMAL(10, 2), nullable=True),
        sa.Column('stop_loss_pct', sa.DECIMAL(5, 2), nullable=True),
        sa.Column('take_profit_pct', sa.DECIMAL(5, 2), nullable=True),
        sa.Column('strategy_config', sa.Text(), nullable=True),  # JSON stored as text
        sa.Column('status', sa.String(20), nullable=False, server_default='pending'),
        sa.Column('progress', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('error_message', sa.Text(), nullable=True),
        sa.Column('created_at', sa.DateTime(), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('started_at', sa.DateTime(), nullable=True),
        sa.Column('completed_at', sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.ForeignKeyConstraint(['strategy_id'], ['backtest_strategies.id'], ondelete='SET NULL'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index('idx_runs_user', 'backtest_runs', ['user_id'])

    # Create backtest_trades table
    op.create_table(
        'backtest_trades',
        sa.Column('id', sa.String(36), nullable=False),
        sa.Column('run_id', sa.String(36), nullable=False),
        sa.Column('entry_date', sa.Date(), nullable=False),
        sa.Column('exit_date', sa.Date(), nullable=True),
        sa.Column('expiry', sa.Date(), nullable=False),
        sa.Column('strike', sa.DECIMAL(10, 2), nullable=False),
        sa.Column('option_type', sa.String(2), nullable=False),
        sa.Column('action', sa.String(4), nullable=False),
        sa.Column('quantity', sa.Integer(), nullable=False),
        sa.Column('entry_price', sa.DECIMAL(10, 2), nullable=False),
        sa.Column('exit_price', sa.DECIMAL(10, 2), nullable=True),
        sa.Column('entry_iv', sa.DECIMAL(8, 4), nullable=True),
        sa.Column('entry_delta', sa.DECIMAL(8, 4), nullable=True),
        sa.Column('realized_pnl', sa.DECIMAL(12, 2), nullable=True),
        sa.Column('unrealized_pnl', sa.DECIMAL(12, 2), nullable=True),
        sa.Column('exit_reason', sa.String(50), nullable=True),
        sa.Column('created_at', sa.DateTime(), nullable=False, server_default=sa.text('NOW()')),
        sa.ForeignKeyConstraint(['run_id'], ['backtest_runs.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index('idx_trades_run', 'backtest_trades', ['run_id'])

    # Create backtest_results table
    op.create_table(
        'backtest_results',
        sa.Column('id', sa.String(36), nullable=False),
        sa.Column('run_id', sa.String(36), nullable=False),
        sa.Column('total_trades', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('winning_trades', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('losing_trades', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('win_rate', sa.DECIMAL(5, 2), nullable=True),
        sa.Column('total_pnl', sa.DECIMAL(15, 2), nullable=True),
        sa.Column('total_return_pct', sa.DECIMAL(8, 2), nullable=True),
        sa.Column('cagr', sa.DECIMAL(8, 2), nullable=True),
        sa.Column('max_drawdown', sa.DECIMAL(15, 2), nullable=True),
        sa.Column('max_drawdown_pct', sa.DECIMAL(8, 2), nullable=True),
        sa.Column('sharpe_ratio', sa.DECIMAL(6, 2), nullable=True),
        sa.Column('sortino_ratio', sa.DECIMAL(6, 2), nullable=True),
        sa.Column('calmar_ratio', sa.DECIMAL(6, 2), nullable=True),
        sa.Column('avg_profit', sa.DECIMAL(12, 2), nullable=True),
        sa.Column('avg_loss', sa.DECIMAL(12, 2), nullable=True),
        sa.Column('profit_factor', sa.DECIMAL(6, 2), nullable=True),
        sa.Column('avg_holding_days', sa.DECIMAL(6, 2), nullable=True),
        sa.Column('equity_curve', sa.Text(), nullable=True),  # JSON stored as text
        sa.Column('monthly_returns', sa.Text(), nullable=True),  # JSON stored as text
        sa.Column('created_at', sa.DateTime(), nullable=False, server_default=sa.text('NOW()')),
        sa.ForeignKeyConstraint(['run_id'], ['backtest_runs.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('run_id')
    )

    # Seed default indices
    op.execute("""
        INSERT INTO indices (id, symbol, name, exchange, lot_size, data_available_from, is_active)
        VALUES
        (gen_random_uuid()::text, 'NIFTY', 'NIFTY 50', 'NSE', 25, '2015-01-01', true),
        (gen_random_uuid()::text, 'BANKNIFTY', 'NIFTY Bank', 'NSE', 15, '2015-01-01', true),
        (gen_random_uuid()::text, 'MIDCPNIFTY', 'MIDCAP NIFTY', 'NSE', 50, '2016-01-01', true),
        (gen_random_uuid()::text, 'FINNIFTY', 'NIFTY Financial Services', 'NSE', 25, '2021-01-01', true),
        (gen_random_uuid()::text, 'SENSEX', 'S&P BSE SENSEX', 'BSE', 10, '2015-01-01', true)
        ON CONFLICT (symbol) DO NOTHING;
    """)

    # Seed default strategy templates
    op.execute("""
        INSERT INTO backtest_strategies (id, user_id, name, description, strategy_type, entry_rules, exit_rules, is_template, created_at, updated_at)
        VALUES
        (
            gen_random_uuid()::text,
            NULL,
            'Iron Condor - High IV',
            'Sell OTM call and put spreads when IV rank is high. Target 50% profit or 2x loss.',
            'iron_condor',
            '{"iv_rank_min": 50, "days_to_expiry": [15, 45], "wing_width": 100}',
            '{"profit_target_pct": 50, "stop_loss_pct": 200, "days_before_expiry_exit": 3}',
            true,
            NOW(),
            NOW()
        ),
        (
            gen_random_uuid()::text,
            NULL,
            'Short Straddle - Weekly',
            'Sell ATM call and put on weekly options. Exit at 50% profit or 1 day before expiry.',
            'straddle',
            '{"days_to_expiry": [5, 10]}',
            '{"profit_target_pct": 50, "days_before_expiry_exit": 1}',
            true,
            NOW(),
            NOW()
        ),
        (
            gen_random_uuid()::text,
            NULL,
            'Short Strangle - Monthly',
            'Sell OTM strangle on monthly options when IV is elevated.',
            'strangle',
            '{"iv_rank_min": 40, "days_to_expiry": [20, 45]}',
            '{"profit_target_pct": 50, "stop_loss_pct": 100, "days_before_expiry_exit": 5}',
            true,
            NOW(),
            NOW()
        )
        ON CONFLICT DO NOTHING;
    """)


def downgrade() -> None:
    op.drop_table('backtest_results')
    op.drop_table('backtest_trades')
    op.drop_table('backtest_runs')
    op.drop_table('backtest_strategies')
    op.drop_table('historical_options')
    op.drop_table('historical_spot_prices')
    op.drop_table('indices')
