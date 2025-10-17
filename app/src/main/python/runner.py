import io
import traceback
from contextlib import redirect_stdout, redirect_stderr

# Persistent execution context for notebook-like cells
_session_globals = {"__name__": "__main__"}


def run_user_code(source: str) -> str:
    if not source.strip():
        return ""

    stdout = io.StringIO()
    stderr = io.StringIO()
    globals_dict = {"__name__": "__main__"}

    try:
        compiled = compile(source, "<user>", "exec")
    except SyntaxError:
        traceback.print_exc(file=stderr)
        return stderr.getvalue()

    try:
        with redirect_stdout(stdout), redirect_stderr(stderr):
            exec(compiled, globals_dict, {})
    except Exception:  # noqa: BLE001 - surface any runtime error back to UI
        traceback.print_exc(file=stderr)

    return stdout.getvalue() + stderr.getvalue()


def reset_session() -> None:
    """Reset the persistent execution context for cell runs."""
    global _session_globals
    _session_globals = {"__name__": "__main__"}


def run_cell(source: str) -> str:
    """Execute code in a persistent globals dict (notebook-style)."""
    if not source.strip():
        return ""
    stdout = io.StringIO()
    stderr = io.StringIO()
    try:
        compiled = compile(source, "<cell>", "exec")
    except SyntaxError:
        traceback.print_exc(file=stderr)
        return stderr.getvalue()
    try:
        with redirect_stdout(stdout), redirect_stderr(stderr):
            # Use the same dict for globals and locals so variables persist
            # across cells just like Jupyter
            exec(compiled, _session_globals, _session_globals)
    except Exception:
        traceback.print_exc(file=stderr)
    return stdout.getvalue() + stderr.getvalue()


def check_syntax(source: str) -> str:
    """
    Lightweight syntax check. Returns a compact string to avoid
    cross-language marshalling complexity:

    - "OK" when no syntax error
    - "ERR:<line>:<col>:<message>" when SyntaxError found
    """
    try:
        compile(source, "<user>", "exec")
        return "OK"
    except SyntaxError as e:
        line = getattr(e, 'lineno', 1) or 1
        col = getattr(e, 'offset', 1) or 1
        msg = getattr(e, 'msg', str(e))
        # Make message single line and safe
        safe = str(msg).replace('\n', ' ').replace('\r', ' ').strip()
        return f"ERR:{line}:{col}:{safe}"
