package orsc;

/** An online-list row received from the server. */
final class OnlinePlayerEntry {
	final String name;
	final int crown;
	final String location;

	OnlinePlayerEntry(String name, int crown, String location) {
		this.name = name;
		this.crown = crown;
		this.location = location;
	}
}
