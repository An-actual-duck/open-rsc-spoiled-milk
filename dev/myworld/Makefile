ROOT_DIR := $(abspath $(CURDIR)/../..)
PYTHON ?= python3

.PHONY: help generators sync-generated check list dry-run build run smoke test benchmark benchmark-matrix combat-check prayer-check items-check npcs-check generator-tests layout-check boundary-check

help:
	@printf '%s\n' \
		'MyWorld compatibility tasks:' \
		'  Prefer root-level make targets from the repository root:' \
		'  make generators       # sync manifest-backed generated artifacts' \
		'  make check            # validate generated artifacts + prerequisites' \
		'  make build-server     # validate artifacts and compile server/plugins' \
		'  make run              # validate artifacts and run the server' \
		'  make test             # full MyWorld validation suite without rewriting defs' \
		'' \
		'  These legacy dev/myworld targets still delegate to the same root workflows:' \
		'  make -C dev/myworld generators' \
		'  make -C dev/myworld check' \
		'  make -C dev/myworld build' \
		'  make -C dev/myworld run' \
		'  make -C dev/myworld test'

generators:
	cd $(ROOT_DIR) && $(PYTHON) ./tools/generators/run-generators.py

sync-generated: generators

check:
	cd $(ROOT_DIR) && ./scripts/check.sh

list:
	cd $(ROOT_DIR) && $(PYTHON) ./tools/generators/run-generators.py --list

dry-run:
	cd $(ROOT_DIR) && $(PYTHON) ./tools/generators/run-generators.py --dry-run

build:
	cd $(ROOT_DIR) && ./scripts/build-server.sh

run:
	cd $(ROOT_DIR) && ./scripts/run-server.sh

smoke:
	cd $(ROOT_DIR) && ./tests/myworld/test-smoke.sh

test:
	cd $(ROOT_DIR) && ./tests/myworld/test-all.sh

benchmark:
	cd $(ROOT_DIR) && ./tools/benchmarks/benchmark-foundation.sh

benchmark-matrix:
	cd $(ROOT_DIR) && ./tools/benchmarks/benchmark-foundation-matrix.sh

combat-check:
	cd $(ROOT_DIR) && $(PYTHON) ./tools/generators/run-generators.py --check --group combat
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-combat-data.py
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-combat-interaction.py
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-npc-attack-styles.py
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-combat-runtime-invariants.py
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-combat-exceptions.py
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-combat-scenarios.py
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-defense-distribution.py
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-balance-fixtures.py

prayer-check:
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-prayer-rework.py
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-prayer-ui.py

items-check:
	cd $(ROOT_DIR) && $(PYTHON) ./tools/generators/run-generators.py --check --group items

npcs-check:
	cd $(ROOT_DIR) && $(PYTHON) ./tools/generators/run-generators.py --check --group npcs

generator-tests:
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-generator-scripts.py

layout-check:
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-myworld-plugin-layout.py

boundary-check:
	cd $(ROOT_DIR) && $(PYTHON) ./tests/myworld/test-myworld-import-boundary.py
