package com.gilgamesh;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.ConversationStore;
import com.gilgamesh.nlp.IntentClassifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * GilgameshConfig — Tunable settings, read from gilgamesh.properties.
 *
 * Settings are layered: built-in defaults, then the bundled gilgamesh.properties,
 * then one user file (see {@link #load(Path)}). A value that is missing, not a number,
 * or out of range is reported and replaced by its default, so a typo in the file can
 * never stop the bot from starting.
 */
public class GilgameshConfig {

    private static final Logger LOG = Logger.getLogger(GilgameshConfig.class.getName());

    public static final String FILE_NAME = "gilgamesh.properties";

    public static final String THRESHOLD = "classifier.threshold";
    public static final String CONTEXT_BOOST = "classifier.contextBoost";
    public static final String OVERLAP_WEIGHT = "classifier.overlapWeight";
    public static final String COSINE_WEIGHT = "classifier.cosineWeight";
    public static final String AMBIGUITY_MARGIN = "conversation.ambiguityMargin";
    public static final String HISTORY_SIZE = "memory.historySize";
    public static final String MAX_SESSION_TURNS = "memory.maxSessionTurns";
    public static final String INACTIVITY_MINUTES = "memory.inactivityTimeoutMinutes";
    public static final String SESSION_PATH = "session.path";
    public static final String SESSION_MAX_AGE_HOURS = "session.maxAgeHours";

    /** A numeric setting: its built-in default and the range of values that make sense. */
    private record Setting(double defaultValue, double min, double max) {}

    private static final Map<String, Setting> NUMERIC_SETTINGS = new LinkedHashMap<>();

    static {
        NUMERIC_SETTINGS.put(THRESHOLD, new Setting(IntentClassifier.DEFAULT_CONFIDENCE_THRESHOLD, 0.0, 1.0));
        NUMERIC_SETTINGS.put(CONTEXT_BOOST, new Setting(IntentClassifier.DEFAULT_CONTEXT_BOOST, 1.0, 3.0));
        NUMERIC_SETTINGS.put(OVERLAP_WEIGHT, new Setting(IntentClassifier.DEFAULT_OVERLAP_WEIGHT, 0.0, 1000.0));
        NUMERIC_SETTINGS.put(COSINE_WEIGHT, new Setting(IntentClassifier.DEFAULT_COSINE_WEIGHT, 0.0, 1000.0));
        NUMERIC_SETTINGS.put(AMBIGUITY_MARGIN, new Setting(ConversationManager.DEFAULT_AMBIGUITY_MARGIN, -1.0, 1.0));
        NUMERIC_SETTINGS.put(HISTORY_SIZE, new Setting(ConversationContext.DEFAULT_MAX_HISTORY_SIZE, 1, 1000));
        NUMERIC_SETTINGS.put(MAX_SESSION_TURNS, new Setting(ConversationContext.DEFAULT_MAX_SESSION_TURNS, 0, 1_000_000));
        NUMERIC_SETTINGS.put(INACTIVITY_MINUTES,
                new Setting(ConversationContext.DEFAULT_INACTIVITY_TIMEOUT_MS / 60_000.0, 0, 100_000));
        NUMERIC_SETTINGS.put(SESSION_MAX_AGE_HOURS,
                new Setting(ConversationStore.DEFAULT_MAX_AGE_MS / 3_600_000.0, 0, 100_000));
    }

    private final Properties properties;
    private final String source;

    private GilgameshConfig(Properties properties, String source) {
        this.properties = properties;
        this.source = source;
        for (String key : properties.stringPropertyNames()) {
            if (!NUMERIC_SETTINGS.containsKey(key) && !SESSION_PATH.equals(key)) {
                LOG.warning("Unknown setting '" + key + "' in " + source + " (ignored). Check the spelling.");
            }
        }
    }

    /** The settings the bot ships with. */
    public static GilgameshConfig defaults() {
        return new GilgameshConfig(bundled(), "built-in defaults");
    }

    /** Built-in settings with the given values on top (used by tests). */
    public static GilgameshConfig of(Map<String, String> overrides) {
        Properties properties = bundled();
        properties.putAll(overrides);
        return new GilgameshConfig(properties, "overrides");
    }

    /**
     * Loads the settings, layering one user file over the defaults.
     *
     * @param explicitFile a file named by the user (--config), or null to look for
     *                     ./gilgamesh.properties and then ~/.gilgamesh/gilgamesh.properties
     * @throws IllegalArgumentException if explicitFile is given but cannot be read
     */
    public static GilgameshConfig load(Path explicitFile) {
        Path file = explicitFile;
        if (file == null) {
            Path local = Paths.get(FILE_NAME);
            Path home = Paths.get(System.getProperty("user.home"), ".gilgamesh", FILE_NAME);
            file = Files.isRegularFile(local) ? local : Files.isRegularFile(home) ? home : null;
            if (file == null) {
                return defaults();
            }
        } else if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Config file not found: " + file.toAbsolutePath());
        }

        Properties properties = bundled();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            if (explicitFile != null) {
                throw new IllegalArgumentException("Could not read config file " + file + ": " + e.getMessage(), e);
            }
            LOG.warning("Could not read " + file + " (" + e.getMessage() + "); using default settings.");
            return defaults();
        }
        return new GilgameshConfig(properties, file.toString());
    }

    private static Properties bundled() {
        Properties properties = new Properties();
        try (InputStream stream = GilgameshConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (stream != null) {
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOG.warning("Could not read the bundled " + FILE_NAME + "; using built-in defaults.");
        }
        return properties;
    }

    /** Where these settings came from (a file path, or "built-in defaults"). */
    public String getSource() {
        return source;
    }

    /**
     * Reads a numeric setting, falling back to its default (with a warning) when the
     * value is missing, not a number, or outside its sensible range.
     */
    public double getDouble(String key) {
        Setting setting = NUMERIC_SETTINGS.get(key);
        if (setting == null) {
            throw new IllegalArgumentException("Not a numeric setting: " + key);
        }
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return setting.defaultValue();
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || value < setting.min() || value > setting.max()) {
                LOG.warning(key + "=" + raw.trim() + " in " + source + " is outside " + setting.min() + ".."
                        + setting.max() + "; using " + setting.defaultValue() + ".");
                return setting.defaultValue();
            }
            return value;
        } catch (NumberFormatException e) {
            LOG.warning(key + "=" + raw.trim() + " in " + source + " is not a number; using "
                    + setting.defaultValue() + ".");
            return setting.defaultValue();
        }
    }

    public double threshold() { return getDouble(THRESHOLD); }
    public double contextBoost() { return getDouble(CONTEXT_BOOST); }
    public double overlapWeight() { return getDouble(OVERLAP_WEIGHT); }
    public double cosineWeight() { return getDouble(COSINE_WEIGHT); }
    public double ambiguityMargin() { return getDouble(AMBIGUITY_MARGIN); }
    public int historySize() { return (int) Math.round(getDouble(HISTORY_SIZE)); }
    public int maxSessionTurns() { return (int) Math.round(getDouble(MAX_SESSION_TURNS)); }
    public long inactivityTimeoutMs() { return Math.round(getDouble(INACTIVITY_MINUTES) * 60_000); }
    public long sessionMaxAgeMs() { return Math.round(getDouble(SESSION_MAX_AGE_HOURS) * 3_600_000); }

    /** Where the conversation is saved; the default location when the setting is empty. */
    public Path sessionPath() {
        String raw = properties.getProperty(SESSION_PATH);
        if (raw == null || raw.isBlank()) {
            return ConversationStore.defaultPath();
        }
        String path = raw.trim();
        if (path.equals("~") || path.startsWith("~/") || path.startsWith("~\\")) {
            path = System.getProperty("user.home") + path.substring(1);
        }
        return Paths.get(path);
    }

    /** Applies the classifier settings. */
    public void applyTo(IntentClassifier classifier) {
        classifier.setConfidenceThreshold(threshold());
        classifier.setContextBoost(contextBoost());
        double overlap = overlapWeight();
        double cosine = cosineWeight();
        if (overlap + cosine <= 0) {
            LOG.warning("Both score weights are 0 in " + source + "; using the defaults.");
            overlap = IntentClassifier.DEFAULT_OVERLAP_WEIGHT;
            cosine = IntentClassifier.DEFAULT_COSINE_WEIGHT;
        }
        classifier.setScoreWeights(overlap, cosine);
    }

    /** Creates a conversation context with the memory settings. */
    public ConversationContext newContext() {
        return new ConversationContext(historySize(), maxSessionTurns(), inactivityTimeoutMs());
    }

    /** Creates the session store with the saved-session settings. */
    public ConversationStore newStore() {
        return new ConversationStore(sessionPath(), sessionMaxAgeMs());
    }
}
