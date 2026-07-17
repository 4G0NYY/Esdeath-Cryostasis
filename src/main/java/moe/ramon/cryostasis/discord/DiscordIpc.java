package moe.ramon.cryostasis.discord;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import moe.ramon.cryostasis.Cryostasis;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal Discord Rich Presence client that speaks the local IPC protocol directly, with no
 * native library or third-party dependency. Discord exposes a socket named discord-ipc-0
 * through discord-ipc-9: a Windows named pipe, or a Unix domain socket under the runtime
 * dir. A dependency-free client keeps the Fabric jar self-contained (no jar-in-jar), which
 * is why this is hand-rolled instead of pulling in the Game SDK.
 *
 * All blocking socket work happens on one daemon thread. The client thread only publishes a
 * desired activity string; the worker connects, reconnects, and pushes changes at most every
 * couple of seconds, well under Discord's rate limit. One reply frame is read after every
 * write so the pipe buffer stays balanced and never stalls.
 */
public final class DiscordIpc {
	private static final int OP_HANDSHAKE = 0;
	private static final int OP_FRAME = 1;
	private static final int OP_CLOSE = 2;

	private final String clientId;
	private final int pid = (int) ProcessHandle.current().pid();

	private volatile boolean stopRequested;
	private volatile String desiredActivity; // serialized activity object, or null to clear
	private String lastSentActivity;
	private Thread worker;

	// Exactly one of these is live at a time, depending on the platform.
	private RandomAccessFile pipe;
	private SocketChannel channel;

	public DiscordIpc(String clientId) {
		this.clientId = clientId;
	}

	/** Start the worker thread if it is not already running. Safe to call repeatedly. */
	public synchronized void start() {
		if (worker != null && worker.isAlive()) {
			return;
		}
		stopRequested = false;
		lastSentActivity = null;
		worker = new Thread(this::run, "cryostasis-discord-rpc");
		worker.setDaemon(true);
		worker.start();
	}

	/** Publish the activity to show. Pass null to clear presence on the next cycle. */
	public void setActivity(String activityJson) {
		this.desiredActivity = activityJson;
	}

	/** Signal the worker to clear presence and disconnect. Non-blocking. */
	public synchronized void stop() {
		stopRequested = true;
		desiredActivity = null;
		if (worker != null) {
			worker.interrupt();
		}
	}

	private void run() {
		while (!stopRequested) {
			try {
				if (!isConnected()) {
					if (!connect()) {
						sleep(5000);
						continue;
					}
					lastSentActivity = null;
				}
				String want = desiredActivity;
				if (!Objects.equals(want, lastSentActivity)) {
					sendActivity(want);
					readFrame(); // drain Discord's ack so the buffer stays balanced
					lastSentActivity = want;
				}
				sleep(2000);
			} catch (IOException e) {
				// The client went away or the pipe broke; drop it and reconnect next loop.
				closeTransport();
				sleep(3000);
			} catch (Exception e) {
				Cryostasis.LOGGER.debug("Discord RPC worker error", e);
				closeTransport();
				sleep(3000);
			}
		}
		// Clear the interrupt flag left by stop() so the final blocking writes can run.
		Thread.interrupted();
		// Best-effort: clear the presence so it does not linger after the module is disabled.
		try {
			if (isConnected()) {
				sendActivity(null);
				readFrame();
			}
		} catch (Exception ignored) {
			// Nothing useful to do while shutting down.
		}
		closeTransport();
	}

	private boolean connect() {
		boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
		for (int i = 0; i < 10; i++) {
			try {
				if (windows) {
					pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
				} else {
					Path path = unixSocketPath(i);
					if (path == null) {
						continue;
					}
					channel = SocketChannel.open(StandardProtocolFamily.UNIX);
					channel.connect(UnixDomainSocketAddress.of(path));
				}
				handshake();
				Cryostasis.LOGGER.info("Discord RPC connected on ipc-{}", i);
				return true;
			} catch (Exception e) {
				closeTransport();
			}
		}
		return false;
	}

	private Path unixSocketPath(int index) {
		String base = firstNonNull(System.getenv("XDG_RUNTIME_DIR"),
				System.getenv("TMPDIR"), System.getenv("TMP"), System.getenv("TEMP"), "/tmp");
		if (base == null) {
			return null;
		}
		// Discord may live directly under the runtime dir or inside a Flatpak/snap subdir.
		String[] subdirs = {"", "app/com.discordapp.Discord/", "snap.discord-canary/", "snap.discord/"};
		for (String sub : subdirs) {
			Path candidate = Path.of(base, sub + "discord-ipc-" + index);
			if (candidate.toFile().exists()) {
				return candidate;
			}
		}
		return Path.of(base, "discord-ipc-" + index);
	}

	private void handshake() throws IOException {
		JsonObject hello = new JsonObject();
		hello.addProperty("v", 1);
		hello.addProperty("client_id", clientId);
		writeFrame(OP_HANDSHAKE, hello.toString());
		readFrame(); // READY dispatch
	}

	private void sendActivity(String activityJson) throws IOException {
		JsonObject args = new JsonObject();
		args.addProperty("pid", pid);
		if (activityJson == null) {
			args.add("activity", JsonNull.INSTANCE);
		} else {
			args.add("activity", JsonParser.parseString(activityJson));
		}
		JsonObject frame = new JsonObject();
		frame.addProperty("cmd", "SET_ACTIVITY");
		frame.addProperty("nonce", UUID.randomUUID().toString());
		frame.add("args", args);
		writeFrame(OP_FRAME, frame.toString());
	}

	private void writeFrame(int op, String payload) throws IOException {
		byte[] data = payload.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
		buf.putInt(op);
		buf.putInt(data.length);
		buf.put(data);
		buf.flip();
		if (pipe != null) {
			pipe.write(buf.array(), 0, buf.limit());
		} else if (channel != null) {
			while (buf.hasRemaining()) {
				channel.write(buf);
			}
		} else {
			throw new IOException("No Discord transport");
		}
	}

	private void readFrame() throws IOException {
		ByteBuffer header = readFully(8).order(ByteOrder.LITTLE_ENDIAN);
		int op = header.getInt();
		int length = header.getInt();
		ByteBuffer body = readFully(length);
		if (op == OP_CLOSE) {
			throw new IOException("Discord closed the connection");
		}
	}

	private ByteBuffer readFully(int n) throws IOException {
		byte[] out = new byte[n];
		if (pipe != null) {
			pipe.readFully(out);
			return ByteBuffer.wrap(out);
		}
		if (channel != null) {
			ByteBuffer buf = ByteBuffer.wrap(out);
			while (buf.hasRemaining()) {
				if (channel.read(buf) < 0) {
					throw new IOException("Discord socket closed");
				}
			}
			buf.flip();
			return buf;
		}
		throw new IOException("No Discord transport");
	}

	private boolean isConnected() {
		return pipe != null || (channel != null && channel.isConnected());
	}

	private void closeTransport() {
		try {
			if (pipe != null) {
				pipe.close();
			}
		} catch (IOException ignored) {
			// Already broken; nothing to recover.
		}
		try {
			if (channel != null) {
				channel.close();
			}
		} catch (IOException ignored) {
			// Already broken; nothing to recover.
		}
		pipe = null;
		channel = null;
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String firstNonNull(String... values) {
		for (String v : values) {
			if (v != null && !v.isEmpty()) {
				return v;
			}
		}
		return null;
	}
}
