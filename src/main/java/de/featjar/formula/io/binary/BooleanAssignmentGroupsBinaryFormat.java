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
package de.featjar.formula.io.binary;

import de.featjar.base.data.Problem.Severity;
import de.featjar.base.data.Result;
import de.featjar.base.io.binary.ABinaryFormat;
import de.featjar.base.io.format.ParseProblem;
import de.featjar.base.io.input.AInput;
import de.featjar.base.io.input.AInputMapper;
import de.featjar.base.io.output.AOutput;
import de.featjar.base.io.output.AOutputMapper;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.assignment.BooleanAssignmentList;
import de.featjar.formula.assignment.BooleanClause;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.io.IBooleanAssignmentGroupsFormat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Reads / Writes a list of assignments.
 *
 * @author Sebastian Krieter
 */
public class BooleanAssignmentGroupsBinaryFormat extends ABinaryFormat<BooleanAssignmentGroups>
        implements IBooleanAssignmentGroupsFormat {

    private static final byte BooleanSolutionType = 0b0000_0001;
    private static final byte BooleanClauseType = 0b0000_0010;
    private static final byte BooleanAssignmentType = 0b0000_0100;

    @Override
    public void write(BooleanAssignmentGroups assignmentSpace, AOutputMapper outputMapper) throws IOException {
        final VariableMap variableMap = assignmentSpace.getVariableMap();
        final int maxIndex = variableMap.maxIndex();

        AOutput out = outputMapper.get();

        out.writeInt(maxIndex);
        for (int i = 1; i <= maxIndex; i++) {
            writeString(out, variableMap.get(i).orElse(""));
        }
        final List<BooleanAssignmentList> groups = assignmentSpace.getGroups();
        out.writeInt(groups.size());
        for (BooleanAssignmentList group : groups) {
            out.writeInt(group.size());
            for (BooleanAssignment assignment : group) {
                final int[] literals = assignment.get();
                if (assignment instanceof BooleanSolution) {
                    out.writeByte(BooleanSolutionType);
                    final BitSet bs = new BitSet(2 * maxIndex);
                    for (int i = 0, bsIndex = 0; i < literals.length; i++) {
                        final int l = literals[i];
                        if (l == 0) {
                            bs.set(bsIndex++, false);
                            bs.set(bsIndex++, variableMap.has(i + 1));
                        } else {
                            bs.set(bsIndex++, true);
                            bs.set(bsIndex++, l > 0);
                        }
                    }
                    writeByteArray(out, bs.toByteArray());
                } else if (assignment instanceof BooleanClause) {
                    out.writeByte(BooleanClauseType);
                    out.writeInt(literals.length);
                    for (int l : literals) {
                        out.writeInt(l);
                    }
                } else if (assignment instanceof BooleanAssignment) {
                    out.writeByte(BooleanAssignmentType);
                    out.writeInt(literals.length);
                    for (int l : literals) {
                        out.writeInt(l);
                    }
                } else {
                    throw new IllegalArgumentException(assignment.getClass().toString());
                }
            }
        }
        out.flush();
    }

    @Override
    public Result<BooleanAssignmentGroups> parse(AInputMapper inputMapper) {
        final AInput in = inputMapper.get();
        try {
            final VariableMap variableMap = new VariableMap();
            final int maxIndex = in.readInt();
            for (int i = 1; i <= maxIndex; i++) {
                final String name = readString(in);
                if (!name.isEmpty()) {
                    variableMap.add(i, name);
                }
            }
            final int numberOfGroups = in.readInt();
            final ArrayList<BooleanAssignmentList> groups = new ArrayList<>(numberOfGroups);
            for (int i = 0; i < numberOfGroups; i++) {
                final int numberOfAssignment = in.readInt();
                final BooleanAssignmentList group = new BooleanAssignmentList(variableMap, numberOfAssignment);
                for (int j = 0; j < numberOfAssignment; j++) {
                    final byte type = in.readByte();
                    final int[] literals;
                    switch (type) {
                        case BooleanSolutionType:
                            {
                                final BitSet bs = BitSet.valueOf(readByteArray(in));
                                literals = new int[maxIndex];
                                int bsIndex = 0;
                                for (int k = 0; k < maxIndex; k++) {
                                    if (bs.get(bsIndex)) {
                                        literals[k] = bs.get(bsIndex + 1) ? (k + 1) : -(k + 1);
                                    } else {
                                        literals[k] = 0;
                                    }
                                    bsIndex += 2;
                                }
                                group.add(new BooleanSolution(literals, false));
                            }
                            break;
                        case BooleanClauseType:
                            {
                                final int numLiterals = in.readInt();
                                literals = new int[numLiterals];
                                for (int k = 0; k < numLiterals; k++) {
                                    literals[k] = in.readInt();
                                }
                                group.add(new BooleanClause(literals, false));
                            }
                            break;
                        case BooleanAssignmentType:
                            {
                                final int numLiterals = in.readInt();
                                literals = new int[numLiterals];
                                for (int k = 0; k < numLiterals; k++) {
                                    literals[k] = in.readInt();
                                }
                                group.add(new BooleanAssignment(literals));
                            }
                            break;
                        default:
                            return Result.empty(new ParseProblem("Unkown type " + type, Severity.ERROR, 0));
                    }
                }
                groups.add(group);
            }
            return Result.of(new BooleanAssignmentGroups(variableMap, groups));
        } catch (final IOException e) {
            return Result.empty(e);
        }
    }

    @Override
    public boolean supportsWrite() {
        return true;
    }

    @Override
    public boolean supportsParse() {
        return true;
    }

    @Override
    public String getName() {
        return "Binary";
    }

    @Override
    public String getFileExtension() {
        return "bin";
    }
}
