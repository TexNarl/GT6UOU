package gregapi.code;

/**
 * Broad periodic-table categories used by the GT6UOU material API.
 *
 * <p>This is intentionally small and gameplay-facing. It is not meant to be a
 * full chemistry ontology; it gives recipes, tooltips, and material rules a
 * stable way to ask "what kind of element is this?".</p>
 */
public enum MTCategory {
    ALKALI_METAL,
    ALKALINE_EARTH_METAL,
    LANTHANIDE,
    ACTINIDE,
    TRANSITION_METAL,
    POST_TRANSITION_METAL,
    METALLOID,
    REACTIVE_NONMETAL,
    NOBLE_GAS,
    UNKNOWN
}
