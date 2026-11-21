package orsc;

public final class PersistentMovementReceiptFixture {
	public static void main(String[] args) {
		PersistentMovementReceipt movement = new PersistentMovementReceipt();
		check(!movement.accept(1, 5, 100), "movement cannot open its own stream");
		check(!movement.open(1, 5, 100, 4), "new stream requires its real baseline");
		check(movement.open(1, 5, 100, 5), "first stream starts at matching baseline");
		check(movement.accept(1, 5, 102), "movement does not ask for the active terrain scene");
		check(!movement.accept(1, 5, 101), "old movement sequence is discarded");
		check(!movement.open(1, 6, 103, 6), "an existing stream cannot change its anchor");
		check(movement.open(2, 6, 104, 6), "relocation starts a new movement lifetime");
		check(!movement.accept(1, 5, 105), "old travel lifetime is discarded");
		check(movement.open(3, 6, 106, 6), "another relocation can use the same terrain scene");
		movement.reset();
		check(!movement.open(3, 6, 107, 7), "world change rejects the old baseline");
		check(movement.open(4, 7, Integer.MAX_VALUE, 7), "a new world establishes its own movement");
		check(movement.accept(4, 7, Integer.MIN_VALUE), "ordered receipts survive sequence wrap");
		check(!movement.accept(4, 7, Integer.MAX_VALUE), "pre-wrap duplicate is discarded");
		System.out.println("PASS persistent movement ordering, terrain revisions, relocation and recovery");
	}

	private static void check(boolean passed, String description) {
		if (!passed) throw new AssertionError(description);
	}
}
