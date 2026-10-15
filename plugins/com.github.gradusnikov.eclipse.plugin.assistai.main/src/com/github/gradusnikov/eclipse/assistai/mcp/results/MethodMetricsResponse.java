package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * How connected each method is: how many distinct methods call it (fan-in) and how many
 * distinct methods it calls (fan-out), counted one level deep.
 * <p>
 * The counts come from the same JDT call hierarchy {@code getMethodCallHierarchy} walks,
 * read directly from the compiler model rather than from an index that can go stale. A
 * high fan-in marks a method much of the project depends on; a high fan-out marks one
 * that coordinates many others - both are the places a refactoring has to be careful.
 * <p>
 * Each method carries the same location triple {@link CallHierarchyResponse.CallNode}
 * does, so one can be opened without a follow-up search.
 */
public record MethodMetricsResponse(
    Status status,
    String projectName,
    int minFanOut,
    int minFanIn,
    int methodsScanned,
    int totalMatched,
    boolean truncated,
    List<MethodMetric> methods,
    List<Diagnostic> diagnostics,
    String summaryText
)
{
    public enum Status
    {
        OK,
        /** No open project resolves the name. */
        PROJECT_NOT_FOUND,
        /** The project exists but is closed. */
        PROJECT_CLOSED,
        /** The scan could not run; see diagnostics. */
        FAILED
    }

    /**
     * One method and its two counts.
     *
     * @param signature the parameter list as it reads in source, without parentheses
     * @param lineNumber 1-based, or -1 when there is no source to resolve it against
     * @param fanIn distinct direct callers
     * @param fanOut distinct direct callees
     */
    public record MethodMetric(
        String methodName,
        String declaringType,
        String signature,
        String projectName,
        String filePath,
        int lineNumber,
        int fanIn,
        int fanOut
    )
    {
    }

    /** A scan that ran, however few methods matched the minima. */
    public static MethodMetricsResponse of( String projectName, int minFanOut, int minFanIn, int methodsScanned,
                                            List<MethodMetric> methods, boolean truncated, int totalMatched,
                                            List<Diagnostic> diagnostics )
    {
        String summary = methods.isEmpty()
                ? "No methods in " + methodsScanned + " scanned met the fan-in/fan-out thresholds."
                : totalMatched + ( totalMatched == 1 ? " method" : " methods" ) + " met the thresholds"
                        + ( truncated ? " (showing " + methods.size() + ", highest first)" : "" ) + ".";

        return new MethodMetricsResponse( Status.OK, projectName, minFanOut, minFanIn, methodsScanned,
                totalMatched, truncated, methods, diagnostics, summary );
    }

    /** Nothing was scanned, and the status says why. */
    public static MethodMetricsResponse failed( String projectName, Status status, Diagnostic diagnostic )
    {
        return new MethodMetricsResponse( status, projectName, 0, 0, 0, 0, false, List.of(),
                List.of( diagnostic ), diagnostic.message() );
    }
}
