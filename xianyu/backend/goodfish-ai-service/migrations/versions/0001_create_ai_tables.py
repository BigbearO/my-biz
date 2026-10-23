"""create ai conversation tables

Revision ID: 0001
Revises:
Create Date: 2026-10-23

Creates the three tables owned by the conversation service
(ai_conversation / ai_conversation_message / ai_reply_log) together with
their indexes, matching app/models exactly.
"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

from app.constants import (
    INDEX_CONVERSATION_LAST_MESSAGE_AT,
    INDEX_MESSAGE_CONVERSATION_ID,
    INDEX_REPLY_LOG_CONVERSATION_ID,
    INDEX_REPLY_LOG_CREATED_AT,
    INDEX_UK_CONVERSATION_ID,
    TABLE_AI_CONVERSATION,
    TABLE_AI_CONVERSATION_MESSAGE,
    TABLE_AI_REPLY_LOG,
)

revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        TABLE_AI_CONVERSATION,
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column("conversation_id", sa.String(length=128), nullable=False),
        sa.Column("goodfish_item_id", sa.String(length=64), nullable=False),
        sa.Column("buyer_id", sa.String(length=64), nullable=False),
        sa.Column("goodfish_account_id", sa.String(length=64), nullable=False),
        sa.Column("last_message_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    # Reinstated for the conversation list page: the list paginates by
    # last_message_at DESC, id DESC. The original design dropped this index
    # because no page consumed it; history querying brings it back.
    op.create_index(
        INDEX_CONVERSATION_LAST_MESSAGE_AT,
        TABLE_AI_CONVERSATION,
        ["last_message_at", "id"],
    )

    op.create_table(
        TABLE_AI_CONVERSATION_MESSAGE,
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column("conversation_id", sa.String(length=128), nullable=False),
        sa.Column("role", sa.String(length=16), nullable=False),
        sa.Column("content", sa.Text(), nullable=False),
        sa.Column("message_type", sa.String(length=16), nullable=False),
        sa.Column("request_id", sa.String(length=64), nullable=True),
        sa.Column("model_name", sa.String(length=64), nullable=True),
        sa.Column("prompt_tokens", sa.Integer(), nullable=True),
        sa.Column("completion_tokens", sa.Integer(), nullable=True),
        sa.Column("latency_ms", sa.Integer(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        INDEX_UK_CONVERSATION_ID,
        TABLE_AI_CONVERSATION,
        ["conversation_id"],
        unique=True,
    )
    op.create_index(
        INDEX_MESSAGE_CONVERSATION_ID,
        TABLE_AI_CONVERSATION_MESSAGE,
        ["conversation_id", "created_at"],
    )

    op.create_table(
        TABLE_AI_REPLY_LOG,
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column("request_id", sa.String(length=64), nullable=False),
        sa.Column("conversation_id", sa.String(length=128), nullable=False),
        sa.Column("account_id", sa.String(length=64), nullable=False),
        sa.Column("item_id", sa.String(length=64), nullable=False),
        sa.Column("buyer_id", sa.String(length=64), nullable=False),
        sa.Column("rendered_system_prompt", sa.Text(), nullable=False),
        sa.Column("intent", sa.String(length=16), nullable=True),
        sa.Column("model_name", sa.String(length=64), nullable=True),
        sa.Column("user_message", sa.Text(), nullable=False),
        sa.Column("ai_reply", sa.Text(), nullable=False),
        sa.Column("prompt_tokens", sa.Integer(), nullable=True),
        sa.Column("completion_tokens", sa.Integer(), nullable=True),
        sa.Column("latency_ms", sa.Integer(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        INDEX_REPLY_LOG_CONVERSATION_ID,
        TABLE_AI_REPLY_LOG,
        ["conversation_id"],
    )
    op.create_index(
        INDEX_REPLY_LOG_CREATED_AT,
        TABLE_AI_REPLY_LOG,
        ["created_at"],
    )


def downgrade() -> None:
    op.drop_index(INDEX_REPLY_LOG_CREATED_AT, table_name=TABLE_AI_REPLY_LOG)
    op.drop_index(INDEX_REPLY_LOG_CONVERSATION_ID, table_name=TABLE_AI_REPLY_LOG)
    op.drop_table(TABLE_AI_REPLY_LOG)
    op.drop_index(INDEX_MESSAGE_CONVERSATION_ID, table_name=TABLE_AI_CONVERSATION_MESSAGE)
    op.drop_table(TABLE_AI_CONVERSATION_MESSAGE)
    op.drop_index(INDEX_UK_CONVERSATION_ID, table_name=TABLE_AI_CONVERSATION)
    op.drop_index(INDEX_CONVERSATION_LAST_MESSAGE_AT, table_name=TABLE_AI_CONVERSATION)
    op.drop_table(TABLE_AI_CONVERSATION)
