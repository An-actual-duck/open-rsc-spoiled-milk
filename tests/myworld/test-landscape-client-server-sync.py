#!/usr/bin/env python3
import sys

from installed_world_package import file_inventory, package_root


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def main() -> None:
    server = file_inventory("server")
    client = file_inventory("client")
    if server != client:
        fail(
            "Client and server installed World Builder packages must match exactly: "
            f"server={package_root('server')} client={package_root('client')}"
        )

    print("PASS: client and server installed World Builder packages match")


if __name__ == "__main__":
    main()
