package io.elysian.core.model;

/**
 * MTAM state-tier classification.
 * Each tier has an associated encoding strategy used during migration.
 */
public enum StateTier {
    /** Frequently accessed — stored in Count-Min Sketch / Bloom filters. */
    HOT  ("COUNT_MIN_SKETCH"),
    /** Moderately accessed — delta-encoded append-only log. */
    WARM ("DELTA_ENCODING"),
    /** Rarely accessed — LZ4-compressed immutable snapshot. */
    COLD ("LZ4_SNAPSHOT");

    private final String encodingType;

    StateTier(String encodingType) {
        this.encodingType = encodingType;
    }

    public String getEncodingType() { return encodingType; }
}
