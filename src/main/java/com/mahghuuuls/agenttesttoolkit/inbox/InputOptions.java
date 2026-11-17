package com.mahghuuuls.agenttesttoolkit.inbox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The words of one input step, parsed and bounded: a key binding pressed, held or released, the
 * bindings listed, a click by another route, a turn of the view, the camera view, the window's
 * focus.
 *
 * <p>Pure, and kept beside {@link PlayerActionOptions} for the same reason: the command, the
 * inbox's {@code client} verb and the network message's own check read the same words, and the
 * network package may use this package while this package may not use the network one. Steps a
 * client older than 1.9.0 cannot read travel in their own message, so this parser decides only
 * what is well formed, never who may receive it.
 */
public final class InputOptions {

    /** Press, hold or release one key binding by its registered name. */
    public static final String KEY = "key";
    /** List the registered key bindings, optionally only those whose name starts with a prefix. */
    public static final String KEYS = "keys";
    /** A right click; here only with a route other than the binding or with {@code keepHold}. */
    public static final String USE = PlayerActionOptions.USE;
    /** A left click; here only with a route other than the binding or with {@code keepHold}. */
    public static final String ATTACK = PlayerActionOptions.ATTACK;
    /** A use or attack held; here only with {@code keepHold}, which a client older than 1.9.0 does not read. */
    public static final String HOLD_INPUT = PlayerActionOptions.HOLD;
    /** Turn the view to an absolute yaw and pitch. */
    public static final String LOOK = "look";
    /** Turn the view to an entity or a block. */
    public static final String FACE = "face";
    /** Set the camera view as F5 does. */
    public static final String VIEW = "view";
    /** Take the effects of the window losing or regaining focus, without touching the window. */
    public static final String FOCUS = "focus";

    /** The steps, in the order the usage text lists them. */
    public static final List<String> STEPS = Collections.unmodifiableList(
            Arrays.asList(KEY, KEYS, USE, ATTACK, HOLD_INPUT, LOOK, FACE, VIEW, FOCUS));

    /** What {@code key} does with its binding. */
    public static final String PRESS = "press";
    public static final String HOLD = "hold";
    public static final String RELEASE = "release";

    /** How a click reaches the game: the binding (as before 1.9.0), plus Forge's input event, or the input queue. */
    public static final String ROUTE_BINDING = "binding";
    public static final String ROUTE_EVENT = "event";
    public static final String ROUTE_QUEUE = "queue";
    public static final List<String> ROUTES = Collections.unmodifiableList(
            Arrays.asList(ROUTE_BINDING, ROUTE_EVENT, ROUTE_QUEUE));

    /** The registered names of the use and attack bindings, whose presses take a route. */
    public static final List<String> CLICK_BINDINGS = Collections.unmodifiableList(
            Arrays.asList("key.use", "key.attack"));

    public static final String OPTION_ROUTE = "route";
    public static final String OPTION_KEEP_HOLD = "keepHold";
    public static final String OPTION_RAW = "raw";
    public static final String OPTION_OVER = "over";

    /** The camera views, in F5's order. */
    public static final List<String> VIEWS = Collections.unmodifiableList(Arrays.asList("first", "back", "front"));
    public static final String FOCUS_LOST = "lost";
    public static final String FOCUS_GAINED = "gained";

    /** The word after {@code face} that names a block by its position. */
    public static final String BLOCK = "block";
    /** A block's faces, by the game's own names. */
    public static final List<String> SIDES = Collections.unmodifiableList(
            Arrays.asList("down", "up", "north", "south", "west", "east"));

    /** The longest turn, in ticks: ten seconds. */
    public static final int MAX_TURN_TICKS = 200;
    /** Yaw is taken from -360 to 360, so a turn may be written either way round. */
    public static final double MAX_YAW = 360.0;
    /** Pitch, straight down to straight up. */
    public static final double MAX_PITCH = 90.0;
    /** The world's horizontal edge. */
    public static final int MAX_BLOCK_XZ = 30000000;
    /** The world's height. */
    public static final int MAX_BLOCK_Y = 255;

