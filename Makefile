-include .env

.DEFAULT_GOAL := help

PYTHON ?= python3

.PHONY: help build-client build-server build compile run run-server run-client run-zgc start-fresh check test smoke pre-field-test download-windows-jre package-player-release generators sync-generated list dry-run benchmark benchmark-matrix combat-check prayer-check items-check npcs-check generator-tests layout-check boundary-check start-linux combined-install get-updates

help:
	@printf '%s\n' \
		'MyWorld tasks:' \
		'  make build-client      # compile the PC client' \
		'  make run-client        # compile and launch the PC client' \
		'  make build-server      # validate generated artifacts and compile server/plugins' \
		'  make build             # alias for build-server' \
		'  make run               # validate generated artifacts and run the MyWorld server' \
		'  make run-zgc           # run the MyWorld server with the Java 17+ ZGC launcher' \
		'  make start-fresh       # reset SQLite dev DB, compile, and run' \
		'  make check             # validate prerequisites and generated artifacts' \
		'  make test              # full MyWorld validation suite without rewriting defs' \
		'  make smoke             # validate, compile, and boot smoke test' \
		'  make pre-field-test    # quick guardrails before live field testing' \
		'  make download-windows-jre # fetch Temurin 17 Windows x64 JRE for release packaging' \
		'  make package-player-release RELEASE_VERSION=... HOST=... PORT=... WINDOWS_JRE=... ASSETS_CLEARED=1 # build alpha player zips' \
		'  make generators        # sync manifest-backed generated artifacts' \
		'  make sync-generated    # alias for generators' \
		'  make list              # list manifest generators and groups' \
		'  make dry-run           # show generator commands without executing' \
		'  make benchmark         # run opt-in foundation timing benchmark' \
		'  make benchmark-matrix  # run short + soak benchmark scenarios' \
		'  make combat-check      # combat-focused generator check + combat tests' \
		'  make prayer-check      # prayer rework catalog guardrails' \
		'  make items-check       # validate only item generated artifacts' \
		'  make npcs-check        # validate only npc generated artifacts' \
		'  make generator-tests   # generator regression harness' \
		'  make layout-check      # MyWorld plugin namespace regression check' \
		'  make boundary-check    # MyWorld import boundary regression check' \
		'' \
		'Legacy OpenRSC/Cabbage Make recipes are archived at docs/inherited-openrsc/legacy-Makefile.'

build-client:
	./scripts/build-client.sh

build-server:
	./scripts/build-server.sh

build: build-server

run:
	./scripts/run-server.sh

run-zgc:
	./scripts/run-server-zgc.sh

start-fresh:
	./scripts/start-fresh.sh

check:
	./scripts/check.sh

test:
	./scripts/test.sh

smoke:
	./tests/myworld/test-smoke.sh

pre-field-test:
	./scripts/pre-field-test.sh

download-windows-jre:
	./scripts/download-windows-jre.sh

package-player-release:
	@test "$(ASSETS_CLEARED)" = "1" || (printf '%s\n' 'Set ASSETS_CLEARED=1 only after confirming packaged art redistribution terms.' >&2; exit 1)
	./scripts/package-player-release.sh --version "$(RELEASE_VERSION)" --host "$(HOST)" --port "$(PORT)" --windows-jre "$(WINDOWS_JRE)" --assets-cleared

generators:
	$(PYTHON) ./tools/generators/run-generators.py

sync-generated: generators

list:
	$(PYTHON) ./tools/generators/run-generators.py --list

dry-run:
	$(PYTHON) ./tools/generators/run-generators.py --dry-run

benchmark:
	./scripts/benchmark.sh

benchmark-matrix:
	./scripts/benchmark-matrix.sh

combat-check:
	$(PYTHON) ./tools/generators/run-generators.py --check --group combat
	$(PYTHON) ./tests/myworld/test-combat-data.py
	$(PYTHON) ./tests/myworld/test-combat-interaction.py
	$(PYTHON) ./tests/myworld/test-npc-attack-styles.py
	$(PYTHON) ./tests/myworld/test-combat-runtime-invariants.py
	$(PYTHON) ./tests/myworld/test-combat-exceptions.py
	$(PYTHON) ./tests/myworld/test-combat-scenarios.py
	$(PYTHON) ./tests/myworld/test-defense-distribution.py
	$(PYTHON) ./tests/myworld/test-balance-fixtures.py

prayer-check:
	$(PYTHON) ./tests/myworld/test-prayer-rework.py
	$(PYTHON) ./tests/myworld/test-prayer-ui.py

items-check:
	$(PYTHON) ./tools/generators/run-generators.py --check --group items

npcs-check:
	$(PYTHON) ./tools/generators/run-generators.py --check --group npcs

generator-tests:
	$(PYTHON) ./tests/myworld/test-generator-scripts.py

layout-check:
	$(PYTHON) ./tests/myworld/test-myworld-plugin-layout.py

boundary-check:
	$(PYTHON) ./tests/myworld/test-myworld-import-boundary.py

#########################################
#####      Compatibility Aliases     #####
#########################################
start-linux: start-fresh

run-server: run

run-client:
	./scripts/run-client.sh

compile: build-server build-client

combined-install:
	@printf '%s\n' 'Legacy Deployment_Scripts have been archived under docs/inherited-openrsc/legacy-launchers/. Use make check, make build-server, and make run for MyWorld.'

get-updates:
	@printf '%s\n' 'Legacy Deployment_Scripts have been archived under docs/inherited-openrsc/legacy-launchers/. Use git pull plus the root MyWorld targets.'
