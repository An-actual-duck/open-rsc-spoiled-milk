from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PLUGIN_HANDLER = ROOT / "server/src/com/openrsc/server/plugins/handler/PluginHandler.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    source = PLUGIN_HANDLER.read_text(encoding="utf-8")

    require(
        "defaultHandler != null && triggerType.isInstance(defaultHandler)" in source,
        "Plugin dispatch should recognize a compatible default trigger handler",
    )
    require(
        "if (defaultHandlerSupportsTrigger)" in source,
        "Default actions should run only when the default handler supports the trigger",
    )
    require(
        'LOGGER.warn("No plugin or default handler accepted trigger: {}", simpleName);' in source,
        "Genuinely unhandled triggers should remain visible in the server log",
    )
    require(
        "Unable to handle unknown plugin" not in source,
        "A missing specialized override should not be mislabeled as an unknown plugin",
    )

    default_check = source.index("if (defaultHandlerSupportsTrigger)")
    default_invoke = source.index(
        "invokePluginAction(triggerType, owner, defaultHandler, data, walkToAction);",
        default_check,
    )
    unhandled_warning = source.index(
        'LOGGER.warn("No plugin or default handler accepted trigger: {}", simpleName);',
        default_invoke,
    )
    require(
        default_check < default_invoke < unhandled_warning,
        "Compatible defaults should dispatch before the genuinely-unhandled warning path",
    )

    print("PASS: plugin default fallback warning semantics validated")


if __name__ == "__main__":
    main()
