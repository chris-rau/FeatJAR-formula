/*
 * Copyright (C) 2025 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-formula.
 *
 * formula is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * formula is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with formula. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-formula> for further information.
 */
package de.featjar.formula.io.dimacs;

import de.featjar.base.data.Pair;
import de.featjar.base.io.NonEmptyLineIterator;
import de.featjar.base.io.input.AInputMapper;
import de.featjar.formula.VariableMap;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic parser for DIMACS format.
 *
 * @author Sebastian Krieter
 */
public class DimacsParser {

    private static final Pattern commentPattern = Pattern.compile("\\A" + DimacsSerializer.COMMENT + "\\s*(.*)\\Z");
    private static final Pattern problemPattern = Pattern.compile(
            "\\A\\s*" + DimacsSerializer.PROBLEM + "\\s+" + DimacsSerializer.TYPE + "\\s+(\\d+)\\s+(\\d+)");

    /** Maps indexes to variables. */
    protected final VariableMap indexVariables = new VariableMap();

    /**
     * The amount of variables as declared in the problem definition. May differ
     * from the actual amount of found variables.
     */
    private int variableCount;
    /** The amount of clauses in the problem. */
    private int clauseCount;

    /** The line iterator. */
    protected NonEmptyLineIterator nonEmptyLineIterator;

    /** True to read the variable directory for naming variables. */
    private boolean readVariableDirectory = false;
    /**
     * <p>
     * Sets the reading variable directory flag. If true, the reader will look for a
     * variable directory in the comments. This contains names for the variables
     * which would otherwise just be numbers.
     * </p>
     *
     * <p>
     * Defaults to false.
     * </p>
     *
     * @param readVariableDirectory whether to read the variable directory
     */
    public void setReadingVariableDirectory(boolean readVariableDirectory) {
        this.readVariableDirectory = readVariableDirectory;
    }

    /**
     * Parses the input.
     *
     * @param inputMapper The source to read from.
     * @return a pair containing the variable map and a list of clauses.
     * @throws IOException    if the reader encounters a problem.
     * @throws ParseException if the input does not conform to the DIMACS CNF file format.
     */
    public Pair<VariableMap, List<int[]>> parse(AInputMapper inputMapper) throws ParseException, IOException {
        init(inputMapper);
        List<int[]> clauses = readLines();
        return new Pair<>(indexVariables, clauses);
    }

    protected void init(AInputMapper inputMapper) {
        nonEmptyLineIterator = inputMapper.get().getNonEmptyLineIterator();
        nonEmptyLineIterator.get();
        indexVariables.clear();
    }

    protected List<int[]> readLines() throws ParseException {
        readComments();
        readProblem();
        final List<int[]> clauses = readClauses();

        if (readVariableDirectory) {
            for (int i = 1; i <= variableCount; i++) {
                if (!indexVariables.has(i)) {
                    indexVariables.add(i, getUniqueName(i));
                }
            }
        }

        final int actualVariableCount = indexVariables.size();
        final int actualClauseCount = clauses.size();
        if (variableCount != actualVariableCount) {
            throw new ParseException(
                    String.format("Found %d instead of %d variables", actualVariableCount, variableCount), 1);
        }
        if (clauseCount != actualClauseCount) {
            throw new ParseException(
                    String.format("Found %d instead of %d clauses", actualClauseCount, clauseCount), 1);
        }
        return clauses;
    }

    private String getUniqueName(int i) {
        String indexName = Integer.toString(i);
        String name = indexName;
        int suffix = 2;
        while (indexVariables.has(name)) {
            name = indexName + "_" + suffix;
            suffix++;
        }
        return name;
    }

    protected void readComments() {
        for (String line = nonEmptyLineIterator.currentLine(); line != null; line = nonEmptyLineIterator.get()) {
            final Matcher matcher = commentPattern.matcher(line);
            if (matcher.matches()) {
                readComment(matcher.group(1)); // read comments ...
            } else {
                break; // ... until a non-comment token is found.
            }
        }
    }

