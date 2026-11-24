#!/usr/bin/env python3
"""Runs one command the way a terminal user does, for tests of the runner's signal and timeout behavior.

A command started as a background job of a non-interactive shell ignores SIGINT, so a shell harness cannot test it. This launcher
is started in the foreground, resets the dispositions it hands on to the defaults, and reports what happened on its own stdout
as `key=value` lines (the command's output goes to --log).

  launch.py --log FILE [--timeout SECONDS] [--signal NAME (--when-exists FILE | --after SECONDS)] -- COMMAND [ARGS...]

  --signal NAME      send this signal (INT, TERM, HUP) to the command once --when-exists FILE exists, or --after that many seconds
  --timeout SECONDS  end the command with KILL and report hung=yes when it has not finished by then (default 60)
Output: status=N (a negative number is a fatal signal of the command itself), hung=yes|no, signalled=yes|no.
"""
import argparse
import os
import signal
import subprocess
import sys
import time


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", required=True)
    parser.add_argument("--timeout", type=float, default=60)
    parser.add_argument("--signal", choices=["INT", "TERM", "HUP"])
    parser.add_argument("--when-exists")
    parser.add_argument("--after", type=float)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    no_trigger = args.when_exists is None and args.after is None
    if not command or (args.signal is None) != no_trigger or (args.when_exists is not None and args.after is not None):
        parser.error("a command is required, and --signal goes with exactly one of --when-exists and --after")

    def defaults() -> None:
        for name in ("SIGINT", "SIGTERM", "SIGHUP", "SIGQUIT"):
            signal.signal(getattr(signal, name), signal.SIG_DFL)

    with open(args.log, "wb") as log:
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, preexec_fn=defaults, start_new_session=True)
        started = time.monotonic()
        deadline = started + args.timeout
        signalled = False
        hung = False
        while process.poll() is None:
            due = os.path.exists(args.when_exists) if args.when_exists is not None else args.after is not None and time.monotonic() - started >= args.after
            if args.signal is not None and not signalled and due:
                process.send_signal(getattr(signal, f"SIG{args.signal}"))
                signalled = True
            if time.monotonic() > deadline:
                hung = True
                os.killpg(process.pid, signal.SIGKILL)
                break
            time.sleep(0.02)
        status = process.wait()
    print(f"status={status}")
    print(f"hung={'yes' if hung else 'no'}")
    print(f"signalled={'yes' if signalled else 'no'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
