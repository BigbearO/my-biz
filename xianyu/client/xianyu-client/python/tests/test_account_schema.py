"""Tests for the local Goodfish account schema."""

from datetime import datetime

import pytest
from sqlalchemy import create_engine, inspect, select
from sqlalchemy.exc import IntegrityError

from xianyu_client.core.constants import (
    ACCOUNT_GOODFISH_ID_INDEX_NAME,
    ACCOUNT_INITIAL_IS_VALID,
    ACCOUNT_INITIAL_REMARK,
    ACCOUNT_LISTEN_STATE_IDLE,
    ACCOUNT_TABLE_NAME,
    DATABASE_FILE_NAME,
)
from xianyu_client.db.migrations import run_migrations
from xianyu_client.db.models import GoodfishAccountModel
from xianyu_client.db.session import create_database_engine, create_session_maker


TEST_GOODFISH_ID = "2038890"
TEST_GOODFISH_NICKNAME = "buyer-one"
TEST_ENCRYPTED_COOKIE = "dmFsaWQtY3J5cHRvZGF0YQ=="
DUPLICATE_GOODFISH_ID = TEST_GOODFISH_ID
async def test_account_migration_creates_columns_and_unique_index(tmp_path):
    """The account migration creates nullable-safe columns and a unique index."""
    database_path = tmp_path / DATABASE_FILE_NAME
    run_migrations(database_path)
    engine = create_engine(f"sqlite:///{database_path.as_posix()}", future=True)

    try:
        inspector = inspect(engine)
        columns = {column["name"]: column for column in inspector.get_columns(ACCOUNT_TABLE_NAME)}
        indexes = inspector.get_indexes(ACCOUNT_TABLE_NAME)
    finally:
        engine.dispose()

    assert ACCOUNT_TABLE_NAME in inspector.get_table_names()
    assert set(columns) == {
        "id",
        "goodfish_id",
        "goodfish_nickname",
        "cookie",
        "remark",
        "is_valid",
        "listen_state",
        "last_check_at",
        "created_at",
        "updated_at",
    }
    assert all(not column["nullable"] for column in columns.values())
    assert any(index["name"] == ACCOUNT_GOODFISH_ID_INDEX_NAME and index["unique"] for index in indexes)


async def test_account_model_supplies_safe_defaults(tmp_path):
    """New ORM accounts receive inactive listening and validation defaults."""
    database_path = tmp_path / DATABASE_FILE_NAME
    run_migrations(database_path)
    engine = create_database_engine(database_path)
    session_factory = create_session_maker(engine)

    async with session_factory() as session:
        session.add(
            GoodfishAccountModel(
                goodfish_id=TEST_GOODFISH_ID,
                goodfish_nickname=TEST_GOODFISH_NICKNAME,
                cookie=TEST_ENCRYPTED_COOKIE,
            )
        )
        await session.commit()
        account = (await session.execute(select(GoodfishAccountModel))).scalar_one()

    assert account.remark == ACCOUNT_INITIAL_REMARK
    assert account.is_valid is ACCOUNT_INITIAL_IS_VALID
    assert account.listen_state == ACCOUNT_LISTEN_STATE_IDLE
    assert isinstance(account.last_check_at, datetime)
    assert isinstance(account.created_at, datetime)
    assert isinstance(account.updated_at, datetime)
    assert not account.is_listening_candidate()
    await engine.dispose()


async def test_goodfish_id_is_unique(tmp_path):
    """A second local row cannot reuse an existing Goodfish ID."""
    database_path = tmp_path / DATABASE_FILE_NAME
    run_migrations(database_path)
    engine = create_database_engine(database_path)
    session_factory = create_session_maker(engine)

    async with session_factory() as session:
        session.add(
            GoodfishAccountModel(
                goodfish_id=TEST_GOODFISH_ID,
                goodfish_nickname=TEST_GOODFISH_NICKNAME,
                cookie=TEST_ENCRYPTED_COOKIE,
            )
        )
        await session.commit()
        session.add(
            GoodfishAccountModel(
                goodfish_id=DUPLICATE_GOODFISH_ID,
                goodfish_nickname=TEST_GOODFISH_NICKNAME,
                cookie=TEST_ENCRYPTED_COOKIE,
            )
        )
        with pytest.raises(IntegrityError):
            await session.commit()
        await session.rollback()
        count = len((await session.execute(select(GoodfishAccountModel))).scalars().all())
        assert count == 1
    await engine.dispose()
