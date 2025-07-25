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
package de.featjar.formula.assignment;

import de.featjar.base.computation.AComputation;
import de.featjar.base.computation.Dependency;
import de.featjar.base.computation.IComputation;
import de.featjar.base.computation.Progress;
import de.featjar.base.data.Maps;
import de.featjar.base.data.Result;
import de.featjar.formula.VariableMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A map assigning {@link Integer} values to {@link BooleanAssignment} keys.
 *
 * @author Christopher Rau
 */
public class BooleanAssignmentValueMap implements Iterable<Map.Entry<BooleanAssignment, Integer>> {

    public ValuedBooleanAssignmentList toValuedBooleanAssignmentList() {
        return booleanAssignmentValues.entrySet().stream()
                .map(entry -> new ValuedBooleanAssignment(entry.getKey(), entry.getValue()))
                .collect(Collectors.toCollection(ValuedBooleanAssignmentList::new));
    }

    public static final class EmptyComputation extends AComputation<BooleanAssignmentValueMap> {

        public static final Dependency<BooleanAssignmentList> ASSIGNMENT_LIST =
                Dependency.newDependency(BooleanAssignmentList.class);

        public EmptyComputation(IComputation<BooleanAssignmentList> booleanAssignmentList) {
            super(booleanAssignmentList);
        }

        @Override
        public Result<BooleanAssignmentValueMap> compute(List<Object> dependencyList, Progress progress) {
            return Result.of(new BooleanAssignmentValueMap(
                    ASSIGNMENT_LIST.get(dependencyList).getVariableMap(), Maps.empty()));
        }
    }

    protected VariableMap variableMap;
    protected final Map<BooleanAssignment, Integer> booleanAssignmentValues;

    public BooleanAssignmentValueMap(VariableMap variableMap, Map<BooleanAssignment, Integer> booleanAssignmentValues) {
        this.variableMap = Objects.requireNonNull(variableMap);
        this.booleanAssignmentValues = new LinkedHashMap<>(Objects.requireNonNull(booleanAssignmentValues));
    }

    public BooleanAssignmentValueMap(VariableMap variableMap) {
        this.variableMap = Objects.requireNonNull(variableMap);
        this.booleanAssignmentValues = new LinkedHashMap<>();
    }

    public VariableMap getVariableMap() {
        return variableMap;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BooleanAssignmentValueMap other = (BooleanAssignmentValueMap) obj;
        return Objects.equals(variableMap, other.variableMap)
                && Objects.equals(booleanAssignmentValues, other.booleanAssignmentValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variableMap, booleanAssignmentValues);
    }

    @Override
    public String toString() {
        return "BooleanAssignmentValueMap [variable-map=" + variableMap + ", assignment-value-map="
                + booleanAssignmentValues.toString() + "]";
    }

    public int size() {
        return booleanAssignmentValues.size();
    }

    public Integer getValue(BooleanAssignment assignment) {
        return booleanAssignmentValues.get(assignment);
    }

    public Integer addValue(BooleanAssignment booleanAssignment, Integer integer) {
        return booleanAssignmentValues.put(booleanAssignment, integer);
    }

    public Integer removeValue(BooleanAssignment assignment) {
        return booleanAssignmentValues.remove(assignment);
    }

    public Set<BooleanAssignment> getAssignments() {
        return booleanAssignmentValues.keySet();
    }

    @Override
    public Iterator<Map.Entry<BooleanAssignment, Integer>> iterator() {
        return booleanAssignmentValues.entrySet().iterator();
    }

    /**
     * Combines all {@link BooleanAssignment} keys of the map to a {@link BooleanAssignmentList}.
     * @return the combined {@link BooleanAssignmentList}
     */
    public BooleanAssignmentList getBooleanAssignmentList() {
        return new BooleanAssignmentList(variableMap, getAssignments());
    }

    /**
     * Changes the {@link VariableMap variable map} and calls {@link BooleanAssignment#adapt(VariableMap, VariableMap, boolean)} for every assignment in this list.
     * This does not create a copy of this list, but directly changes each assignment.
     *
     * @param newVariables the new variable map
     * @param integrateOldVariables whether variable names from the old variable map are added to the new variable map, if missing
     * @return this list
     */
    public BooleanAssignmentValueMap adapt(VariableMap newVariables, boolean integrateOldVariables) {
        booleanAssignmentValues
                .keySet()
                .forEach(assignment -> assignment.adapt(variableMap, newVariables, integrateOldVariables));
        variableMap = newVariables;
        return this;
    }
}
