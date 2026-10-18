"""Create local Goodfish account table

Revision ID: 0002
Revises: 0001
Create Date: 2026-10-18
"""

from alembic import op
import sqlalchemy as sa

from xianyu_client.core.constants import (
    ACCOUNT_GOODFISH_ID_INDEX_NAME,
    ACCOUNT_GOODFISH_ID_MAX_LENGTH,
    ACCOUNT_TABLE_NAME,
    ACCOUNT_GOODFISH_NICKNAME_MAX_LENGTH,
    ACCOUNT_INITIAL_IS_VALID_SQL,
    ACCOUNT_INITIAL_REMARK,
    ACCOUNT_LISTEN_STATE_IDLE,
    ACCOUNT_LISTEN_STATE_MAX_LENGTH,
    ACCOUNT_REMARK_MAX_LENGTH,
)

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    """Create the local encrypted-cookie account table."""
    op.create_table(
        ACCOUNT_TABLE_NAME,
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("goodfish_id", sa.String(length=ACCOUNT_GOODFISH_ID_MAX_LENGTH), nullable=False),
        sa.Column(
            "goodfish_nickname",
            sa.String(length=ACCOUNT_GOODFISH_NICKNAME_MAX_LENGTH),
            nullable=False,
        ),
        sa.Column("cookie", sa.Text(), nullable=False),
        sa.Column(
            "remark",
            sa.String(length=ACCOUNT_REMARK_MAX_LENGTH),
            nullable=False,
            server_default=ACCOUNT_INITIAL_REMARK,
        ),
        sa.Column(
            "is_valid",
            sa.Boolean(),
            nullable=False,
            server_default=ACCOUNT_INITIAL_IS_VALID_SQL,
        ),
        sa.Column(
            "listen_state",
            sa.String(length=ACCOUNT_LISTEN_STATE_MAX_LENGTH),
            nullable=False,
            server_default=ACCOUNT_LISTEN_STATE_IDLE,
        ),
        sa.Column("last_check_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        ACCOUNT_GOODFISH_ID_INDEX_NAME,
        ACCOUNT_TABLE_NAME,
        ["goodfish_id"],
        unique=True,
    )


def downgrade() -> None:
    """Drop the local Goodfish account table."""
    op.drop_index(ACCOUNT_GOODFISH_ID_INDEX_NAME, table_name=ACCOUNT_TABLE_NAME)
    op.drop_table(ACCOUNT_TABLE_NAME)
