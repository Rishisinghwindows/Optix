"""Add visitor tracking tables

Revision ID: 004
Revises: 003_add_chat_tables
Create Date: 2026-02-15
"""
from alembic import op
import sqlalchemy as sa

# revision identifiers
revision = '004'
down_revision = '003'
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Create visitors table
    op.create_table(
        'visitors',
        sa.Column('id', sa.String(36), primary_key=True),
        sa.Column('visitor_id', sa.String(64), nullable=False, index=True),
        sa.Column('ip_address', sa.String(45), nullable=True),
        sa.Column('user_agent', sa.Text, nullable=True),
        sa.Column('referrer', sa.String(500), nullable=True),
        sa.Column('page_url', sa.String(500), nullable=True),
        sa.Column('page_title', sa.String(200), nullable=True),
        sa.Column('device_type', sa.String(20), nullable=True),
        sa.Column('browser', sa.String(50), nullable=True),
        sa.Column('os', sa.String(50), nullable=True),
        sa.Column('country', sa.String(100), nullable=True),
        sa.Column('city', sa.String(100), nullable=True),
        sa.Column('session_id', sa.String(64), nullable=True, index=True),
        sa.Column('is_new_visitor', sa.Boolean, default=True),
        sa.Column('visited_at', sa.DateTime, nullable=False, index=True),
    )

    # Create page_views table
    op.create_table(
        'page_views',
        sa.Column('id', sa.String(36), primary_key=True),
        sa.Column('visitor_id', sa.String(64), nullable=False, index=True),
        sa.Column('session_id', sa.String(64), nullable=True),
        sa.Column('page_url', sa.String(500), nullable=False),
        sa.Column('page_title', sa.String(200), nullable=True),
        sa.Column('time_spent', sa.Integer, nullable=True),
        sa.Column('viewed_at', sa.DateTime, nullable=False, index=True),
    )

    # Create additional indexes
    op.create_index('idx_visitors_visitor_date', 'visitors', ['visitor_id', 'visited_at'])
    op.create_index('idx_pageviews_date', 'page_views', ['viewed_at'])


def downgrade() -> None:
    op.drop_index('idx_pageviews_date', 'page_views')
    op.drop_index('idx_visitors_visitor_date', 'visitors')
    op.drop_table('page_views')
    op.drop_table('visitors')
