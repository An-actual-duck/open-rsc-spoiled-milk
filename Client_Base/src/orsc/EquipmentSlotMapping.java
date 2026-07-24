package orsc;

/**
 * Translates the server's physical equipment slots into the compact client
 * layout. The server retains alternate helmet/body/leg slots at 5-7; the
 * client presents each alternate family through its primary slot.
 */
public final class EquipmentSlotMapping {
	private EquipmentSlotMapping() {
	}

	public static int serverToClient(final int serverSlot) {
		if (serverSlot == 5) {
			return 0;
		}
		if (serverSlot == 6) {
			return 1;
		}
		if (serverSlot == 7) {
			return 2;
		}
		if (serverSlot > 7) {
			return serverSlot - 3;
		}
		return serverSlot;
	}
}
