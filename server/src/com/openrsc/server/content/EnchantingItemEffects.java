package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.PrayerCatalog;

public final class EnchantingItemEffects {
	private static final class TieredLine {
		private final int altarId;
		private final int[] itemIds;

		private TieredLine(final int altarId, final int[] itemIds) {
			this.altarId = altarId;
			this.itemIds = itemIds;
		}
	}

	private static final class TieredEffect {
		private final TieredLine[] lines;
		private final int altarId;
		private final double step;

		private TieredEffect(final TieredLine[] lines, final int altarId, final double step) {
			this.lines = lines;
			this.altarId = altarId;
			this.step = step;
		}
	}

	public static final int AIR_ALTAR = 1190;
	public static final int MIND_ALTAR = 1192;
	public static final int WATER_ALTAR = 1194;
	public static final int EARTH_ALTAR = 1196;
	public static final int FIRE_ALTAR = 1198;
	public static final int BODY_ALTAR = 1200;
	public static final int COSMIC_ALTAR = 1202;
	public static final int CHAOS_ALTAR = 1204;
	public static final int NATURE_ALTAR = 1206;
	public static final int LAW_ALTAR = 1208;
	public static final int DEATH_ALTAR = 1210;
	public static final int BLOOD_ALTAR = 1212;
	public static final int SOUL_ALTAR = 1296;
	public static final int LIFE_ALTAR = 1321;

	private static final int[] ALL_ALTARS = {
		AIR_ALTAR,
		MIND_ALTAR,
		WATER_ALTAR,
		EARTH_ALTAR,
		FIRE_ALTAR,
		BODY_ALTAR,
		COSMIC_ALTAR,
		CHAOS_ALTAR,
		NATURE_ALTAR,
		LAW_ALTAR,
		DEATH_ALTAR,
		BLOOD_ALTAR,
		SOUL_ALTAR,
		LIFE_ALTAR
	};

	private static final int[] BASE_AMULETS = {
		ItemId.SAPPHIRE_AMULET.id(),
		ItemId.EMERALD_AMULET.id(),
		ItemId.RUBY_AMULET.id(),
		ItemId.DIAMOND_AMULET.id(),
		ItemId.UNENCHANTED_DRAGONSTONE_AMULET.id()
	};
	private static final int[] BASE_STAFFS = {
		ItemId.STAFF.id(),
		ItemId.PINE_STAFF.id(),
		ItemId.OAK_STAFF.id(),
		ItemId.WILLOW_STAFF.id(),
		ItemId.PALM_STAFF.id(),
		ItemId.MAPLE_STAFF.id(),
		ItemId.YEW_STAFF.id(),
		ItemId.EBONY_STAFF.id(),
		ItemId.MAGIC_WOOD_STAFF.id(),
		ItemId.BLOOD_STAFF.id()
	};
	private static final int[] BASE_WOOL_HATS = {
		ItemId.WOOL_WIZARD_HAT.id()
	};
	private static final int[] BASE_WOOL_TOPS = {
		ItemId.WOOL_ROBE_TOP.id()
	};
	private static final int[] BASE_WOOL_SKIRTS = {
		ItemId.WOOL_ROBE_SKIRT.id()
	};
	private static final int[] BASE_WOOL_GLOVES = {
		ItemId.WOOL_GLOVES.id()
	};
	private static final int[] BASE_WOOL_BOOTS = {
		ItemId.WOOL_BOOTS.id()
	};

