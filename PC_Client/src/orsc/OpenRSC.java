package orsc;

import orsc.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class OpenRSC extends ORSCApplet {

	public static OpenRSC applet;
	static JFrame jframe;
	private static final long serialVersionUID = 1L;

	public static void main(String[] args) {
		// MUST do this before anything else runs in order to override OS-level dpi settings
		// (not applicable to macOS, which implements OS-scaling in a different fashion)
		if (!Utils.isMacOS()) {
			// Disable OS-level scaling in all JREs > 8
			System.setProperty("sun.java2d.uiScale.enabled", "false");
			System.setProperty("sun.java2d.uiScale", "1");

			// Required for newer versions of Oracle 8 to disable OS-level scaling
			System.setProperty("sun.java2d.dpiaware", "true");

			// Linux / other
			if (!Utils.isWindowsOS()) {
				System.setProperty("GDK_SCALE", "1");
			}
		}

		if (Utils.isMacOS()) {
			// Note: Only works on some Java 8 implementations
			System.setProperty("apple.awt.application.appearance", "system");
		}

		File scalingSettings = new File("./clientSettings.conf");
		if (scalingSettings.exists()) {
			Properties props = new Properties();

			try (FileInputStream in = new FileInputStream(scalingSettings.getAbsolutePath())) {
				props.load(in);

				// Load scaling settings
				String scalingTypeString = props.getProperty("scaling_type");
				String scalarString = props.getProperty("ui_scale");
				if (scalarString == null || scalarString.isEmpty()) {
					scalarString = props.getProperty("scaling_scalar");
				}
				if (scalingTypeString != null && !scalingTypeString.isEmpty()) {
					int scalingTypeOrdinal = Integer.parseInt(scalingTypeString);
					mudclient.scalingType = ScaledWindow.ScalingAlgorithm.values()[scalingTypeOrdinal];
				}
				if (scalarString != null && !scalarString.isEmpty()) {
					ORSCApplet.oldRenderingScalar = mudclient.renderingScalar;
					mudclient.newRenderingScalar = Float.parseFloat(scalarString);
				}
			} catch (Exception e) {
				System.out.println("Something went wrong loading scaling settings");
				e.printStackTrace();
			}
		}

		scaledWindow = ScaledWindow.getInstance();
		SwingUtilities.invokeLater(OpenRSC::createAndShowGUI);
	}

	public static void createAndShowGUI() {
		try {
			jframe = new JFrame(Config.getServerNameWelcome());
			applet = new OpenRSC();
			// Here we add 12 because 12 was added back in 2009 for the skip tutorial line.
			applet.setPreferredSize(new Dimension(512, 334 + 12));
			jframe.getContentPane().setLayout(new BorderLayout());
			jframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			jframe.setIconImage(Utils.getImage("icon.png").getImage());
			jframe.setTitle(Config.WINDOW_TITLE);
			jframe.getContentPane().add(applet);
			jframe.setResizable(true); // true or false based on server sent config
			jframe.setVisible(false); // All rendering is forwarded to the ScaledWindow class
			jframe.setBackground(Color.black);
			// Just like above, here we add 12 because 12 was added back in 2009 for the skip tutorial line.
			jframe.setMinimumSize(new Dimension(512, 334 + 12));
			jframe.pack();
			jframe.setLocationRelativeTo(null);
			applet.init();
			applet.seedInitialLoadingFrame();
			applet.start();

			scaledWindow.launchScaledWindow();

			applet.resizeMudclient(512, 346);
		} catch (HeadlessException e) {
			e.printStackTrace();
		}
	}

	public void setTitle(String title) {
		scaledWindow.setTitle(title);
	}

	public void setIconImage(String serverName) {
		switch (serverName) {
			case "RSC Coleslaw":
				scaledWindow.setIconImage(Utils.getImage("coleslaw.icon.png").getImage());
				break;
			case "RSC Uranium":
				scaledWindow.setIconImage(Utils.getImage("uranium.icon.png").getImage());
				break;
			case "RSC Cabbage":
				scaledWindow.setIconImage(Utils.getImage("cabbage.icon.png").getImage());
				break;
			case "OpenPK":
				scaledWindow.setIconImage(Utils.getImage("openpk.icon.png").getImage());
				break;
			default:
				scaledWindow.setIconImage(Utils.getImage("icon.png").getImage());
		}
	}

	public String getCacheLocation() {
		return Config.F_CACHE_DIR + File.separator;
	}

	@Override
	public void playSound(byte[] soundData, int offset, int dataLength) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void stopSoundPlayer() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public boolean getResizable() {
		return Config.allowResize1();
	}
}
