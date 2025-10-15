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
package de.featjar.formula.io.textual;

import de.featjar.base.data.Result;
import de.featjar.base.io.format.IFormat;
import de.featjar.base.io.input.AInputMapper;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentValueMap;

/**
 * Textual format for serializing and parsing a {@link BooleanAssignmentValueMap}.
 *
 * @author Christopher Rau
 */
public class BooleanAssignmentValueMapFormat implements IFormat<BooleanAssignmentValueMap> {

    @Override
    public boolean supportsParse() {
        return true;
    }

    @Override
    public boolean supportsWrite() {
        return true;
    }

    /**
     * Parses a textual representation of a {@link BooleanAssignmentValueMap}.
     * <BR>
     * Each line corresponds to one entry, with
     * '=' as the value separator. Left of the '=', a {@link BooleanAssignment} is represented as a list of
     * comma-seperated literals. Negative literals have a '-' as prefix, while positive literals either have no prefix or
     * '+'. All further '-' and '+' chars are interpreted as part of the literal name. To the right of the '=', a {@link Integer}
     * provides the value corresponding to the {@link BooleanAssignment} key.
     * <BR>
     * Line example: 'literal1,+literal2,-literal3=value'.
     * @param inputMapper {@link AInputMapper} providing the textual representation.
     * @return The corresponding {@link BooleanAssignmentValueMap} wrapped in a {@link Result}
     */
    @Override
    public Result<BooleanAssignmentValueMap> parse(AInputMapper inputMapper) {
        return new BooleanAssignmentValueMapParser().parse(inputMapper);
    }

    /**
     * Serializes a {@link BooleanAssignmentValueMap} into a {@link String}.
     * <BR>
     * Each line corresponds to one entry, with
     * '=' as the value separator. Left of the '=', a {@link BooleanAssignment} is represented as a list of
     * comma-seperated literals. Negative literals have a '-' as prefix, while positive literals either have no prefix or
     * '+'. All further '-' and '+' chars are interpreted as part of the literal name. To the right of the '=', a {@link Integer}
     * provides the value corresponding to the {@link BooleanAssignment} key.
     * <BR>
     * Line example: 'literal1,+literal2,-literal3=value'.
     * @param booleanAssignmentValueMap {@link BooleanAssignmentValueMap} to be serialized.
     * @return The corresponding {@link String} wrapped in a {@link Result}
     */
    @Override
    public Result<String> serialize(BooleanAssignmentValueMap booleanAssignmentValueMap) {
        return new BooleanAssignmentValueMapParser().serialize(booleanAssignmentValueMap);
    }

    @Override
    public String getName() {
        return "BooleanAssignmentValueMap";
    }
}