    /**
     * A key binding's registered name, such as {@code key.jump} or a mod's own; no spaces and no
     * {@code =}, so it cannot be read as an option.
     */
    private static final Pattern BINDING = Pattern.compile("[A-Za-z0-9_.:\\-]{1,128}");
    /** A target selector with its arguments; the server resolves it, so only its shape is checked. */
    private static final Pattern SELECTOR = Pattern.compile("@[aeprs](?:\\[[A-Za-z0-9_=,.!:~+\\-]{0,120}\\])?");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    /** An angle in degrees, at most three decimals. */
    private static final Pattern ANGLE = Pattern.compile("-?(?:0|[1-9][0-9]{0,2})(?:\\.[0-9]{1,3})?");
    private static final Pattern COORDINATE = Pattern.compile("-?(?:0|[1-9][0-9]{0,7})");
    /** 0 to 200, without leading zeros. */
    private static final Pattern TURN_TICKS = Pattern.compile("(?:0|[1-9][0-9]?|1[0-9]{2}|200)");
    /** 1 to 1200, without leading zeros. */
    private static final Pattern HOLD_TICKS = Pattern.compile("(?:[1-9][0-9]{0,2}|1[01][0-9]{2}|1200)");

    private final String step;
    /** The binding for {@code key}, the prefix for {@code keys}, the selector or UUID for {@code face}; empty otherwise. */
    private String name = "";
    /**
     * For {@code key}: press, hold or release. For {@code hold}: use or attack. For {@code view}: the
     * view. For {@code focus}: lost or gained.
     */
    private String mode = "";
    private int ticks;
    private boolean raw;
    private boolean keepHold;
    private String route = ROUTE_BINDING;
    private double yaw;
    private double pitch;
    private int over;
    private boolean faceBlock;
    private int blockX;
    private int blockY;
    private int blockZ;
    /** The face of the block to aim at; empty for its centre. */
    private String side = "";
    private final StepExtras extras = new StepExtras();

    private InputOptions(String step) {
        this.step = step;
    }

    /** True for an input step, written in lower case. */
    public static boolean isStep(String word) {
        return word != null && STEPS.contains(word);
    }

    /**
     * Parses the words after {@code client}: the step first, then its own words, then options
     * written {@code key=value}. Null when they do not form one step.
     */
    public static InputOptions parse(String[] words) {
        if (words == null || words.length == 0 || !isStep(words[0])) {
            return null;
        }
        InputOptions options = new InputOptions(words[0]);
        int next = options.readPositional(words);
        if (next < 0) {
            return null;
        }
        Set<String> seen = new HashSet<String>();
        for (int i = next; i < words.length; i++) {
            int eq = words[i].indexOf('=');
            if (eq <= 0) {
                return null;
            }
            String key = words[i].substring(0, eq);
            if (!seen.add(key) || !options.readOption(key, words[i].substring(eq + 1))) {
                return null;
            }
        }
        return options.extras.isValid() ? options : null;
    }

    /** Reads the step's own words; the index of the first option word, or -1 when they are wrong. */
    private int readPositional(String[] words) {
        if (KEY.equals(step)) {
            if (words.length < 3 || !BINDING.matcher(words[1]).matches()) {
                return -1;
            }
            name = words[1];
            mode = words[2];
            if (PRESS.equals(mode) || RELEASE.equals(mode)) {
                return 3;
            }
            if (HOLD.equals(mode) && words.length >= 4 && HOLD_TICKS.matcher(words[3]).matches()) {
                ticks = Integer.parseInt(words[3]);
                return 4;
            }
            return -1;
        }
        if (KEYS.equals(step)) {
            if (words.length == 1) {
                return 1;
            }
            if (!BINDING.matcher(words[1]).matches()) {
                return -1;
            }
            name = words[1];
            return 2;
        }
        if (USE.equals(step) || ATTACK.equals(step)) {
            return 1;
        }
        if (HOLD_INPUT.equals(step)) {
            if (words.length < 3 || !(USE.equals(words[1]) || ATTACK.equals(words[1]))
                    || !HOLD_TICKS.matcher(words[2]).matches()) {
                return -1;
            }
            mode = words[1];
            ticks = Integer.parseInt(words[2]);
            return 3;
        }
        if (LOOK.equals(step)) {
            if (words.length < 3 || !ANGLE.matcher(words[1]).matches() || !ANGLE.matcher(words[2]).matches()) {
                return -1;
            }
            yaw = Double.parseDouble(words[1]);
            pitch = Double.parseDouble(words[2]);
            return Math.abs(yaw) <= MAX_YAW && Math.abs(pitch) <= MAX_PITCH ? 3 : -1;
        }
        if (FACE.equals(step)) {
            if (words.length < 2) {
                return -1;
            }
            if (BLOCK.equals(words[1])) {
                return readBlock(words);
            }
            if (!SELECTOR.matcher(words[1]).matches() && !UUID_TEXT.matcher(words[1]).matches()) {
                return -1;
            }
            name = words[1];
            return 2;
        }
        if (words.length < 2) {
            return -1;
        }
        mode = words[1];
        if (VIEW.equals(step)) {
            return VIEWS.contains(mode) ? 2 : -1;
        }
        // focus
        return FOCUS_LOST.equals(mode) || FOCUS_GAINED.equals(mode) ? 2 : -1;
    }