	private static final int[] ALTAR_RUNES = {
		ItemId.AIR_RUNE.id(),
		ItemId.MIND_RUNE.id(),
		ItemId.WATER_RUNE.id(),
		ItemId.EARTH_RUNE.id(),
		ItemId.FIRE_RUNE.id(),
		ItemId.BODY_RUNE.id(),
		ItemId.COSMIC_RUNE.id(),
		ItemId.CHAOS_RUNE.id(),
		ItemId.NATURE_RUNE.id(),
		ItemId.LAW_RUNE.id(),
		ItemId.DEATH_RUNE.id(),
		ItemId.BLOOD_RUNE.id(),
		ItemId.SOUL_RUNE.id(),
		ItemId.LIFE_RUNE.id()
	};
	private static final int[][] ELEMENTAL_STAFFS = {
		{ItemId.STAFF_OF_AIR.id(), ItemId.PINE_STAFF_OF_AIR.id(), ItemId.OAK_STAFF_OF_AIR.id(), ItemId.WILLOW_STAFF_OF_AIR.id(), ItemId.PALM_STAFF_OF_AIR.id(), ItemId.MAPLE_STAFF_OF_AIR.id(), ItemId.YEW_STAFF_OF_AIR.id(), ItemId.EBONY_STAFF_OF_AIR.id(), ItemId.MAGIC_WOOD_STAFF_OF_AIR.id(), ItemId.BLOOD_STAFF_OF_AIR.id()},
		{ItemId.STAFF_OF_WATER.id(), ItemId.PINE_STAFF_OF_WATER.id(), ItemId.OAK_STAFF_OF_WATER.id(), ItemId.WILLOW_STAFF_OF_WATER.id(), ItemId.PALM_STAFF_OF_WATER.id(), ItemId.MAPLE_STAFF_OF_WATER.id(), ItemId.YEW_STAFF_OF_WATER.id(), ItemId.EBONY_STAFF_OF_WATER.id(), ItemId.MAGIC_WOOD_STAFF_OF_WATER.id(), ItemId.BLOOD_STAFF_OF_WATER.id()},
		{ItemId.STAFF_OF_EARTH.id(), ItemId.PINE_STAFF_OF_EARTH.id(), ItemId.OAK_STAFF_OF_EARTH.id(), ItemId.WILLOW_STAFF_OF_EARTH.id(), ItemId.PALM_STAFF_OF_EARTH.id(), ItemId.MAPLE_STAFF_OF_EARTH.id(), ItemId.YEW_STAFF_OF_EARTH.id(), ItemId.EBONY_STAFF_OF_EARTH.id(), ItemId.MAGIC_WOOD_STAFF_OF_EARTH.id(), ItemId.BLOOD_STAFF_OF_EARTH.id()},
		{ItemId.STAFF_OF_FIRE.id(), ItemId.PINE_STAFF_OF_FIRE.id(), ItemId.OAK_STAFF_OF_FIRE.id(), ItemId.WILLOW_STAFF_OF_FIRE.id(), ItemId.PALM_STAFF_OF_FIRE.id(), ItemId.MAPLE_STAFF_OF_FIRE.id(), ItemId.YEW_STAFF_OF_FIRE.id(), ItemId.EBONY_STAFF_OF_FIRE.id(), ItemId.MAGIC_WOOD_STAFF_OF_FIRE.id(), ItemId.BLOOD_STAFF_OF_FIRE.id()}
	};
	private static final int[] MIND_STAFFS = {
		ItemId.MIND_STAFF.id(), ItemId.MIND_PINE_STAFF.id(), ItemId.MIND_OAK_STAFF.id(), ItemId.MIND_WILLOW_STAFF.id(), ItemId.MIND_PALM_STAFF.id(), ItemId.MIND_MAPLE_STAFF.id(), ItemId.MIND_YEW_STAFF.id(), ItemId.MIND_EBONY_STAFF.id(), ItemId.MIND_MAGIC_STAFF.id(), ItemId.MIND_BLOOD_STAFF.id()
	};
	private static final int[] BODY_STAFFS = {
		ItemId.BODY_STAFF.id(), ItemId.BODY_PINE_STAFF.id(), ItemId.BODY_OAK_STAFF.id(), ItemId.BODY_WILLOW_STAFF.id(), ItemId.BODY_PALM_STAFF.id(), ItemId.BODY_MAPLE_STAFF.id(), ItemId.BODY_YEW_STAFF.id(), ItemId.BODY_EBONY_STAFF.id(), ItemId.BODY_MAGIC_STAFF.id(), ItemId.BODY_BLOOD_STAFF.id()
	};
	private static final int[] COSMIC_STAFFS = {
		ItemId.COSMIC_STAFF.id(), ItemId.COSMIC_PINE_STAFF.id(), ItemId.COSMIC_OAK_STAFF.id(), ItemId.COSMIC_WILLOW_STAFF.id(), ItemId.COSMIC_PALM_STAFF.id(), ItemId.COSMIC_MAPLE_STAFF.id(), ItemId.COSMIC_YEW_STAFF.id(), ItemId.COSMIC_EBONY_STAFF.id(), ItemId.COSMIC_MAGIC_STAFF.id(), ItemId.COSMIC_BLOOD_STAFF.id()
	};
	private static final int[] CHAOS_STAFFS = {
		ItemId.CHAOS_STAFF.id(), ItemId.CHAOS_PINE_STAFF.id(), ItemId.CHAOS_OAK_STAFF.id(), ItemId.CHAOS_WILLOW_STAFF.id(), ItemId.CHAOS_PALM_STAFF.id(), ItemId.CHAOS_MAPLE_STAFF.id(), ItemId.CHAOS_YEW_STAFF.id(), ItemId.CHAOS_EBONY_STAFF.id(), ItemId.CHAOS_MAGIC_STAFF.id(), ItemId.CHAOS_BLOOD_STAFF.id()
	};
	private static final int[] NATURE_STAFFS = {
		ItemId.NATURE_STAFF.id(), ItemId.NATURE_PINE_STAFF.id(), ItemId.NATURE_OAK_STAFF.id(), ItemId.NATURE_WILLOW_STAFF.id(), ItemId.NATURE_PALM_STAFF.id(), ItemId.NATURE_MAPLE_STAFF.id(), ItemId.NATURE_YEW_STAFF.id(), ItemId.NATURE_EBONY_STAFF.id(), ItemId.NATURE_MAGIC_STAFF.id(), ItemId.NATURE_BLOOD_STAFF.id()
	};
	private static final int[] LAW_STAFFS = {
		ItemId.LAW_STAFF.id(), ItemId.LAW_PINE_STAFF.id(), ItemId.LAW_OAK_STAFF.id(), ItemId.LAW_WILLOW_STAFF.id(), ItemId.LAW_PALM_STAFF.id(), ItemId.LAW_MAPLE_STAFF.id(), ItemId.LAW_YEW_STAFF.id(), ItemId.LAW_EBONY_STAFF.id(), ItemId.LAW_MAGIC_STAFF.id(), ItemId.LAW_BLOOD_STAFF.id()
	};
	private static final int[] DEATH_STAFFS = {
		ItemId.DEATH_STAFF.id(), ItemId.DEATH_PINE_STAFF.id(), ItemId.DEATH_OAK_STAFF.id(), ItemId.DEATH_WILLOW_STAFF.id(), ItemId.DEATH_PALM_STAFF.id(), ItemId.DEATH_MAPLE_STAFF.id(), ItemId.DEATH_YEW_STAFF.id(), ItemId.DEATH_EBONY_STAFF.id(), ItemId.DEATH_MAGIC_STAFF.id(), ItemId.DEATH_BLOOD_STAFF.id()
	};
	private static final int[] BLOOD_STAFFS = {
		ItemId.BLOOD_RUNE_STAFF.id(), ItemId.BLOOD_RUNE_PINE_STAFF.id(), ItemId.BLOOD_RUNE_OAK_STAFF.id(), ItemId.BLOOD_RUNE_WILLOW_STAFF.id(), ItemId.BLOOD_RUNE_PALM_STAFF.id(), ItemId.BLOOD_RUNE_MAPLE_STAFF.id(), ItemId.BLOOD_RUNE_YEW_STAFF.id(), ItemId.BLOOD_RUNE_EBONY_STAFF.id(), ItemId.BLOOD_RUNE_MAGIC_STAFF.id(), ItemId.BLOOD_RUNE_BLOOD_STAFF.id()
	};
	private static final int[] SOUL_STAFFS = {
		ItemId.SOUL_STAFF.id(), ItemId.SOUL_PINE_STAFF.id(), ItemId.SOUL_OAK_STAFF.id(), ItemId.SOUL_WILLOW_STAFF.id(), ItemId.SOUL_PALM_STAFF.id(), ItemId.SOUL_MAPLE_STAFF.id(), ItemId.SOUL_YEW_STAFF.id(), ItemId.SOUL_EBONY_STAFF.id(), ItemId.SOUL_MAGIC_STAFF.id(), ItemId.SOUL_BLOOD_STAFF.id()
	};
	private static final int[] LIFE_STAFFS = {
		ItemId.LIFE_STAFF.id(), ItemId.LIFE_PINE_STAFF.id(), ItemId.LIFE_OAK_STAFF.id(), ItemId.LIFE_WILLOW_STAFF.id(), ItemId.LIFE_PALM_STAFF.id(), ItemId.LIFE_MAPLE_STAFF.id(), ItemId.LIFE_YEW_STAFF.id(), ItemId.LIFE_EBONY_STAFF.id(), ItemId.LIFE_MAGIC_STAFF.id(), ItemId.LIFE_BLOOD_STAFF.id()
	};
	private static final String[] WOOL_ROBE_TIER_NAMES = {
		"Beginner's",
		"Novice",
		"Apprentice",
		"Adept",
		"Expert",
		"Master",
		"Mystic",
		"Arcane",
		"Elder",
		"Mythic"
	};
	private static final int[][] WOOL_HAT_PRODUCTS = {
		{2072, 2331, 2332, 2333, 2334, 2335, 2336, 2337, 2338, 2339},
		{2076, 2340, 2341, 2342, 2343, 2344, 2345, 2346, 2347, 2348},
		{2073, 2349, 2350, 2351, 2352, 2353, 2354, 2355, 2356, 2357},
		{2074, 2358, 2359, 2360, 2361, 2362, 2363, 2364, 2365, 2366},
		{2075, 2367, 2368, 2369, 2370, 2371, 2372, 2373, 2374, 2375},
		{2077, 2376, 2377, 2378, 2379, 2380, 2381, 2382, 2383, 2384},
		{2079, 2385, 2386, 2387, 2388, 2389, 2390, 2391, 2392, 2393},
		{2078, 2394, 2395, 2396, 2397, 2398, 2399, 2400, 2401, 2402},
		{2080, 2403, 2404, 2405, 2406, 2407, 2408, 2409, 2410, 2411},
		{2081, 2412, 2413, 2414, 2415, 2416, 2417, 2418, 2419, 2420},
		{2082, 2421, 2422, 2423, 2424, 2425, 2426, 2427, 2428, 2429},
		{2084, 2430, 2431, 2432, 2433, 2434, 2435, 2436, 2437, 2438},
		{2083, 2439, 2440, 2441, 2442, 2443, 2444, 2445, 2446, 2447},
		{2764, 2765, 2766, 2767, 2768, 2769, 2770, 2771, 2772, 2773}
	};
	private static final int[][] WOOL_TOP_PRODUCTS = {
		{2085, 2448, 2449, 2450, 2451, 2452, 2453, 2454, 2455, 2456},
		{2089, 2457, 2458, 2459, 2460, 2461, 2462, 2463, 2464, 2465},
		{2086, 2466, 2467, 2468, 2469, 2470, 2471, 2472, 2473, 2474},
		{2087, 2475, 2476, 2477, 2478, 2479, 2480, 2481, 2482, 2483},
		{2088, 2484, 2485, 2486, 2487, 2488, 2489, 2490, 2491, 2492},
		{2090, 2493, 2494, 2495, 2496, 2497, 2498, 2499, 2500, 2501},
		{2092, 2502, 2503, 2504, 2505, 2506, 2507, 2508, 2509, 2510},
		{2091, 2511, 2512, 2513, 2514, 2515, 2516, 2517, 2518, 2519},
		{2093, 2520, 2521, 2522, 2523, 2524, 2525, 2526, 2527, 2528},
		{2094, 2529, 2530, 2531, 2532, 2533, 2534, 2535, 2536, 2537},
		{2095, 2538, 2539, 2540, 2541, 2542, 2543, 2544, 2545, 2546},
		{2097, 2547, 2548, 2549, 2550, 2551, 2552, 2553, 2554, 2555},
		{2096, 2556, 2557, 2558, 2559, 2560, 2561, 2562, 2563, 2564},
		{2774, 2775, 2776, 2777, 2778, 2779, 2780, 2781, 2782, 2783}
	};
	private static final int[][] WOOL_SKIRT_PRODUCTS = {
		{2098, 2565, 2566, 2567, 2568, 2569, 2570, 2571, 2572, 2573},
		{2102, 2574, 2575, 2576, 2577, 2578, 2579, 2580, 2581, 2582},
		{2099, 2583, 2584, 2585, 2586, 2587, 2588, 2589, 2590, 2591},
		{2100, 2592, 2593, 2594, 2595, 2596, 2597, 2598, 2599, 2600},
		{2101, 2601, 2602, 2603, 2604, 2605, 2606, 2607, 2608, 2609},
		{2103, 2610, 2611, 2612, 2613, 2614, 2615, 2616, 2617, 2618},
		{2105, 2619, 2620, 2621, 2622, 2623, 2624, 2625, 2626, 2627},
		{2104, 2628, 2629, 2630, 2631, 2632, 2633, 2634, 2635, 2636},
		{2106, 2637, 2638, 2639, 2640, 2641, 2642, 2643, 2644, 2645},
		{2107, 2646, 2647, 2648, 2649, 2650, 2651, 2652, 2653, 2654},
		{2108, 2655, 2656, 2657, 2658, 2659, 2660, 2661, 2662, 2663},
		{2110, 2664, 2665, 2666, 2667, 2668, 2669, 2670, 2671, 2672},
		{2109, 2673, 2674, 2675, 2676, 2677, 2678, 2679, 2680, 2681},
		{2784, 2785, 2786, 2787, 2788, 2789, 2790, 2791, 2792, 2793}
	};

