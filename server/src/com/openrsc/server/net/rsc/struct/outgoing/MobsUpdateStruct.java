package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

import java.util.List;
import java.util.Map;

public class MobsUpdateStruct extends AbstractStruct<OpcodeOut> {

	public List<Map.Entry<Integer, Integer>> mobs;
	public List<Object> mobsUpdate; // retro can be byte or short

	public static final class BitUpdate implements Map.Entry<Integer, Integer> {
		private final int key;
		private final int value;

		public BitUpdate(final int key, final int value) {
			this.key = key;
			this.value = value;
		}

		public int getRawKey() {
			return key;
		}

		public int getRawValue() {
			return value;
		}

		@Override
		public Integer getKey() {
			return key;
		}

		@Override
		public Integer getValue() {
			return value;
		}

		@Override
		public Integer setValue(final Integer value) {
			throw new UnsupportedOperationException("BitUpdate is immutable");
		}
	}
}