    private int readBlock(String[] words) {
        if (words.length < 5) {
            return -1;
        }
        for (int i = 2; i <= 4; i++) {
            if (!COORDINATE.matcher(words[i]).matches()) {
                return -1;
            }
        }
        faceBlock = true;
        blockX = Integer.parseInt(words[2]);
        blockY = Integer.parseInt(words[3]);
        blockZ = Integer.parseInt(words[4]);
        if (Math.abs(blockX) > MAX_BLOCK_XZ || Math.abs(blockZ) > MAX_BLOCK_XZ || blockY < 0 || blockY > MAX_BLOCK_Y) {
            return -1;
        }
        if (words.length > 5 && SIDES.contains(words[5])) {
            side = words[5];
            return 6;
        }
        return 5;
    }

    /** One {@code key=value} option; false when this step does not take it or the value is wrong. */
    private boolean readOption(String key, String value) {
        if (KEYS.equals(step)) {
            // A listing acts on nothing, so it takes no wait and no picture.
            return false;
        }
        if (OPTION_RAW.equals(key)) {
            return KEY.equals(step) && (PRESS.equals(mode) || HOLD.equals(mode)) && readFlag(value, true);
        }
        if (OPTION_KEEP_HOLD.equals(key)) {
            return (USE.equals(step) || ATTACK.equals(step) || HOLD_INPUT.equals(step)
                    || (KEY.equals(step) && (PRESS.equals(mode) || HOLD.equals(mode))))
                    && readFlag(value, false);
        }
        if (OPTION_ROUTE.equals(key)) {
            if (!(USE.equals(step) || ATTACK.equals(step) || pressesClickBinding()) || !ROUTES.contains(value)) {
                return false;
            }
            route = value;
            return true;
        }
        if (OPTION_OVER.equals(key)) {
            if (!LOOK.equals(step) || !TURN_TICKS.matcher(value).matches()) {
                return false;
            }
            over = Integer.parseInt(value);
            return true;
        }
        return extras.take(key, value) == StepExtras.Take.TAKEN;
    }

    /**
     * A {@code key} press of the use or attack binding, which takes a click's routes: the other
     * routes are mouse input, which only the bindings behind the mouse buttons answer to.
     */
    private boolean pressesClickBinding() {
        return KEY.equals(step) && PRESS.equals(mode) && CLICK_BINDINGS.contains(name);
    }

    /** {@code true} or {@code false}; sets {@link #raw} or {@link #keepHold}. */
    private boolean readFlag(String value, boolean isRaw) {
        boolean flag;
        if ("true".equals(value)) {
            flag = true;
        } else if ("false".equals(value)) {
            flag = false;
        } else {
            return false;
        }
        if (isRaw) {
            raw = flag;
        } else {
            keepHold = flag;
        }
        return true;
    }

    /**
     * The words that {@link #parse} reads back into the same step: the step, its own words, then
     * its options in a fixed order, leaving out what is the default.
     */
    public List<String> toWords() {
        List<String> words = new ArrayList<String>();
        words.add(step);
        if (KEY.equals(step)) {
            words.add(name);
            words.add(mode);
            if (HOLD.equals(mode)) {
                words.add(String.valueOf(ticks));
            }
        } else if (KEYS.equals(step)) {
            if (!name.isEmpty()) {
                words.add(name);
            }
        } else if (HOLD_INPUT.equals(step)) {
            words.add(mode);
            words.add(String.valueOf(ticks));
        } else if (LOOK.equals(step)) {
            words.add(angleText(yaw));
            words.add(angleText(pitch));
        } else if (FACE.equals(step)) {
            if (faceBlock) {
                words.add(BLOCK);
                words.add(String.valueOf(blockX));
                words.add(String.valueOf(blockY));
                words.add(String.valueOf(blockZ));
                if (!side.isEmpty()) {
                    words.add(side);
                }
            } else {
                words.add(name);
            }
        } else if (VIEW.equals(step) || FOCUS.equals(step)) {
            words.add(mode);
        }
        if (raw) {
            words.add(OPTION_RAW + "=true");
        }
        if (keepHold) {
            words.add(OPTION_KEEP_HOLD + "=true");
        }
        if (!ROUTE_BINDING.equals(route)) {
            words.add(OPTION_ROUTE + "=" + route);
        }
        if (over != 0) {
            words.add(OPTION_OVER + "=" + over);
        }
        extras.addWords(words);
        return words;
    }