	private static final int[][] WOOL_GLOVE_PRODUCTS = {
		{2796, 2797, 2798, 2799, 2800, 2801, 2802, 2803, 2804, 2805},
		{2806, 2807, 2808, 2809, 2810, 2811, 2812, 2813, 2814, 2815},
		{2816, 2817, 2818, 2819, 2820, 2821, 2822, 2823, 2824, 2825},
		{2826, 2827, 2828, 2829, 2830, 2831, 2832, 2833, 2834, 2835},
		{2836, 2837, 2838, 2839, 2840, 2841, 2842, 2843, 2844, 2845},
		{2846, 2847, 2848, 2849, 2850, 2851, 2852, 2853, 2854, 2855},
		{2856, 2857, 2858, 2859, 2860, 2861, 2862, 2863, 2864, 2865},
		{2866, 2867, 2868, 2869, 2870, 2871, 2872, 2873, 2874, 2875},
		{2876, 2877, 2878, 2879, 2880, 2881, 2882, 2883, 2884, 2885},
		{2886, 2887, 2888, 2889, 2890, 2891, 2892, 2893, 2894, 2895},
		{2896, 2897, 2898, 2899, 2900, 2901, 2902, 2903, 2904, 2905},
		{2906, 2907, 2908, 2909, 2910, 2911, 2912, 2913, 2914, 2915},
		{2916, 2917, 2918, 2919, 2920, 2921, 2922, 2923, 2924, 2925},
		{2926, 2927, 2928, 2929, 2930, 2931, 2932, 2933, 2934, 2935},
	};

	private static final int[][] WOOL_BOOT_PRODUCTS = {
		{2936, 2937, 2938, 2939, 2940, 2941, 2942, 2943, 2944, 2945},
		{2946, 2947, 2948, 2949, 2950, 2951, 2952, 2953, 2954, 2955},
		{2956, 2957, 2958, 2959, 2960, 2961, 2962, 2963, 2964, 2965},
		{2966, 2967, 2968, 2969, 2970, 2971, 2972, 2973, 2974, 2975},
		{2976, 2977, 2978, 2979, 2980, 2981, 2982, 2983, 2984, 2985},
		{2986, 2987, 2988, 2989, 2990, 2991, 2992, 2993, 2994, 2995},
		{2996, 2997, 2998, 2999, 3000, 3001, 3002, 3003, 3004, 3005},
		{3006, 3007, 3008, 3009, 3010, 3011, 3012, 3013, 3014, 3015},
		{3016, 3017, 3018, 3019, 3020, 3021, 3022, 3023, 3024, 3025},
		{3026, 3027, 3028, 3029, 3030, 3031, 3032, 3033, 3034, 3035},
		{3036, 3037, 3038, 3039, 3040, 3041, 3042, 3043, 3044, 3045},
		{3046, 3047, 3048, 3049, 3050, 3051, 3052, 3053, 3054, 3055},
		{3056, 3057, 3058, 3059, 3060, 3061, 3062, 3063, 3064, 3065},
		{3066, 3067, 3068, 3069, 3070, 3071, 3072, 3073, 3074, 3075},
	};

	private static final int[][] ELEMENTAL_AMULETS = {
		{1593, 1594, 1595, 1596, 1597}, // wind
		{1598, 1599, 1600, 1601, 1602}, // water
		{1603, 1604, 1605, 1606, 1607}, // earth
		{1608, 1609, 1610, 1611, 1612}  // fire
	};
	private static final int[] CHAOS_AMULETS = {
		1719, 1720, 1721, 1722, 1723
	};
	private static final int[] DEATH_AMULETS = {
		1724, 1725, 1726, 1727, 1728
	};
	private static final int[] BLOOD_AMULETS = {
		1729, 1730, 1731, 1732, 1733
	};
	private static final int[] MIND_AMULETS = {
		1734, 1735, 1736, 1737, 1738
	};
	private static final int[] BODY_AMULETS = {
		1739, 1740, 1741, 1742, 1743
	};
	private static final int[] NATURE_AMULETS = {
		1744, 1745, 1746, 1747, 1748
	};
	private static final int[] COSMIC_AMULETS = {
		1749, 1750, 1751, 1752, 1753
	};
	private static final int[] SOUL_AMULETS = {
		1754, 1755, 1756, 1757, 1758
	};
	private static final int[] LAW_AMULETS = {
		1709, 1710, 1711, 1712, 1713
	};
	private static final int[] SOUL_NECKLACES = {
		1759, 1760, 1761, 1762, 1763
	};
	private static final int[] LIFE_NECKLACES = {
		3101, 3102, 3103, 3104, 3105
	};
	private static final int[] LIFE_AMULETS = {
		3106, 3107, 3108, 3109, 3110
	};
	private static final int[] BASE_NECKLACES = {
		289, 290, 291, 292, 544
	};
	private static final int[] BASE_RINGS = {
		ItemId.SAPPHIRE_RING.id(),
		ItemId.EMERALD_RING.id(),
		ItemId.RUBY_RING.id(),
		ItemId.DIAMOND_RING.id(),
		ItemId.DRAGONSTONE_RING.id()
	};
	private static final int[][] NECKLACES = {
		{1613, 1614, 1615, 1616, 1617}, // air
		{1618, 1619, 1620, 1621, 1622}, // mind
		{1623, 1624, 1625, 1626, 1627}, // water
		{1628, 1629, 1630, 1631, 1632}, // earth
		{1633, 1634, 1635, 1636, 1637}, // fire
		{1638, 1639, 1640, 1641, 1642}, // body
		{1643, 1644, 1645, 1646, 1647}, // cosmic
		{1648, 1649, 1650, 1651, 1652}, // chaos
		{1653, 1654, 1655, 1656, 1657}, // nature
		{1658, 1659, 1660, 1661, 1662}, // law
		{1663, 1664, 1665, 1666, 1667}, // death
		{1668, 1669, 1670, 1671, 1672}  // blood
	};
	private static final int[][] ELEMENTAL_RINGS = {
		{1673, 1674, 1675, 1676, 1677}, // air
		{1678, 1679, 1680, 1681, 1682}, // water
		{1683, 1684, 1685, 1686, 1687}, // earth
		{1688, 1689, 1690, 1691, 1692}  // fire
	};
	private static final int[] CHAOS_RINGS = {
		ItemId.RING_OF_RECOIL.id(), 1693, 1694, 1695, 1696
	};
	private static final int[] NATURE_RINGS = {
		ItemId.RING_OF_FORGING.id(), 1697, 1698, 1699, 1700
	};
	private static final int[] COSMIC_RINGS = {
		1701, 1702, 1703, 1704, ItemId.DRAGONSTONE_RING_OF_FORTUNE.id()
	};
	private static final int[] SOUL_RINGS = {
		1705, 1706, 1707, ItemId.RING_OF_LIFE.id(), 1708
	};
	private static final int[] LAW_RINGS = {
		1714, 1715, 1716, 1717, 1718
	};
	private static final int[] MIND_RINGS = {
		3076, 3077, 3078, 3079, 3080
	};
	private static final int[] BODY_RINGS = {
		3081, 3082, 3083, 3084, 3085
	};
	private static final int[] DEATH_RINGS = {
		3086, 3087, 3088, 3089, 3090
	};
	private static final int[] BLOOD_RINGS = {
		3091, 3092, 3093, 3094, 3095
	};
	private static final int[] LIFE_RINGS = {
		3096, 3097, 3098, 3099, 3100
	};
	private static final TieredLine[] SPECIAL_AMULET_LINES = {
		new TieredLine(LAW_ALTAR, LAW_AMULETS),
		new TieredLine(CHAOS_ALTAR, CHAOS_AMULETS),
		new TieredLine(DEATH_ALTAR, DEATH_AMULETS),
		new TieredLine(BLOOD_ALTAR, BLOOD_AMULETS),
		new TieredLine(MIND_ALTAR, MIND_AMULETS),
		new TieredLine(BODY_ALTAR, BODY_AMULETS),
		new TieredLine(NATURE_ALTAR, NATURE_AMULETS),
		new TieredLine(COSMIC_ALTAR, COSMIC_AMULETS),
		new TieredLine(SOUL_ALTAR, SOUL_AMULETS),
		new TieredLine(LIFE_ALTAR, LIFE_AMULETS)
	};
	private static final TieredLine[] SPECIAL_RING_LINES = {
		new TieredLine(MIND_ALTAR, MIND_RINGS),
		new TieredLine(BODY_ALTAR, BODY_RINGS),
		new TieredLine(CHAOS_ALTAR, CHAOS_RINGS),
		new TieredLine(NATURE_ALTAR, NATURE_RINGS),
		new TieredLine(COSMIC_ALTAR, COSMIC_RINGS),
		new TieredLine(LAW_ALTAR, LAW_RINGS),
		new TieredLine(DEATH_ALTAR, DEATH_RINGS),
		new TieredLine(BLOOD_ALTAR, BLOOD_RINGS),
		new TieredLine(SOUL_ALTAR, SOUL_RINGS),
		new TieredLine(LIFE_ALTAR, LIFE_RINGS)
	};
	private static final TieredLine[] ELEMENTAL_AMULET_LINES = {
		new TieredLine(AIR_ALTAR, ELEMENTAL_AMULETS[0]),
		new TieredLine(WATER_ALTAR, ELEMENTAL_AMULETS[1]),
		new TieredLine(EARTH_ALTAR, ELEMENTAL_AMULETS[2]),
		new TieredLine(FIRE_ALTAR, ELEMENTAL_AMULETS[3])
	};
	private static final TieredLine[] ELEMENTAL_RING_LINES = {
		new TieredLine(AIR_ALTAR, ELEMENTAL_RINGS[0]),
		new TieredLine(WATER_ALTAR, ELEMENTAL_RINGS[1]),
		new TieredLine(EARTH_ALTAR, ELEMENTAL_RINGS[2]),
		new TieredLine(FIRE_ALTAR, ELEMENTAL_RINGS[3])
	};
	private static final TieredLine[] STANDARD_STAFF_LINES = {
		new TieredLine(AIR_ALTAR, ELEMENTAL_STAFFS[0]),
		new TieredLine(MIND_ALTAR, MIND_STAFFS),
		new TieredLine(WATER_ALTAR, ELEMENTAL_STAFFS[1]),
		new TieredLine(EARTH_ALTAR, ELEMENTAL_STAFFS[2]),
		new TieredLine(FIRE_ALTAR, ELEMENTAL_STAFFS[3]),
		new TieredLine(BODY_ALTAR, BODY_STAFFS),
		new TieredLine(COSMIC_ALTAR, COSMIC_STAFFS),
		new TieredLine(CHAOS_ALTAR, CHAOS_STAFFS),
		new TieredLine(NATURE_ALTAR, NATURE_STAFFS),
		new TieredLine(LAW_ALTAR, LAW_STAFFS),
		new TieredLine(DEATH_ALTAR, DEATH_STAFFS),
		new TieredLine(BLOOD_ALTAR, BLOOD_STAFFS),
		new TieredLine(SOUL_ALTAR, SOUL_STAFFS),
		new TieredLine(LIFE_ALTAR, LIFE_STAFFS)
	};
	private static final TieredLine[] STANDARD_NECKLACE_LINES = {
		new TieredLine(AIR_ALTAR, NECKLACES[0]),
		new TieredLine(MIND_ALTAR, NECKLACES[1]),
		new TieredLine(WATER_ALTAR, NECKLACES[2]),
		new TieredLine(EARTH_ALTAR, NECKLACES[3]),
		new TieredLine(FIRE_ALTAR, NECKLACES[4]),
		new TieredLine(BODY_ALTAR, NECKLACES[5]),
		new TieredLine(COSMIC_ALTAR, NECKLACES[6]),
		new TieredLine(CHAOS_ALTAR, NECKLACES[7]),
		new TieredLine(NATURE_ALTAR, NECKLACES[8]),
		new TieredLine(LAW_ALTAR, NECKLACES[9]),
		new TieredLine(DEATH_ALTAR, NECKLACES[10]),
		new TieredLine(BLOOD_ALTAR, NECKLACES[11])
	};
	private static final TieredEffect ELEMENTAL_RING_DAMAGE_EFFECT =
		new TieredEffect(ELEMENTAL_RING_LINES, AIR_ALTAR, 3.0D);
	private static final TieredEffect CHAOS_RING_RECOIL_EFFECT =
		new TieredEffect(SPECIAL_RING_LINES, CHAOS_ALTAR, 0.08D);
	private static final TieredEffect NATURE_RING_FORGING_EFFECT =
		new TieredEffect(SPECIAL_RING_LINES, NATURE_ALTAR, 0.10D);
	private static final TieredEffect COSMIC_RING_ADDITIONAL_ROLL_EFFECT =
		new TieredEffect(SPECIAL_RING_LINES, COSMIC_ALTAR, 0.05D);
	private static final TieredEffect SOUL_RING_SURVIVAL_EFFECT =
		new TieredEffect(SPECIAL_RING_LINES, SOUL_ALTAR, 0.05D);
	private static final TieredEffect NECKLACE_RUNE_PRESERVATION_EFFECT =
		new TieredEffect(STANDARD_NECKLACE_LINES, AIR_ALTAR, 0.10D);
	private static final TieredEffect AIR_AMULET_SPEED_EFFECT =
		new TieredEffect(ELEMENTAL_AMULET_LINES, AIR_ALTAR, 0.02D);
	private static final TieredEffect WATER_AMULET_HIGH_ROLL_EFFECT =
		new TieredEffect(ELEMENTAL_AMULET_LINES, WATER_ALTAR, 0.02D);
	private static final TieredEffect CHAOS_AMULET_SECOND_HIT_EFFECT =
		new TieredEffect(SPECIAL_AMULET_LINES, CHAOS_ALTAR, 0.15D);
	private static final TieredEffect DEATH_AMULET_KILL_CHAIN_EFFECT =
		new TieredEffect(SPECIAL_AMULET_LINES, DEATH_ALTAR, 0.01D);
	private static final TieredEffect BLOOD_AMULET_MAX_HITS_EFFECT =
		new TieredEffect(SPECIAL_AMULET_LINES, BLOOD_ALTAR, 0.05D);
	private static final TieredEffect MIND_AMULET_XP_EFFECT =
		new TieredEffect(SPECIAL_AMULET_LINES, MIND_ALTAR, 0.05D);
	private static final TieredEffect BODY_AMULET_XP_EFFECT =
		new TieredEffect(SPECIAL_AMULET_LINES, BODY_ALTAR, 0.05D);
	private static final TieredEffect NATURE_AMULET_FOOD_EFFECT =
		new TieredEffect(SPECIAL_AMULET_LINES, NATURE_ALTAR, 0.10D);
	private static final TieredEffect COSMIC_AMULET_EXTRA_RESOURCE_EFFECT =
		new TieredEffect(SPECIAL_AMULET_LINES, COSMIC_ALTAR, 0.03D);
	private static final TieredEffect COSMIC_AMULET_HERB_QUALITY_EFFECT =
		new TieredEffect(SPECIAL_AMULET_LINES, COSMIC_ALTAR, 0.03D);

