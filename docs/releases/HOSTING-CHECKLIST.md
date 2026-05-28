# Hosted Limited-Alpha Checklist

The player downloads connect to a single hosted server. Do not publish a
client archive configured for `localhost` or for the development database.

## Live Configuration

Create a deployment-only copy of `server/myworld.conf` on the host, outside the
repository working tree if it contains operational details. Use these required
changes from the development configuration:

```yaml
database:
	db_name: spoiled_milk_alpha

world:
	server_name: Spoiled Milk
	server_name_welcome: Spoiled Milk
	welcome_text: Spoiled Milk limited alpha playtest.
	server_port: 43605
```

Keep `client_version: 10010` and `enforce_custom_client_version: true` aligned
with the released client. If the public port differs from `43605`, provide the
same port to `scripts/package-player-release.sh`.

## Initial Deployment

1. Create `server/inc/sqlite/spoiled_milk_alpha.db` from
   `server/inc/sqlite/myworld_seed.db` once for the new hosted world.
2. Configure the host firewall and port forwarding for the selected TCP server
   port; expose the websocket port only if it is needed by the selected client.
3. Start the server with the live config file, not `scripts/start-fresh.sh`;
   that development command recreates local state.
4. Start a configured release client, register a test account, log out, restart
   the server, and confirm the account and character progress persist.

## Routine Operation

1. Back up the live SQLite database before each server build or gameplay
   deployment.
2. Run `./scripts/pre-field-test.sh` and compile the server build selected for
   deployment before replacing the hosted server binaries.
3. Keep the published client endpoint and hosted server port synchronized.
4. Record the git revision used for each hosted alpha build and each attached
   player download.
