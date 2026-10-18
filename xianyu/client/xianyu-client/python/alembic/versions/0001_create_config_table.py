"""Create local config table

Revision ID: 0001
Revises:
Create Date: 2026-10-17
"""

from alembic import op
import sqlalchemy as sa

from xianyu_client.core.constants import (
    CONFIG_DESCRIPTION_MAX_LENGTH,
    CONFIG_KEY_INDEX_NAME,
    CONFIG_KEY_MAX_LENGTH,
    CONFIG_TABLE_NAME,
)

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    """Create the key-value table used by local runtime settings."""
    op.create_table(
        CONFIG_TABLE_NAME,
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("key", sa.String(length=CONFIG_KEY_MAX_LENGTH), nullable=False),
        sa.Column("value", sa.Text(), nullable=False),
        sa.Column("desc", sa.String(length=CONFIG_DESCRIPTION_MAX_LENGTH), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("key", name="uq_config_key"),
    )
    op.create_index(CONFIG_KEY_INDEX_NAME, CONFIG_TABLE_NAME, ["key"], unique=True)


def downgrade() -> None:
    """Drop the local config table."""
    op.drop_index(CONFIG_KEY_INDEX_NAME, table_name=CONFIG_TABLE_NAME)
    op.drop_table(CONFIG_TABLE_NAME)

