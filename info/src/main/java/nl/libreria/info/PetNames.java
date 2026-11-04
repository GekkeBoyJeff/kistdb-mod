package nl.libreria.info;

import java.util.Random;

/** Names for pets: Biscuit, Pepper, Sir Waffles… {@link Names} keeps them unique on the server. */
final class PetNames {
	static final String[] NAMES = {"Biscuit", "Pepper", "Maple", "Shadow", "Bramble", "Clover", "Pickles", "Nugget", "Mochi",
			"Waffles", "Pumpkin", "Hazel", "Juniper", "Ziggy", "Pip", "Bean", "Toffee", "Fudge", "Cinnamon", "Ginger", "Nutmeg",
			"Sage", "Basil", "Olive", "Pebble", "Acorn", "Button", "Socks", "Mittens", "Whiskers", "Patches", "Smudge", "Freckles",
			"Dot", "Domino", "Oreo", "Cookie", "Muffin", "Crumpet", "Scone", "Bagel", "Pretzel", "Noodle", "Dumpling", "Tofu",
			"Sushi", "Wasabi", "Miso", "Ramen", "Kiwi", "Mango", "Peaches", "Plum", "Cherry", "Berry", "Fig", "Date", "Truffle",
			"Cocoa", "Mocha", "Latte", "Espresso", "Chai", "Honey", "Sugar", "Caramel", "Butterscotch", "Marshmallow", "Sprout",
			"Twig", "Moss", "Fern", "Willow", "Birch", "Aspen", "Rowan", "Ash", "Ember", "Blaze", "Cinder", "Sooty", "Smokey",
			"Storm", "Thunder", "Misty", "Frost", "Snowball", "Flurry", "Blizzard", "Sunny", "Rusty", "Copper", "Goldie", "Silver",
			"Jasper", "Onyx", "Opal", "Ruby", "Amber", "Jade", "Pearl", "Flint", "Slate", "Pebbles", "Boulder", "Rocky", "Comet",
			"Nova", "Luna", "Stella", "Orion", "Apollo", "Atlas", "Zeus", "Thor", "Loki", "Odin", "Freya", "Athena", "Juno",
			"Hermes", "Bandit", "Ranger", "Scout", "Hunter", "Tracker", "Rover", "Wanderer", "Drifter", "Ace", "Duke", "Duchess",
			"Baron", "Count", "Earl", "Major", "Captain", "Admiral", "Bishop", "Rook", "Knight", "Pawn", "Jester", "Bard", "Merlin",
			"Biscotti", "Cannoli", "Gnocchi", "Ravioli", "Pesto",
			"Paprika", "Saffron", "Clove", "Anise", "Fennel", "Thyme", "Rosemary", "Parsley", "Dill", "Mint", "Tansy", "Poppy",
			"Daisy", "Tulip", "Lily", "Iris", "Violet", "Rosie", "Petunia", "Marigold", "Bluebell", "Heather", "Holly", "Ivy",
			"Bumble", "Buzz", "Beetle", "Cricket", "Moth", "Sparrow", "Robin", "Finch", "Wren", "Pigeon", "Dodo", "Puffin",
			"Otter", "Badger", "Ferret", "Weasel", "Hedgehog", "Mole", "Vole", "Shrew", "Tater", "Spud", "Turnip", "Radish",
			"Parsnip", "Pumpernickel", "Crouton", "Gravy", "Mustard", "Ketchup", "Relish", "Nacho", "Taco", "Churro", "Burrito",
			"Tamale", "Quesadilla", "Pancake", "Crepe", "Brioche", "Croissant", "Eclair", "Macaron", "Meringue", "Sherbet",
			"Sorbet", "Gelato", "Sundae", "Sprinkles", "Jellybean", "Gumdrop", "Lollipop", "Taffy", "Licorice", "Fizz", "Soda",
			"Bubbles", "Pogo", "Bingo", "Bongo", "Banjo", "Fiddle", "Tuba", "Piccolo", "Cello", "Jazz", "Blues", "Echo", "Ripple",
			"Puddle", "Splash", "Drizzle", "Breeze", "Gust", "Zephyr", "Tempest", "Nimbus", "Cirrus", "Dusty", "Scruffy", "Fluffy",
			"Fuzzy", "Snuggles", "Cuddles", "Wiggles", "Giggles", "Doodle", "Scribble", "Sketch", "Inky", "Quill", "Winston", "Watson", "Bertie", "Archie", "Alfie", "Ollie", "Teddy", "Monty", "Rufus",
			"Hector", "Barnaby", "Percy", "Reggie", "Humphrey", "Gus", "Moose", "Bear", "Tank", "Diesel", "Bolt", "Dash", "Zoom",
			"Turbo", "Rocket", "Sprocket", "Gizmo", "Widget", "Gadget", "Pixel", "Byte", "Chip", "Patch", "Tinker", "Cobble"};

	private static final String[] TITLES = {"Sir ", "Lady ", "Little ", "Old ", "Captain ", "Lord ", "Miss ", "Professor ",
			"Duke ", "Big ", "Tiny ", "Doctor "};

	private PetNames() {
	}

	/** First plain names; the more are taken, the more often a title in front (Sir Waffles, Little Pepper). */
	static String name(Random r, int attempt) {
		String base = NAMES[r.nextInt(NAMES.length)];
		return attempt < 30 ? base : TITLES[r.nextInt(TITLES.length)] + base;
	}
}
