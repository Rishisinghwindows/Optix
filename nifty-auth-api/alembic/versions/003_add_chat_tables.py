"""Add chat tables for AI chatbot

Revision ID: 003_add_chat_tables
Revises: 002_add_paper_trading
Create Date: 2024-02-03

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = '003_add_chat_tables'
down_revision = '002_add_paper_trading'
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Create chat_sessions table
    op.create_table(
        'chat_sessions',
        sa.Column('id', sa.String(36), primary_key=True),
        sa.Column('user_id', sa.String(36), sa.ForeignKey('users.id', ondelete='CASCADE'), nullable=True, index=True),
        sa.Column('title', sa.String(200), nullable=True),
        sa.Column('created_at', sa.DateTime(), nullable=False, server_default=sa.func.now()),
        sa.Column('updated_at', sa.DateTime(), nullable=False, server_default=sa.func.now(), onupdate=sa.func.now()),
    )

    # Create index for user sessions ordered by update time
    op.create_index('idx_chat_sessions_user_updated', 'chat_sessions', ['user_id', 'updated_at'])

    # Create chat_messages table
    op.create_table(
        'chat_messages',
        sa.Column('id', sa.String(36), primary_key=True),
        sa.Column('session_id', sa.String(36), sa.ForeignKey('chat_sessions.id', ondelete='CASCADE'), nullable=False, index=True),
        sa.Column('role', sa.Enum('USER', 'ASSISTANT', 'SYSTEM', name='messagerole'), nullable=False),
        sa.Column('content', sa.Text(), nullable=False),
        sa.Column('metadata_json', sa.Text(), nullable=True),
        sa.Column('created_at', sa.DateTime(), nullable=False, server_default=sa.func.now(), index=True),
    )

    # Create index for messages in a session ordered by time
    op.create_index('idx_chat_messages_session_created', 'chat_messages', ['session_id', 'created_at'])


def downgrade() -> None:
    op.drop_index('idx_chat_messages_session_created', 'chat_messages')
    op.drop_table('chat_messages')
    op.drop_index('idx_chat_sessions_user_updated', 'chat_sessions')
    op.drop_table('chat_sessions')
    op.execute("DROP TYPE IF EXISTS messagerole")