	private EnchantingItemEffects() {
	}

	public static final int MIN_ENCHANTED_WOOL_ROBE_TIER = 1;
	public static final int MAX_ENCHANTED_WOOL_ROBE_TIER = 10;

	public static boolean isElementalAmuletBase(final int itemId) {
		return isAmuletBase(itemId);
	}

	public static boolean isBaseStaff(final int itemId) {
		return contains(BASE_STAFFS, itemId);
	}

	public static boolean isBaseWoolRobePiece(final int itemId) {
		return contains(BASE_WOOL_HATS, itemId)
			|| contains(BASE_WOOL_TOPS, itemId)
			|| contains(BASE_WOOL_SKIRTS, itemId)
			|| contains(BASE_WOOL_GLOVES, itemId)
			|| contains(BASE_WOOL_BOOTS, itemId);
	}

	public static boolean isElementalStaff(final int itemId) {
		return isEnchantedStaff(itemId);
	}

	public static boolean isEnchantedStaff(final int itemId) {
		return containsInLines(STANDARD_STAFF_LINES, itemId);
	}

	public static boolean isAmuletBase(final int itemId) {
		for (int baseId : BASE_AMULETS) {
			if (baseId == itemId) {
				return true;
			}
		}
		return false;
	}

	public static boolean isElementalAmulet(final int itemId) {
		return containsInLines(ELEMENTAL_AMULET_LINES, itemId);
	}

	public static boolean isLawAmulet(final int itemId) {
		return contains(LAW_AMULETS, itemId);
	}

	public static boolean isChaosAmulet(final int itemId) {
		return contains(CHAOS_AMULETS, itemId);
	}

	public static boolean isDeathAmulet(final int itemId) {
		return contains(DEATH_AMULETS, itemId);
	}

	public static boolean isBloodAmulet(final int itemId) {
		return contains(BLOOD_AMULETS, itemId);
	}

	public static boolean isMindAmulet(final int itemId) {
		return contains(MIND_AMULETS, itemId);
	}

	public static boolean isBodyAmulet(final int itemId) {
		return contains(BODY_AMULETS, itemId);
	}

	public static boolean isNatureAmulet(final int itemId) {
		return contains(NATURE_AMULETS, itemId);
	}

	public static boolean isCosmicAmulet(final int itemId) {
		return contains(COSMIC_AMULETS, itemId);
	}

	public static boolean isSoulAmulet(final int itemId) {
		return contains(SOUL_AMULETS, itemId);
	}

	public static boolean isNecklaceBase(final int itemId) {
		for (int baseId : BASE_NECKLACES) {
			if (baseId == itemId) {
				return true;
			}
		}
		return false;
	}

	public static boolean isEnchantedNecklace(final int itemId) {
		return containsInLines(STANDARD_NECKLACE_LINES, itemId) || contains(SOUL_NECKLACES, itemId) || contains(LIFE_NECKLACES, itemId);
	}

	public static boolean isRingBase(final int itemId) {
		for (int baseId : BASE_RINGS) {
			if (baseId == itemId) {
				return true;
			}
		}
		return false;
	}

