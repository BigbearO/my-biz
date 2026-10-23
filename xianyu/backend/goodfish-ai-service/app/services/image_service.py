"""Image validation for the reply pipeline (minimal scope for this batch).

The upload endpoint and static file serving arrive in a later batch. Here
we only validate that image URLs passed to /api/ai/reply point at files
that exist in ``image.storage_dir``; missing files degrade to the
``[图片]`` placeholder with a warning and never block the reply.
"""

import logging
import re
from collections.abc import Callable
from pathlib import Path

from ..constants import (
    IMAGE_PLACEHOLDER_TEXT,
    IMAGE_VIEW_URL_PREFIX,
    MESSAGE_TYPE_IMAGE,
    MESSAGE_TYPE_TEXT,
)
from ..settings import BusinessSettings

logger = logging.getLogger(__name__)

_FILENAME_PATTERN = re.compile(r"^[0-9a-f]{32}\.png$")


class ImageService:
    def __init__(self, settings_provider: Callable[[], BusinessSettings]) -> None:
        self._settings_provider = settings_provider

    def extract_filename(self, image_url: str) -> str | None:
        """Return the stored filename when the URL points at our view path."""
        if not image_url:
            return None
        marker = IMAGE_VIEW_URL_PREFIX
        index = image_url.find(marker)
        if index < 0:
            return None
        filename = image_url[index + len(marker):].strip()
        if not _FILENAME_PATTERN.match(filename):
            return None
        return filename

    def file_exists(self, filename: str) -> bool:
        storage_dir = self._settings_provider().image.storage_dir
        return (Path(storage_dir) / filename).is_file()

    def resolve_message_type(self, image_urls: list[str]) -> str:
        """Image when at least one valid, existing image remains."""
        for url in image_urls:
            filename = self.extract_filename(url)
            if filename and self.file_exists(filename):
                return MESSAGE_TYPE_IMAGE
        return MESSAGE_TYPE_TEXT

    def validate_urls(self, image_urls: list[str]) -> tuple[list[str], list[str]]:
        """Split urls into (existing, degraded placeholders)."""
        existing: list[str] = []
        degraded: list[str] = []
        for url in image_urls:
            filename = self.extract_filename(url)
            if filename and self.file_exists(filename):
                existing.append(url)
            else:
                logger.warning(
                    "Image missing on disk, degrading to placeholder text: %s",
                    filename or "unrecognized url",
                )
                degraded.append(IMAGE_PLACEHOLDER_TEXT)
        return existing, degraded

    @staticmethod
    def build_placeholder_content() -> str:
        return IMAGE_PLACEHOLDER_TEXT
