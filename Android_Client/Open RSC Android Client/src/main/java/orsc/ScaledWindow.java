package orsc;

// Stub class for feature that is disabled on Android
public class ScaledWindow {

	private static ScaledWindow instance = null;
	private ScaledWindow() {
		instance = this;
	}
	public enum ScalingAlgorithm {
		INTEGER_SCALING,
		BILINEAR_INTERPOLATION,
		BICUBIC_INTERPOLATION
	}
	public static ScaledWindow getInstance() {
		if (instance == null) {
			synchronized (ScaledWindow.class) {
				instance = new ScaledWindow();
			}
		}
		return instance;
	}
	public void validateAppletSize() {
	}
}