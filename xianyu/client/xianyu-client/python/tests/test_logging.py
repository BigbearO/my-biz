from xianyu_client.core.logging import SecretRedactionFilter


def test_secret_redaction_filter_replaces_secret_values():
    """Secret values cannot reach the message body retained by loggers."""
    record = {"message": "token=secret-value port=53219"}
    accepted = SecretRedactionFilter(("secret-value",))(record)

    assert accepted is True
    assert record["message"] == "token=*** port=53219"