	public static boolean isElementalRing(final int itemId) {
		return containsInLines(ELEMENTAL_RING_LINES, itemId);
	}

	public static boolean isChaosRing(final int itemId) {
		return contains(CHAOS_RINGS, itemId);
	}

	public static boolean isNatureRing(final int itemId) {
		return contains(NATURE_RINGS, itemId);
	}

	public static boolean isWealthRing(final int itemId) {
		return contains(COSMIC_RINGS, itemId);
	}

	public static boolean isSoulRing(final int itemId) {
		return contains(SOUL_RINGS, itemId);
	}

	public static boolean isLawRing(final int itemId) {
		return contains(LAW_RINGS, itemId);
	}

	public static int getAmuletProduct(final int altarId, final int baseAmuletId) {
		final int[] productLine = getAmuletProductLine(altarId);
		final int tierIndex = getTierIndexForBaseAmulet(baseAmuletId);
		return getTieredProduct(productLine, tierIndex);
	}

	public static int getElementalAmuletProduct(final int altarId, final int baseAmuletId) {
		return getTieredProduct(
			getTieredLineItemsByAltar(altarId, ELEMENTAL_AMULET_LINES),
			getTierIndexForBaseAmulet(baseAmuletId)
		);
	}

	public static int getRuneForAltar(final int altarId) {
		int altarIndex = getAltarIndex(altarId);
		if (altarIndex < 0 || altarIndex >= ALTAR_RUNES.length) {
			return -1;
		}
		return ALTAR_RUNES[altarIndex];
	}

	public static boolean isAltarRune(final int itemId) {
		return contains(ALTAR_RUNES, itemId);
	}

	public static int getElementalRingProduct(final int altarId, final int baseRingId) {
		return getTieredProduct(
			getTieredLineItemsByAltar(altarId, ELEMENTAL_RING_LINES),
			getTierIndexForBaseRing(baseRingId)
		);
	}

	public static int getRingProduct(final int altarId, final int baseRingId) {
		final int[] productLine = getRingProductLine(altarId);
		final int tierIndex = getTierIndexForBaseRing(baseRingId);
		return getTieredProduct(productLine, tierIndex);
	}

	public static int getNecklaceProduct(final int altarId, final int baseNecklaceId) {
		final int[] productLine = getNecklaceProductLine(altarId);
		final int tierIndex = getTierIndexForBaseNecklace(baseNecklaceId);
		return getTieredProduct(productLine, tierIndex);
	}

	public static int getStaffProduct(final int altarId, final int baseStaffId) {
		return getTieredProduct(
			getTieredLineItemsByAltar(altarId, STANDARD_STAFF_LINES),
			getTierIndexForBaseStaff(baseStaffId)
		);
	}

	public static int getWoolRobeProduct(final int altarId, final int baseItemId) {
		return getWoolRobeProduct(altarId, baseItemId, 1);
	}

	public static int getWoolRobeProduct(final int altarId, final int baseItemId, final int tier) {
		final int altarIndex = getAltarIndex(altarId);
		if (altarIndex == -1 || tier < 1 || tier > MAX_ENCHANTED_WOOL_ROBE_TIER) {
			return -1;
		}
		if (contains(BASE_WOOL_HATS, baseItemId)) {
			return WOOL_HAT_PRODUCTS[altarIndex][tier - 1];
		}
		if (contains(BASE_WOOL_TOPS, baseItemId)) {
			return WOOL_TOP_PRODUCTS[altarIndex][tier - 1];
		}
		if (contains(BASE_WOOL_SKIRTS, baseItemId)) {
			return WOOL_SKIRT_PRODUCTS[altarIndex][tier - 1];
		}
		if (contains(BASE_WOOL_GLOVES, baseItemId)) {
			return WOOL_GLOVE_PRODUCTS[altarIndex][tier - 1];
		}
		if (contains(BASE_WOOL_BOOTS, baseItemId)) {
			return WOOL_BOOT_PRODUCTS[altarIndex][tier - 1];
		}
		return -1;
	}

	public static int getWoolRobeProductForPiece(final int altarId, final int itemId, final int tier) {
		final int altarIndex = getAltarIndex(altarId);
		if (altarIndex == -1 || tier < 1 || tier > MAX_ENCHANTED_WOOL_ROBE_TIER) {
			return -1;
		}
		if (isWoolRobeHat(itemId)) {
			return WOOL_HAT_PRODUCTS[altarIndex][tier - 1];
		}
		if (isWoolRobeTop(itemId)) {
			return WOOL_TOP_PRODUCTS[altarIndex][tier - 1];
		}
		if (isWoolRobeSkirt(itemId)) {
			return WOOL_SKIRT_PRODUCTS[altarIndex][tier - 1];
		}
		if (isWoolRobeGloves(itemId)) {
			return WOOL_GLOVE_PRODUCTS[altarIndex][tier - 1];
		}
		if (isWoolRobeBoots(itemId)) {
			return WOOL_BOOT_PRODUCTS[altarIndex][tier - 1];
		}
		return -1;
	}

	public static boolean isEnchantedWoolRobePiece(final int itemId) {
		return getAltarIdForWoolRobeItem(itemId) != -1;
	}

	public static boolean isWoolRobeHat(final int itemId) {
		return contains(BASE_WOOL_HATS, itemId) || containsInProductMatrix(WOOL_HAT_PRODUCTS, itemId);
	}

	public static boolean isWoolRobeTop(final int itemId) {
		return contains(BASE_WOOL_TOPS, itemId) || containsInProductMatrix(WOOL_TOP_PRODUCTS, itemId);
	}

	public static boolean isWoolRobeSkirt(final int itemId) {
		return contains(BASE_WOOL_SKIRTS, itemId) || containsInProductMatrix(WOOL_SKIRT_PRODUCTS, itemId);
	}

	public static boolean isWoolRobeGloves(final int itemId) {
		return contains(BASE_WOOL_GLOVES, itemId) || containsInProductMatrix(WOOL_GLOVE_PRODUCTS, itemId);
	}

	public static boolean isWoolRobeBoots(final int itemId) {
		return contains(BASE_WOOL_BOOTS, itemId) || containsInProductMatrix(WOOL_BOOT_PRODUCTS, itemId);
	}

	public static int getAltarIdForWoolRobeItem(final int itemId) {
		for (int altarIndex = 0; altarIndex < ALL_ALTARS.length; altarIndex++) {
			if (contains(WOOL_HAT_PRODUCTS[altarIndex], itemId)
				|| contains(WOOL_TOP_PRODUCTS[altarIndex], itemId)
				|| contains(WOOL_SKIRT_PRODUCTS[altarIndex], itemId)
				|| contains(WOOL_GLOVE_PRODUCTS[altarIndex], itemId)
				|| contains(WOOL_BOOT_PRODUCTS[altarIndex], itemId)) {
				return ALL_ALTARS[altarIndex];
			}
		}
		return -1;
	}

	public static String getWoolRobeTierName(final int tier) {
		return tier < 1 || tier > WOOL_ROBE_TIER_NAMES.length ? "Tier " + tier : WOOL_ROBE_TIER_NAMES[tier - 1];
	}

	public static int getHighestEnchantingTierForLevel(final int level) {
		for (int tier = MAX_ENCHANTED_WOOL_ROBE_TIER; tier >= 1; tier--) {
			if (level >= getTemporaryEnchantingRequirementForTier(tier)) {
				return tier;
			}
		}
		return 0;
	}

	public static int getWoolRobeTier(final Item item) {
		if (item == null) {
			return 1;
		}
		final int itemId = item.getCatalogId();
		if (isBaseWoolRobePiece(itemId)) {
			return 1;
		}
		if (!isEnchantedWoolRobePiece(itemId)) {
			return 1;
		}
		final int storedTier = item.getItemStatus().getDurability();
		final int derivedTier = getTierForWoolRobeItem(itemId);
		if (derivedTier == 1 && storedTier >= 2 && storedTier <= MAX_ENCHANTED_WOOL_ROBE_TIER) {
			return storedTier;
		}
		return derivedTier == -1 ? 1 : derivedTier;
	}

	public static int getWoolRobeMagicDefense(final Item item) {
		return getWoolRobeDefenseAllocation(item)[2];
	}

	public static int getWoolRobeMeleeDefense(final Item item) {
		return getWoolRobeDefenseAllocation(item)[0];
	}

	public static int getWoolRobeRangedDefense(final Item item) {
		return getWoolRobeDefenseAllocation(item)[1];
	}

	public static int getRunePreservationRune(final int itemId) {
		final TieredLine line = getTieredLineForItem(itemId, STANDARD_NECKLACE_LINES);
		return line == null ? -1 : getRuneForAltar(line.altarId);
	}

	public static double getStaffRunePreservationChance(final int itemId, final int runeId) {
		return isStaffForRune(itemId, runeId) ? 0.50D : 0.0D;
	}

