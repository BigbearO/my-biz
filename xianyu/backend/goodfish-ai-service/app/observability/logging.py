import logging


class TelemetryLogFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        if record.name == "uvicorn.access" or record.name.startswith(
            "uvicorn.access."
        ):
            return False
        if record.name == "app" or record.name.startswith("app."):
            return record.levelno >= logging.INFO
        return record.levelno >= logging.WARNING
