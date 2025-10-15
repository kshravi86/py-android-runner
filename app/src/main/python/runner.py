import io
import traceback
from contextlib import redirect_stdout, redirect_stderr


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
