# Upstream protocol provenance

The Cookie validation protocol in `xianyu/http_protocol.py` is derived from:

- Upstream project: <https://github.com/zhinianboke/xianyu-auto-reply>
- Inspected commit: `c9efea9b96330211849ea70c62b85980a31e0f22`
- Relevant upstream files:
  - `scheduler/app/services/scheduler/login_renew_task.py`
  - `common/utils/xianyu_utils.py`
  - `common/services/xianyu_mtop.py`

This client intentionally reuses only the mtop endpoint, signing algorithm, Cookie
identity fields (`unb`/`munb` and `tracknick`), and the in-memory token refresh
retry behavior. Upstream database writes, password login, Redis, proxy support,
and browser automation dependencies are not included.

The upstream project is licensed under the GNU Affero General Public License
v3.0. Its full license text remains available at `LICENSE` in the upstream
repository at the commit recorded above.
