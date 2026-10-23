"""Keyword-based intent classification (log-only).

The detected intent is stored on ``ai_reply_log.intent`` for statistics and
never influences prompt assembly — all three scenario prompts are always
embedded. Keyword tables come from configuration and hot-reload via the
settings provider.
"""

from collections.abc import Callable

from ..constants import INTENT_DEFAULT
from ..settings import BusinessSettings


class IntentService:
    def __init__(self, settings_provider: Callable[[], BusinessSettings]) -> None:
        self._settings_provider = settings_provider

    def detect(self, message: str) -> str:
        """Return the first intent whose keyword appears in the message."""
        keywords = self._settings_provider().intent.keywords
        if message:
            for intent, words in keywords.items():
                if any(word and word in message for word in words):
                    return intent
        return INTENT_DEFAULT