	public static double getWoolRobeRunePreservationChance(final int itemId, final int runeId) {
		final int altarId = getAltarIdForWoolRobeItem(itemId);
		if (altarId == -1 || getRuneForAltar(altarId) != runeId) {
			return 0.0D;
		}
		return 0.10D;
	}

	public static double getElementalRingDamageBonus(final int itemId) {
		return getTieredEffectValue(itemId, ELEMENTAL_RING_DAMAGE_EFFECT);
	}

	public static int getElementalPowerBonus(final int itemId, final PrayerCatalog.CombatStyle combatStyle) {
		final int ringBonus = getElementalJewelryPowerBonus(itemId, ELEMENTAL_RING_LINES, combatStyle);
		if (ringBonus > 0) {
			return ringBonus;
		}
		return getElementalJewelryPowerBonus(itemId, STANDARD_NECKLACE_LINES, combatStyle);
	}

	public static int getElementalDefenseBonus(final int itemId, final PrayerCatalog.CombatStyle combatStyle) {
		final int waterTier = getTierForAltar(itemId, ELEMENTAL_AMULET_LINES, WATER_ALTAR);
		if (waterTier != -1) {
			return waterTier * 2;
		}
		final int tier;
		switch (combatStyle) {
			case RANGED:
				tier = getTierForAltar(itemId, ELEMENTAL_AMULET_LINES, AIR_ALTAR);
				return tier == -1 ? 0 : tier * 3;
			case MELEE:
				tier = getTierForAltar(itemId, ELEMENTAL_AMULET_LINES, EARTH_ALTAR);
				return tier == -1 ? 0 : tier * 3;
			case MAGIC:
				tier = getTierForAltar(itemId, ELEMENTAL_AMULET_LINES, FIRE_ALTAR);
				return tier == -1 ? 0 : tier * 3;
			default:
				return 0;
		}
	}

	public static double getChaosRingRecoilChance(final int itemId) {
		final double ringChance = getTieredEffectValue(itemId, CHAOS_RING_RECOIL_EFFECT);
		if (ringChance > 0.0D) {
			return ringChance;
		}
		final int necklaceTier = getTierForAltar(itemId, STANDARD_NECKLACE_LINES, CHAOS_ALTAR);
		return necklaceTier == -1 ? 0.0D : necklaceTier * 0.08D;
	}

	public static double getNatureRingForgingChance(final int itemId) {
		return getTieredEffectValue(itemId, NATURE_RING_FORGING_EFFECT);
	}

	public static double getWealthAdditionalRollChance(final int itemId) {
		return getTieredEffectValue(itemId, COSMIC_RING_ADDITIONAL_ROLL_EFFECT);
	}

	public static double getCosmicNecklaceStandardDropChance(final int itemId) {
		final int tier = getTierForAltar(itemId, STANDARD_NECKLACE_LINES, COSMIC_ALTAR);
		return tier == -1 ? 0.0D : tier * 0.10D;
	}

	public static double getSoulRingSurvivalChance(final int itemId) {
		return getTieredEffectValue(itemId, SOUL_RING_SURVIVAL_EFFECT);
	}

	public static int getLawItemMaxCharges(final int itemId) {
		if (getTierForAltar(itemId, SPECIAL_AMULET_LINES, LAW_ALTAR) != -1) {
			return 3;
		}
		int tier = getTierForAltar(itemId, SPECIAL_RING_LINES, LAW_ALTAR);
		if (tier == -1) {
			tier = getTierForAltar(itemId, STANDARD_NECKLACE_LINES, LAW_ALTAR);
		}
		if (tier != -1) {
			switch (tier) {
				case 1:
					return 50;
				case 2:
					return 150;
				case 3:
					return 300;
				case 4:
					return 500;
				case 5:
					return 750;
				default:
					return 0;
			}
		}
		return 0;
	}

	public static int getInitialItemDurability(final int itemId) {
		final int lawCharges = getLawItemMaxCharges(itemId);
		return lawCharges > 0 ? lawCharges : 100;
	}

	public static int getLawAmuletTier(final int itemId) {
		return getTierForAltar(itemId, SPECIAL_AMULET_LINES, LAW_ALTAR);
	}

	public static double getRunePreservationChance(final int itemId) {
		return getTierScaledValue(itemId, STANDARD_NECKLACE_LINES, NECKLACE_RUNE_PRESERVATION_EFFECT.step);
	}

	public static boolean isSupportedAltar(final int altarId) {
		return getAltarIndex(altarId) != -1;
	}

	public static int getTierForBaseAmulet(final int baseAmuletId) {
		int tierIndex = getTierIndexForBaseAmulet(baseAmuletId);
		return tierIndex == -1 ? -1 : tierIndex + 1;
	}

	public static int getTierForBaseNecklace(final int baseNecklaceId) {
		int tierIndex = getTierIndexForBaseNecklace(baseNecklaceId);
		return tierIndex == -1 ? -1 : tierIndex + 1;
	}

	public static int getTierForBaseRing(final int baseRingId) {
		int tierIndex = getTierIndexForBaseRing(baseRingId);
		return tierIndex == -1 ? -1 : tierIndex + 1;
	}

	public static int getTierForBaseStaff(final int baseStaffId) {
		int tierIndex = getTierIndexForBaseStaff(baseStaffId);
		return tierIndex == -1 ? -1 : tierIndex + 1;
	}

	public static int getRuneCostForTier(final int tier) {
		return tier > 0 ? tier * 50 : -1;
	}

	public static int getJewelryEnchantingRequirementForTier(final int tier) {
		switch (tier) {
			case 1:
				return 8;
			case 2:
				return 18;
			case 3:
				return 32;
			case 4:
				return 48;
			case 5:
				return 58;
			default:
				return -1;
		}
	}

	public static int getStaffEnchantingRequirementForTier(final int tier) {
		return getTemporaryEnchantingRequirementForTier(tier);
	}

	public static int getAltarTier(final int altarId) {
		switch (altarId) {
			case AIR_ALTAR:
			case WATER_ALTAR:
			case EARTH_ALTAR:
			case FIRE_ALTAR:
			case LIFE_ALTAR:
				return 2;
			case MIND_ALTAR:
			case BODY_ALTAR:
				return 3;
			case CHAOS_ALTAR:
				return 4;
			case COSMIC_ALTAR:
				return 5;
			case NATURE_ALTAR:
				return 6;
			case LAW_ALTAR:
				return 7;
			case DEATH_ALTAR:
				return 8;
			case SOUL_ALTAR:
				return 9;
			case BLOOD_ALTAR:
				return 10;
			default:
				return -1;
		}
	}

	public static int getAltarLevelRequirement(final int altarId) {
		switch (altarId) {
			case AIR_ALTAR:
			case WATER_ALTAR:
			case EARTH_ALTAR:
			case FIRE_ALTAR:
			case LIFE_ALTAR:
				return 1;
			case MIND_ALTAR:
				return 8;
			case BODY_ALTAR:
				return 15;
			case CHAOS_ALTAR:
				return 22;
			case COSMIC_ALTAR:
				return 30;
			case NATURE_ALTAR:
				return 38;
			case LAW_ALTAR:
				return 46;
			case DEATH_ALTAR:
				return 54;
			case SOUL_ALTAR:
				return 62;
			case BLOOD_ALTAR:
				return 70;
			default:
				return -1;
		}
	}

	public static int getWoolRobeRuneCost(final int tier) {
		return tier > 0 ? tier * 100 : -1;
	}

	public static int getStaffRuneCost(final int tier) {
		return tier > 0 ? tier * 200 : -1;
	}

	public static int getTemporaryEnchantingRequirementForTier(final int tier) {
		switch (tier) {
			case 1:
				return 1;
			case 2:
				return 8;
			case 3:
				return 15;
			case 4:
				return 22;
			case 5:
				return 30;
			case 6:
				return 38;
			case 7:
				return 46;
			case 8:
				return 54;
			case 9:
				return 62;
			case 10:
				return 70;
			default:
				return -1;
		}
	}

	private static int[] getWoolRobeDefenseAllocation(final Item item) {
		final int[] allocation = {0, 0, 0};
		if (item == null) {
			return allocation;
		}
		final int itemId = item.getCatalogId();
		final int baselineMagicDefense = getWoolRobeBaselineMagicDefense(itemId);
		if (baselineMagicDefense <= 0) {
			return allocation;
		}

		allocation[2] = isEnchantedWoolRobePiece(itemId)
			? getWoolRobeBudget(itemId, getWoolRobeTier(item))
			: baselineMagicDefense;
		return allocation;
	}

	private static int getWoolRobeBaselineMagicDefense(final int itemId) {
		if (isWoolRobeHat(itemId)) {
			return 1;
		}
		if (isWoolRobeTop(itemId)) {
			return 4;
		}
		if (isWoolRobeSkirt(itemId)) {
			return 3;
		}
		if (isWoolRobeGloves(itemId) || isWoolRobeBoots(itemId)) {
			return 2;
		}
		return 0;
	}

