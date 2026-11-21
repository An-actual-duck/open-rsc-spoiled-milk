package orsc;

public final class PersistentMovementReceiptFixture {
	public static void main(String[] args) {
		PersistentMovementReceipt movement = new PersistentMovementReceipt();
		check(!movement.accept(1, 5, 100, 4), "new stream requires its real baseline");
		check(movement.accept(1, 5, 100, 5), "first stream starts at matching baseline");
		check(movement.accept(1, 5, 102, 6), "ordinary terrain revision leaves movement live");
		check(!movement.accept(1, 5, 101, 6), "old movement sequence is discarded");
		check(!movement.accept(1, 6, 103, 6), "an existing stream cannot change its anchor");
		check(movement.accept(2, 6, 104, 6), "relocation starts a new movement lifetime");
		check(!movement.accept(1, 5, 105, 6), "old travel lifetime is discarded");
		check(movement.accept(3, 6, 106, 6), "another relocation can use the same terrain scene");
		movement.reset();
		check(!movement.accept(3, 6, 107, 7), "world change rejects the old baseline");
		check(movement.accept(4, 7, Integer.MAX_VALUE, 7), "a new world establishes its own movement");
		check(movement.accept(4, 7, Integer.MIN_VALUE, 8), "ordered receipts survive sequence wrap");
		check(!movement.accept(4, 7, Integer.MAX_VALUE, 8), "pre-wrap duplicate is discarded");
		System.out.println("PASS persistent movement ordering, terrain revisions, relocation and recovery");
	}

	private static void check(boolean passed, String description) {
		if (!passed) throw new AssertionError(description);
	}
}
