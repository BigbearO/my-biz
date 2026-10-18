"""Tests for startup verification of stored cookie credentials."""

from sqlalchemy import select

from xianyu_client.core.constants import (
    CREDENTIAL_STATE_READY,
    CREDENTIAL_STATE_UNDECRYPTABLE,
    DATABASE_FILE_NAME,
    MASTER_KEY_FILE_NAME,
)
from xianyu_client.db.migrations import run_migrations
from xianyu_client.db.models import GoodfishAccountModel
from xianyu_client.db.session import create_database_engine, create_session_maker
from xianyu_client.managers.account_manager import AccountManager
from xianyu_client.managers.cookie_credential_manager import CookieCredentialManager
from xianyu_client.managers.master_key_manager import MasterKeyManager
from xianyu_client.services.credential_service import CredentialService
from tests.helpers.key_protectors import ReversibleKeyProtector, UnavailableKeyProtector


TEST_COOKIE = "unb=64; cookie2=token-value"
TEST_GOODFISH_ID = "2038890"
TEST_GOODFISH_NICKNAME = "buyer-one"
TEST_INVALID_ENCRYPTED_COOKIE = "not-valid-base64!"
CORRUPTED_KEY_BYTES = b"corrupted-master-key"


def _build_credential_service(tmp_path, protector=None):
    """Create an isolated credential service with its database collaborators.

    Args:
        tmp_path: pytest-provided temporary directory.

    Returns:
        The credential service, cookie manager, and async session factory.
    """
    database_path = tmp_path / DATABASE_FILE_NAME
    run_migrations(database_path)
    engine = create_database_engine(database_path)
    session_factory = create_session_maker(engine)
    master_key_manager = MasterKeyManager(
        tmp_path / MASTER_KEY_FILE_NAME,
        protector or ReversibleKeyProtector(),
    )
    cookie_manager = CookieCredentialManager(master_key_manager)
    credential_service = CredentialService(cookie_manager, AccountManager(session_factory))
    return credential_service, cookie_manager, session_factory, engine


async def test_initialize_reports_only_undecryptable_cookies(tmp_path):
    """Startup integrity checking identifies bad ciphertext without changing rows."""
    credential_service, cookie_manager, session_factory, engine = _build_credential_service(tmp_path)

    async with session_factory() as session:
        encrypted_cookie = await cookie_manager.encrypt(TEST_COOKIE)
        valid_account = GoodfishAccountModel(
            goodfish_id=TEST_GOODFISH_ID,
            goodfish_nickname=TEST_GOODFISH_NICKNAME,
            cookie=encrypted_cookie,
        )
        invalid_account = GoodfishAccountModel(
            goodfish_id=f"{TEST_GOODFISH_ID}-bad",
            goodfish_nickname=TEST_GOODFISH_NICKNAME,
            cookie=TEST_INVALID_ENCRYPTED_COOKIE,
        )
        session.add_all([valid_account, invalid_account])
        await session.commit()
        invalid_account_id = invalid_account.id

    state = await credential_service.initialize()

    assert state == CREDENTIAL_STATE_UNDECRYPTABLE
    assert credential_service.state == CREDENTIAL_STATE_UNDECRYPTABLE
    assert credential_service.failed_account_ids == (invalid_account_id,)
    async with session_factory() as session:
        accounts = (await session.execute(select(GoodfishAccountModel))).scalars().all()
    assert len(accounts) == 2
    assert accounts[0].cookie != TEST_COOKIE
    assert accounts[1].cookie == TEST_INVALID_ENCRYPTED_COOKIE
    assert all(not account.is_valid for account in accounts)
    await engine.dispose()


async def test_initialize_is_ready_when_all_cookies_decrypt(tmp_path):
    """A decryptable account set leaves credential state ready."""
    credential_service, cookie_manager, session_factory, engine = _build_credential_service(tmp_path)

    async with session_factory() as session:
        session.add(
            GoodfishAccountModel(
                goodfish_id=TEST_GOODFISH_ID,
                goodfish_nickname=TEST_GOODFISH_NICKNAME,
                cookie=await cookie_manager.encrypt(TEST_COOKIE),
            )
        )
        await session.commit()

    state = await credential_service.initialize()

    assert state == CREDENTIAL_STATE_READY
    assert credential_service.failed_account_ids == ()
    await engine.dispose()


async def test_initialize_preserves_data_when_master_key_fails(tmp_path):
    """A failed master-key restore marks the state but keeps all local data."""
    credential_service, _, session_factory, engine = _build_credential_service(tmp_path)
    key_path = tmp_path / MASTER_KEY_FILE_NAME
    key_path.write_bytes(CORRUPTED_KEY_BYTES)

    async with session_factory() as session:
        session.add(
            GoodfishAccountModel(
                goodfish_id=TEST_GOODFISH_ID,
                goodfish_nickname=TEST_GOODFISH_NICKNAME,
                cookie=TEST_INVALID_ENCRYPTED_COOKIE,
            )
        )
        await session.commit()

    state = await credential_service.initialize()

    assert state == CREDENTIAL_STATE_UNDECRYPTABLE
    assert credential_service.failed_account_ids == ()
    assert key_path.read_bytes() == CORRUPTED_KEY_BYTES
    async with session_factory() as session:
        account = (await session.execute(select(GoodfishAccountModel))).scalar_one()
    assert account.cookie == TEST_INVALID_ENCRYPTED_COOKIE
    await engine.dispose()


async def test_initialize_reports_state_when_master_key_cannot_be_created(tmp_path):
    """A protector failure prevents key creation without falling back to plaintext."""
    credential_service, _, _, engine = _build_credential_service(tmp_path, UnavailableKeyProtector())

    state = await credential_service.initialize()

    assert state == CREDENTIAL_STATE_UNDECRYPTABLE
    assert credential_service.failed_account_ids == ()
    assert not (tmp_path / MASTER_KEY_FILE_NAME).exists()
    await engine.dispose()