	private static int getWoolRobeResourceCost(final int itemId) {
		if (isWoolRobeHat(itemId)) {
			return 1;
		}
		if (isWoolRobeTop(itemId)) {
			return 4;
		}
		if (isWoolRobeSkirt(itemId)) {
			return 3;
		}
		if (isWoolRobeGloves(itemId) || isWoolRobeBoots(itemId)) {
			return 2;
		}
		return 0;
	}

	private static int getWoolRobeBudget(final int itemId, final int tier) {
		final int baselineMagicDefense = getWoolRobeBaselineMagicDefense(itemId);
		final int resourceCost = getWoolRobeResourceCost(itemId);
		if (baselineMagicDefense <= 0 || resourceCost <= 0) {
			return 0;
		}
		final int scaledBudget = (int) Math.ceil(tier * resourceCost * 0.6D);
		return Math.max(baselineMagicDefense, scaledBudget);
	}

	private static int getTierForWoolRobeItem(final int itemId) {
		return getTierForProductMatrix(itemId, WOOL_HAT_PRODUCTS, WOOL_TOP_PRODUCTS, WOOL_SKIRT_PRODUCTS, WOOL_GLOVE_PRODUCTS, WOOL_BOOT_PRODUCTS);
	}

	private static void distributeWaterRobeBudget(final int[] allocation, final int extraBudget) {
		final int split = extraBudget / 3;
		allocation[0] += split;
		allocation[1] += split;
		allocation[2] += split;
		final int remaining = extraBudget - (split * 3);
		if (remaining >= 1) {
			allocation[2] += 1;
		}
		if (remaining >= 2) {
			allocation[1] += 1;
		}
	}

	public static double getSpeedBonus(final int itemId) {
		return getTieredEffectValue(itemId, AIR_AMULET_SPEED_EFFECT);
	}

	public static double getHighRollBias(final int itemId) {
		if (itemId == ItemId.AMULET_OF_ACCURACY.id()) {
			// Special quest reward: stronger than tier 1 water, weaker than tier 2.
			return 0.03D;
		}
		return getTieredEffectValue(itemId, WATER_AMULET_HIGH_ROLL_EFFECT);
	}

	public static double getDefenseBonus(final int itemId) {
		return getElementalDefenseBonus(itemId, PrayerCatalog.CombatStyle.MELEE);
	}

	public static double getOffenseBonus(final int itemId) {
		return getElementalPowerBonus(itemId, PrayerCatalog.CombatStyle.MAGIC);
	}

	public static double getChaosAmuletSecondHitChance(final int itemId) {
		return getTieredEffectValue(itemId, CHAOS_AMULET_SECOND_HIT_EFFECT);
	}

	public static double getDeathAmuletDamagePerKillBonus(final int itemId) {
		return 0.0D;
	}

	public static double getBloodAmuletMaxHitsBonus(final int itemId) {
		return 0.0D;
	}

	public static int getBloodJewelryHitsBonus(final int itemId) {
		int tier = getTierForAltar(itemId, SPECIAL_RING_LINES, BLOOD_ALTAR);
		if (tier == -1) {
			tier = getTierForAltar(itemId, STANDARD_NECKLACE_LINES, BLOOD_ALTAR);
		}
		return tier == -1 ? 0 : tier * 2;
	}

	public static double getBloodAmuletLifestealChance(final int itemId) {
		return getTieredEffectValue(itemId, BLOOD_AMULET_MAX_HITS_EFFECT);
	}

	public static int getDeathRingLowHealthPowerBonus(final int itemId, final int currentHits, final int maxHits) {
		final int tier = getTierForAltar(itemId, SPECIAL_RING_LINES, DEATH_ALTAR);
		return getDeathLowHealthBonus(tier, currentHits, maxHits);
	}

	public static int getDeathNecklaceLowHealthDefenseBonus(final int itemId, final int currentHits, final int maxHits) {
		final int tier = getTierForAltar(itemId, STANDARD_NECKLACE_LINES, DEATH_ALTAR);
		return getDeathLowHealthBonus(tier, currentHits, maxHits);
	}

	public static int getDeathAmuletBurstRadius(final int itemId) {
		final int tier = getTierForAltar(itemId, SPECIAL_AMULET_LINES, DEATH_ALTAR);
		if (tier <= 0) {
			return 0;
		}
		return tier >= 5 ? 3 : tier >= 3 ? 2 : 1;
	}

	public static double getDeathAmuletBurstPercent(final int itemId) {
		final int tier = getTierForAltar(itemId, SPECIAL_AMULET_LINES, DEATH_ALTAR);
		if (tier <= 0) {
			return 0.0D;
		}
		if (tier >= 4) {
			return 0.15D;
		}
		if (tier >= 2) {
			return 0.10D;
		}
		return 0.05D;
	}

	public static double getMindAmuletXpBonus(final int itemId) {
		final int necklaceTier = getTierForAltar(itemId, STANDARD_NECKLACE_LINES, MIND_ALTAR);
		if (necklaceTier != -1) {
			return necklaceTier * 0.05D;
		}
		final int ringTier = getTierForAltar(itemId, SPECIAL_RING_LINES, MIND_ALTAR);
		return ringTier == -1 ? 0.0D : ringTier * 0.05D;
	}

	public static double getBodyAmuletXpBonus(final int itemId) {
		final int necklaceTier = getTierForAltar(itemId, STANDARD_NECKLACE_LINES, BODY_ALTAR);
		if (necklaceTier != -1) {
			return necklaceTier * 0.05D;
		}
		final int ringTier = getTierForAltar(itemId, SPECIAL_RING_LINES, BODY_ALTAR);
		return ringTier == -1 ? 0.0D : ringTier * 0.05D;
	}

	public static double getMindAmuletPotionDurationBonus(final int itemId) {
		return 0.0D;
	}

	public static double getBodyAmuletRegenSpeedBonus(final int itemId) {
		return 0.0D;
	}

	public static double getMindCombatAmuletXpBonus(final int itemId) {
		final int tier = getTierForAltar(itemId, SPECIAL_AMULET_LINES, MIND_ALTAR);
		return tier == -1 ? 0.0D : tier * 0.05D;
	}

	public static double getBodyDisciplineAmuletXpBonus(final int itemId) {
		final int tier = getTierForAltar(itemId, SPECIAL_AMULET_LINES, BODY_ALTAR);
		return tier == -1 ? 0.0D : tier * 0.05D;
	}

	public static double getNatureAmuletFoodBonus(final int itemId) {
		final double amuletBonus = getTieredEffectValue(itemId, NATURE_AMULET_FOOD_EFFECT);
		if (amuletBonus > 0.0D) {
			return amuletBonus;
		}
		final int necklaceTier = getTierForAltar(itemId, STANDARD_NECKLACE_LINES, NATURE_ALTAR);
		if (necklaceTier != -1) {
			return necklaceTier * 0.10D;
		}
		final int ringTier = getTierForAltar(itemId, SPECIAL_RING_LINES, NATURE_ALTAR);
		return ringTier == -1 ? 0.0D : ringTier * 0.10D;
	}

	public static int getNatureAmuletPoisonDecayBonus(final int itemId) {
		final int tier = getTierForAltar(itemId, SPECIAL_AMULET_LINES, NATURE_ALTAR);
		return tier == -1 ? 0 : tier;
	}

	public static double getCosmicAmuletExtraResourceChance(final int itemId) {
		return 0.0D;
	}

	public static double getCosmicAmuletRareGatheringDoubleChance(final int itemId) {
		final int tier = getTierForAltar(itemId, SPECIAL_AMULET_LINES, COSMIC_ALTAR);
		return tier == -1 ? 0.0D : tier * 0.10D;
	}

	public static double getCosmicAmuletGemChanceMultiplier(final int itemId) {
		return 1.0D;
	}

	public static double getCosmicAmuletHerbQualityChance(final int itemId) {
		return 0.0D;
	}

	public static int getSoulAmuletExtraKeptItems(final int itemId) {
		int tier = getTierForAltar(itemId, SPECIAL_RING_LINES, SOUL_ALTAR);
		if (tier == -1) {
			tier = getTierForAltar(itemId, new TieredLine[] {new TieredLine(SOUL_ALTAR, SOUL_NECKLACES)}, SOUL_ALTAR);
		}
		return tier == -1 ? 0 : tier;
	}

	public static double getSoulAmuletSurvivalChance(final int itemId) {
		final int tier = getTierForAltar(itemId, SPECIAL_AMULET_LINES, SOUL_ALTAR);
		return tier == -1 ? 0.0D : tier * 0.10D;
	}

	public static int getLifeNecklaceSummonHealthPercent(final int itemId) {
		final int tier = getTierForAltar(itemId, new TieredLine[] {new TieredLine(LIFE_ALTAR, LIFE_NECKLACES)}, LIFE_ALTAR);
		return tier == -1 ? 0 : tier * 10;
	}

	public static int getLifeRingSupportDurationPercent(final int itemId) {
		final int tier = getTierForAltar(itemId, SPECIAL_RING_LINES, LIFE_ALTAR);
		return tier == -1 ? 0 : tier * 20;
	}

