package orsc;

import java.io.IOException;
import java.net.Socket;

/** Prepare the JDK socket implementation before graphics loads native libraries. */
final class ClientSocketRuntime {
	private ClientSocketRuntime() {
	}

	static void prepare() throws IOException {
		// On the observed Linux/JDK 21 runtime, this option call loads libextnet.
		// Run it before LWJGL's library-path lookup can race that native load.
		// This creates no connection or binding, and releases the local socket.
		try (Socket socket = new Socket()) {
			socket.setTcpNoDelay(true);
		}
	}
}
