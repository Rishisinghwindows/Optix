"""Initial migration - create users, sessions, and otp_requests tables

Revision ID: 001
Revises:
Create Date: 2024-01-01 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = '001'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Create users table
    op.create_table(
        'users',
        sa.Column('id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('phone', sa.String(15), nullable=True),
        sa.Column('email', sa.String(255), nullable=True),
        sa.Column('full_name', sa.String(100), nullable=True),
        sa.Column('avatar_url', sa.String(500), nullable=True),
        sa.Column('auth_provider', sa.String(20), nullable=False),
        sa.Column('provider_id', sa.String(255), nullable=True),
        sa.Column('is_verified', sa.Boolean(), nullable=False, server_default='false'),
        sa.Column('paper_trading_balance', sa.DECIMAL(15, 2), nullable=False, server_default='1000000.00'),
        sa.Column('is_premium', sa.Boolean(), nullable=False, server_default='false'),
        sa.Column('created_at', sa.DateTime(), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('updated_at', sa.DateTime(), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('last_login_at', sa.DateTime(), nullable=True),
        sa.Column('is_active', sa.Boolean(), nullable=False, server_default='true'),
        sa.PrimaryKeyConstraint('id')
    )

    op.create_index('idx_users_phone', 'users', ['phone'], unique=True)
    op.create_index('idx_users_email', 'users', ['email'], unique=True)
    op.create_index('idx_users_provider', 'users', ['auth_provider', 'provider_id'])

    # Create sessions table
    op.create_table(
        'sessions',
        sa.Column('id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('refresh_token_hash', sa.String(255), nullable=False),
        sa.Column('device_info', postgresql.JSONB(), nullable=True),
        sa.Column('ip_address', sa.String(45), nullable=True),
        sa.Column('created_at', sa.DateTime(), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('expires_at', sa.DateTime(), nullable=False),
        sa.Column('revoked_at', sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )

    op.create_index('idx_sessions_user', 'sessions', ['user_id'])
    op.create_index('idx_sessions_token', 'sessions', ['refresh_token_hash'])
    op.create_index('idx_sessions_user_active', 'sessions', ['user_id', 'revoked_at'])

    # Create otp_requests table
    op.create_table(
        'otp_requests',
        sa.Column('id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('phone', sa.String(15), nullable=False),
        sa.Column('otp_hash', sa.String(255), nullable=False),
        sa.Column('purpose', sa.String(20), nullable=False, server_default='login'),
        sa.Column('attempts', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('created_at', sa.DateTime(), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('expires_at', sa.DateTime(), nullable=False),
        sa.Column('verified_at', sa.DateTime(), nullable=True),
        sa.PrimaryKeyConstraint('id')
    )

    op.create_index('idx_otp_phone_purpose', 'otp_requests', ['phone', 'purpose'])


def downgrade() -> None:
    op.drop_table('otp_requests')
    op.drop_table('sessions')
    op.drop_table('users')