    /** An angle as the parser reads it back: at most three decimals, no trailing zeros. */
    static String angleText(double value) {
        BigDecimal rounded = new BigDecimal(value).setScale(3, RoundingMode.HALF_UP);
        if (rounded.signum() == 0) {
            return "0";
        }
        return rounded.stripTrailingZeros().toPlainString();
    }

    /**
     * Whether this step needs the newer message. A {@code use} or {@code attack} with the
     * binding route and no {@code keepHold}, and a {@code hold} without {@code keepHold}, are the
     * player actions every 1.7.0 client already takes, so they keep going there.
     */
    public boolean travelsAsInput() {
        if (HOLD_INPUT.equals(step)) {
            return keepHold;
        }
        return !(USE.equals(step) || ATTACK.equals(step)) || !ROUTE_BINDING.equals(route) || keepHold;
    }

    /** For a click or hold that does not need the newer message: the same step in the player actions' words. */
    public String[] toPlayerActionWords() {
        List<String> words = new ArrayList<String>();
        words.add(step);
        if (HOLD_INPUT.equals(step)) {
            words.add(mode);
            words.add(String.valueOf(ticks));
        }
        extras.addWords(words);
        return words.toArray(new String[0]);
    }

    /**
     * Whether a dead player cannot take this step. Listing the bindings and the camera view
     * change nothing in the world, so they are sent to a dead player too.
     */
    public static boolean needsLivingPlayer(String step) {
        return !(KEYS.equals(step) || VIEW.equals(step));
    }

    public String getStep() {
        return step;
    }

    /** The binding for {@code key}, the prefix for {@code keys}, the selector or UUID for {@code face}; empty otherwise. */
    public String getName() {
        return name;
    }

    /** {@code press}, {@code hold} or {@code release} for {@code key}; {@code use} or {@code attack} for {@code hold}; the view; {@code lost} or {@code gained}; empty otherwise. */
    public String getMode() {
        return mode;
    }

    /** The ticks of a key's hold or of a {@code hold}; 0 otherwise. */
    public int getTicks() {
        return ticks;
    }

    /** The low-level key state is held too, for mods that read the keyboard directly. */
    public boolean isRaw() {
        return raw;
    }

    /** A running hold of another binding is left running. */
    public boolean isKeepHold() {
        return keepHold;
    }

    /** {@code binding}, {@code event} or {@code queue}. */
    public String getRoute() {
        return route;
    }

    public double getYaw() {
        return yaw;
    }

    public double getPitch() {
        return pitch;
    }

    /** Ticks a turn is spread over; 0 turns at once. */
    public int getOver() {
        return over;
    }

    /** For {@code face}: a block by its position, rather than an entity. */
    public boolean isFaceBlock() {
        return faceBlock;
    }

    public int getBlockX() {
        return blockX;
    }

    public int getBlockY() {
        return blockY;
    }

    public int getBlockZ() {
        return blockZ;
    }

    /** The face of the block aimed at; empty for its centre. */
    public String getSide() {
        return side;
    }

    /**
     * For {@code face block}: the point turned to, the block's centre or the centre of the named
     * face, which lies half a block from the centre toward that side.
     */
    public double[] blockAimPoint() {
        double x = blockX + 0.5;
        double y = blockY + 0.5;
        double z = blockZ + 0.5;
        if ("down".equals(side)) {
            y -= 0.5;
        } else if ("up".equals(side)) {
            y += 0.5;
        } else if ("north".equals(side)) {
            z -= 0.5;
        } else if ("south".equals(side)) {
            z += 0.5;
        } else if ("west".equals(side)) {
            x -= 0.5;
        } else if ("east".equals(side)) {
            x += 0.5;
        }
        return new double[] {x, y, z};
    }

    /** Ticks the client waits before it acts; 0 acts at once. */
    public int getDelay() {
        return extras.delay;
    }

    /** The label of the screenshot taken after the step; empty for none. */
    public String getScreenshot() {
        return extras.screenshot;
    }

    public int getScreenshotDelay() {
        return extras.screenshotDelay;
    }

    /** What that screenshot leaves out; empty for nothing. */
    public String getScreenshotHide() {
        return extras.screenshotHide;
    }
}