    /**
     * Reads the problem definition.
     *
     * @throws ParseException if the input does not conform to the DIMACS CNF file
     *                        format
     */
    private void readProblem() throws ParseException {
        final String line = nonEmptyLineIterator.currentLine();
        if (line == null) {
            throw new ParseException("Invalid problem format", nonEmptyLineIterator.getLineCount());
        }
        final Matcher matcher = problemPattern.matcher(line);
        if (!matcher.find()) {
            throw new ParseException("Invalid problem format", nonEmptyLineIterator.getLineCount());
        }
        final String trail = line.substring(matcher.end());
        if (trail.trim().isEmpty()) {
            nonEmptyLineIterator.get();
        } else {
            nonEmptyLineIterator.setCurrentLine(trail);
        }

        try {
            variableCount = Integer.parseInt(matcher.group(1));
        } catch (final NumberFormatException e) {
            throw new ParseException("Variable count is not an integer", nonEmptyLineIterator.getLineCount());
        }
        if (variableCount < 0) {
            throw new ParseException("Variable count is not positive", nonEmptyLineIterator.getLineCount());
        }

        try {
            clauseCount = Integer.parseInt(matcher.group(2));
        } catch (final NumberFormatException e) {
            throw new ParseException("Clause count is not an integer", nonEmptyLineIterator.getLineCount());
        }
        if (clauseCount < 0) {
            throw new ParseException("Clause count is not positive", nonEmptyLineIterator.getLineCount());
        }
    }

    /**
     * Reads all clauses.
     *
     * @return all clauses; not null
     * @throws ParseException if the input does not conform to the DIMACS CNF file
     *                        format
     */
    private List<int[]> readClauses() throws ParseException {
        final LinkedList<String> literalQueue = new LinkedList<>();
        final List<int[]> clauses = new ArrayList<>(clauseCount);
        int readClausesCount = 0;
        for (String line = nonEmptyLineIterator.currentLine(); line != null; line = nonEmptyLineIterator.get()) {
            final Matcher matcher = commentPattern.matcher(line);
            if (matcher.matches()) {
                readComment(matcher.group(1));
                continue;
            }
            if (problemPattern.matcher(line).matches()) {
                if (!literalQueue.isEmpty()) {
                    clauses.add(parseClause(readClausesCount, literalQueue.size(), literalQueue));
                    readClausesCount++;
                }
                return clauses;
            }
            List<String> literalList = Arrays.asList(line.trim().split("\\s+"));
            literalQueue.addAll(literalList);

            do {
                final int clauseEndIndex = literalList.indexOf("0");
                if (clauseEndIndex < 0) {
                    break;
                }
                final int clauseSize = literalQueue.size() - (literalList.size() - clauseEndIndex);
                if (clauseSize < 0) {
                    throw new ParseException("Invalid clause", nonEmptyLineIterator.getLineCount());
                } else if (clauseSize == 0) {
                    clauses.add(new int[0]);
                } else {
                    clauses.add(parseClause(readClausesCount, clauseSize, literalQueue));
                }
                readClausesCount++;

                if (!DimacsSerializer.CLAUSE_END.equals(literalQueue.removeFirst())) {
                    throw new ParseException("Illegal clause end", nonEmptyLineIterator.getLineCount());
                }
                literalList = literalQueue;
            } while (!literalQueue.isEmpty());
        }
        if (!literalQueue.isEmpty()) {
            clauses.add(parseClause(readClausesCount, literalQueue.size(), literalQueue));
            readClausesCount++;
        }
        return clauses;
    }

    private int[] parseClause(int readClausesCount, int clauseSize, LinkedList<String> literalQueue)
            throws ParseException {
        if (readClausesCount == clauseCount) {
            throw new ParseException(String.format("Found more than %d clauses", clauseCount), 1);
        }
        final int[] literals = new int[clauseSize];
        for (int j = 0; j < literals.length; j++) {
            final String token = literalQueue.removeFirst();
            final int index;
            try {
                index = Integer.parseInt(token);
            } catch (final NumberFormatException e) {
                throw new ParseException("Illegal literal", nonEmptyLineIterator.getLineCount());
            }
            if (index == 0) {
                throw new ParseException("Illegal literal", nonEmptyLineIterator.getLineCount());
            }
            final int key = Math.abs(index);
            if (!indexVariables.has(key)) {
                indexVariables.add(key, getUniqueName(key));
            }
            literals[j] = index;
        }
        return literals;
    }

    /**
     * Called when a comment is read.
     *
     * @param comment content of the comment; not null
     * @return whether the comment was consumed logically
     */
    private boolean readComment(String comment) {
        return readVariableDirectory && readVariableDirectoryEntry(comment);
    }

    /**
     * Reads an entry of the variable directory.
     *
     * @param comment variable directory entry
     * @return true if an entry was found
     */
    private boolean readVariableDirectoryEntry(String comment) {
        final int firstSeparator = comment.indexOf(' ');
        if (firstSeparator <= 0) {
            return false;
        }
        final int index;
        try {
            index = Integer.parseInt(comment.substring(0, firstSeparator));
        } catch (final NumberFormatException e) {
            return false;
        }
        if (comment.length() < (firstSeparator + 2)) {
            return false;
        }
        final String variable = comment.substring(firstSeparator + 1);
        if (!indexVariables.has(index)) {
            indexVariables.add(index, variable);
        }
        return true;
    }
}
