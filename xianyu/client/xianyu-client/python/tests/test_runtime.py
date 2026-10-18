import json

from xianyu_client.runtime import create_handshake_line, write_runtime_file


def test_runtime_file_and_handshake_are_consistent(tmp_path):
    """Runtime metadata is atomically persisted and represented by one stdout line."""
    payload = write_runtime_file(tmp_path, 53219, 1234)
    content = (tmp_path / "runtime.json").read_text(encoding="utf-8")

    assert json.loads(content) == payload
    assert payload["port"] == 53219
    assert payload["pid"] == 1234
    assert create_handshake_line(payload).startswith("#GOODFISH_PORT#{")
    assert create_handshake_line(payload).endswith("}#END#")

