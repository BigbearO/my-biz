import logging

from app.observability import TelemetryLogFilter


def make_record(name: str, level: int) -> logging.LogRecord:
    return logging.LogRecord(
        name=name,
        level=level,
        pathname=__file__,
        lineno=1,
        msg="message",
        args=(),
        exc_info=None,
    )


def test_filter_allows_application_info_logs() -> None:
    record = make_record("app.main", logging.INFO)

    assert TelemetryLogFilter().filter(record) is True


def test_filter_rejects_application_debug_logs() -> None:
    record = make_record("app.main", logging.DEBUG)

    assert TelemetryLogFilter().filter(record) is False


def test_filter_allows_third_party_warnings_only() -> None:
    telemetry_filter = TelemetryLogFilter()

    assert telemetry_filter.filter(make_record("uvicorn.error", logging.INFO)) is False
    assert (
        telemetry_filter.filter(make_record("uvicorn.error", logging.WARNING))
        is True
    )


def test_filter_rejects_access_logs() -> None:
    record = make_record("uvicorn.access.http", logging.WARNING)

    assert TelemetryLogFilter().filter(record) is False
