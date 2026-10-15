package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.List;

/**
 * Methods that have no references anywhere in the workspace and are not recognizable
 * entry points - the question asked before deleting code.
 * <p>
 * Every candidate is a <em>heuristic</em>, never a proof. Reflection, dependency
 * injection, serialization, non-Java callers (JSP, Spring XML, scripting) and public
 * API meant for consumers outside the workspace can all keep a method that this tool
 * reports as unreferenced. The reference count is exact; "therefore dead" is not.
 * <p>
 * Each candidate carries the same location triple {@link ReferencesResponse.Reference}
 * and {@link CallHierarchyResponse.CallNode} do, so a caller can open it without a
 * follow-up search.
 */
public record DeadCodeResponse(
    Status status,
    String projectName,
    String packageFilter,
    int methodsScanned,
    int totalCandidates,
    boolean truncated,
    List<Candidate> candidates,
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
     * One unreferenced method.
     *
     * @param signature the parameter list as it reads in source, without parentheses -
     *            the same rendering {@code getClassOutline} and {@code getMethodSource}
     *            use, so an overload named here can be fed back to them
     * @param lineNumber 1-based, or -1 when there is no source to resolve it against
     */
    public record Candidate(
        String methodName,
        String declaringType,
        String signature,
        String projectName,
        String filePath,
        int lineNumber
    )
    {
    }

    /** A scan that ran, however few candidates it found. */
    public static DeadCodeResponse of( String projectName, String packageFilter, int methodsScanned,
                                       List<Candidate> candidates, boolean truncated, int totalCandidates,
                                       List<Diagnostic> diagnostics )
    {
        String summary = candidates.isEmpty()
                ? "No unreferenced methods found in " + methodsScanned + " scanned. "
                        + "Note: this is a heuristic - reflection and non-Java callers are not seen."
                : totalCandidates + ( totalCandidates == 1 ? " method has" : " methods have" )
                        + " no workspace references and are not recognized entry points"
                        + ( truncated ? " (showing " + candidates.size() + ")" : "" ) + ". "
                        + "These are candidates only: reflection, dependency injection, serialization and "
                        + "non-Java callers can still use a method reported here.";

        return new DeadCodeResponse( Status.OK, projectName, packageFilter, methodsScanned, totalCandidates,
                truncated, candidates, diagnostics, summary );
    }

    /** Nothing was scanned, and the status says why. */
    public static DeadCodeResponse failed( String projectName, String packageFilter, Status status,
                                           Diagnostic diagnostic )
    {
        return new DeadCodeResponse( status, projectName, packageFilter, 0, 0, false, List.of(),
                List.of( diagnostic ), diagnostic.message() );
    }
}