	public static int getLifeAmuletSummonMaxDamageBonus(final int itemId) {
		final int tier = getTierForAltar(itemId, SPECIAL_AMULET_LINES, LIFE_ALTAR);
		return tier == -1 ? 0 : tier;
	}

	public static boolean isCraftingSkill(final int skillId) {
		switch (skillId) {
			case 6:  // cooking
			case 11: // firemaking
			case 12: // crafting
			case 13: // smithing
			case 15: // herblaw
			case 17: // fletching
			case 20: // runecraft/enchanting
			case 22: // tailoring
			case 23: // carpentry
				return true;
			default:
				return false;
		}
	}

	public static boolean isGatheringSkill(final int skillId) {
		switch (skillId) {
			case 8:  // woodcutting
			case 10: // fishing
			case 14: // mining
			case 16: // agility
			case 18: // thieving
			case 21: // harvesting
				return true;
			default:
				return false;
		}
	}

	public static boolean isMindCombatXpSkill(final int skillId) {
		return skillId == Skill.MELEE.id()
			|| skillId == Skill.ATTACK.id()
			|| skillId == Skill.DEFENSE.id()
			|| skillId == Skill.STRENGTH.id()
			|| skillId == Skill.RANGED.id()
			|| skillId == Skill.MAGIC.id()
			|| skillId == Skill.GOODMAGIC.id()
			|| skillId == Skill.EVILMAGIC.id()
			|| skillId == Skill.SUMMONING.id();
	}

	public static boolean isBodyDisciplineXpSkill(final int skillId) {
		return skillId == Skill.HITS.id()
			|| skillId == Skill.AGILITY.id()
			|| skillId == Skill.PRAYER.id()
			|| skillId == Skill.PRAYGOOD.id()
			|| skillId == Skill.PRAYEVIL.id()
			|| skillId == Skill.THIEVING.id();
	}

	private static int getTierIndexForBaseAmulet(final int baseAmuletId) {
		for (int i = 0; i < BASE_AMULETS.length; i++) {
			if (BASE_AMULETS[i] == baseAmuletId) {
				return i;
			}
		}
		return -1;
	}

	private static int getTierIndexForBaseNecklace(final int baseNecklaceId) {
		for (int i = 0; i < BASE_NECKLACES.length; i++) {
			if (BASE_NECKLACES[i] == baseNecklaceId) {
				return i;
			}
		}
		return -1;
	}

	private static int getTierIndexForBaseRing(final int baseRingId) {
		for (int i = 0; i < BASE_RINGS.length; i++) {
			if (BASE_RINGS[i] == baseRingId) {
				return i;
			}
		}
		return -1;
	}

	private static int getTierIndexForBaseStaff(final int baseStaffId) {
		for (int i = 0; i < BASE_STAFFS.length; i++) {
			if (BASE_STAFFS[i] == baseStaffId) {
				return i;
			}
		}
		return -1;
	}

	private static int[] getAmuletProductLine(final int altarId) {
		final TieredLine line = getTieredLineByAltar(altarId, SPECIAL_AMULET_LINES);
		return line != null ? line.itemIds : getTieredLineItemsByAltar(altarId, ELEMENTAL_AMULET_LINES);
	}

	private static int[] getRingProductLine(final int altarId) {
		final TieredLine line = getTieredLineByAltar(altarId, SPECIAL_RING_LINES);
		return line != null ? line.itemIds : getTieredLineItemsByAltar(altarId, ELEMENTAL_RING_LINES);
	}

	private static int[] getNecklaceProductLine(final int altarId) {
		if (altarId == SOUL_ALTAR) {
			return SOUL_NECKLACES;
		}
		if (altarId == LIFE_ALTAR) {
			return LIFE_NECKLACES;
		}
		return getTieredLineItemsByAltar(altarId, STANDARD_NECKLACE_LINES);
	}

	private static int getTieredProduct(final int[] productLine, final int tierIndex) {
		if (productLine == null || tierIndex < 0 || tierIndex >= productLine.length) {
			return -1;
		}
		return productLine[tierIndex];
	}

	private static TieredLine getTieredLineByAltar(final int altarId, final TieredLine[] lines) {
		for (TieredLine line : lines) {
			if (line.altarId == altarId) {
				return line;
			}
		}
		return null;
	}

	private static TieredLine getTieredLineForItem(final int itemId, final TieredLine[] lines) {
		for (TieredLine line : lines) {
			if (contains(line.itemIds, itemId)) {
				return line;
			}
		}
		return null;
	}

	private static int[] getTieredLineItemsByAltar(final int altarId, final TieredLine[] lines) {
		final TieredLine line = getTieredLineByAltar(altarId, lines);
		return line == null ? null : line.itemIds;
	}

	private static int getTierForAltar(final int itemId, final TieredLine[] lines, final int altarId) {
		final int[] itemIds = getTieredLineItemsByAltar(altarId, lines);
		return itemIds == null ? -1 : getTier(itemId, itemIds);
	}

	private static int getElementalJewelryPowerBonus(final int itemId, final TieredLine[] lines, final PrayerCatalog.CombatStyle combatStyle) {
		final int waterTier = getTierForAltar(itemId, lines, WATER_ALTAR);
		if (waterTier != -1) {
			return waterTier * 2;
		}
		int tier;
		switch (combatStyle) {
			case RANGED:
				tier = getTierForAltar(itemId, lines, AIR_ALTAR);
				return tier == -1 ? 0 : tier * 3;
			case MELEE:
				tier = getTierForAltar(itemId, lines, EARTH_ALTAR);
				return tier == -1 ? 0 : tier * 3;
			case MAGIC:
				tier = getTierForAltar(itemId, lines, FIRE_ALTAR);
				return tier == -1 ? 0 : tier * 3;
			default:
				return 0;
		}
	}

	private static int getDeathLowHealthBonus(final int tier, final int currentHits, final int maxHits) {
		if (tier <= 0 || maxHits <= 0 || currentHits >= maxHits) {
			return 0;
		}
		final double missingPercent = Math.max(0.0D, (maxHits - currentHits) / (double) maxHits);
		final double step;
		final int bonusPerStep;
		switch (tier) {
			case 1:
				step = 0.25D;
				bonusPerStep = 1;
				break;
			case 2:
				step = 0.20D;
				bonusPerStep = 1;
				break;
			case 3:
				step = 0.15D;
				bonusPerStep = 1;
				break;
			case 4:
				step = 0.10D;
				bonusPerStep = 1;
				break;
			default:
				step = 0.10D;
				bonusPerStep = 2;
				break;
		}
		return (int) Math.floor(missingPercent / step) * bonusPerStep;
	}

	private static int getTierIndexForItem(final int itemId, final int[] line) {
		for (int tierIndex = 0; tierIndex < line.length; tierIndex++) {
			if (line[tierIndex] == itemId) {
				return tierIndex;
			}
		}
		return -1;
	}

	private static int getTier(final int itemId, final int[] line) {
		final int tierIndex = getTierIndexForItem(itemId, line);
		return tierIndex == -1 ? -1 : tierIndex + 1;
	}

	private static double getTierScaledValue(final int itemId, final int[] line, final double step) {
		final int tier = getTier(itemId, line);
		return tier == -1 ? 0.0D : tier * step;
	}

	private static double getTierScaledValue(final int itemId, final TieredLine[] lines, final double step) {
		final TieredLine line = getTieredLineForItem(itemId, lines);
		if (line == null) {
			return 0.0D;
		}
		return getTierScaledValue(itemId, line.itemIds, step);
	}

	private static double getTierScaledValueByAltar(final int itemId, final TieredLine[] lines, final int altarId, final double step) {
		final int tier = getTierForAltar(itemId, lines, altarId);
		return tier == -1 ? 0.0D : tier * step;
	}

	private static double getTieredEffectValue(final int itemId, final TieredEffect effect) {
		return getTierScaledValueByAltar(itemId, effect.lines, effect.altarId, effect.step);
	}

	private static int getTierForProductMatrix(final int itemId, final int[][]... productMatrixGroups) {
		for (int[][] group : productMatrixGroups) {
			for (int[] line : group) {
				final int tier = getTier(itemId, line);
				if (tier != -1) {
					return tier;
				}
			}
		}
		return -1;
	}

	private static boolean containsInProductMatrix(final int[][] productMatrix, final int itemId) {
		for (int[] line : productMatrix) {
			if (contains(line, itemId)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsInLines(final TieredLine[] lines, final int itemId) {
		return getTieredLineForItem(itemId, lines) != null;
	}

	public static boolean isStaffForRune(final int itemId, final int runeId) {
		final TieredLine line = getTieredLineForItem(itemId, STANDARD_STAFF_LINES);
		return line != null && getRuneForAltar(line.altarId) == runeId;
	}

	private static int getAltarIndex(final int altarId) {
		for (int i = 0; i < ALL_ALTARS.length; i++) {
			if (ALL_ALTARS[i] == altarId) {
				return i;
			}
		}
		return -1;
	}

	private static boolean contains(final int[] values, final int itemId) {
		for (int value : values) {
			if (value == itemId) {
				return true;
			}
		}
		return false;
	}
}
