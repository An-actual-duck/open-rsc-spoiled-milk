package orsc;

final class MonitorMode {
	final long monitor;
	final int x;
	final int y;
	final int width;
	final int height;
	final int refreshRate;

	MonitorMode(int x, int y, int width, int height) {
		this(0L, x, y, width, height, 60);
	}

	MonitorMode(long monitor, int x, int y, int width, int height, int refreshRate) {
		this.monitor = monitor;
		this.x = x;
		this.y = y;
		this.width = Math.max(1, width);
		this.height = Math.max(1, height);
		this.refreshRate = Math.max(0, refreshRate);
	}
}
