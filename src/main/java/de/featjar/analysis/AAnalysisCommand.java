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
package de.featjar.analysis;

import de.featjar.base.FeatJAR;
import de.featjar.base.cli.ACommand;
import de.featjar.base.cli.Option;
import de.featjar.base.cli.OptionList;
import de.featjar.base.computation.IComputation;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.io.IOMapperOptions;
import de.featjar.base.io.format.IFormat;
import de.featjar.base.io.graphviz.GraphVizComputationTreeFormat;
import de.featjar.base.io.text.GenericTextFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Computes an analysis result for a formula.
 *
 * @param <T> the type of the analysis result
 */
public abstract class AAnalysisCommand<T> extends ACommand {

    public static final Option<Boolean> BROWSE_CACHE_OPTION =
            Option.newFlag("browse-cache").setDescription("Show cache contents in default browser");

    public static final Option<Boolean> NON_PARALLEL = Option.newFlag("non-parallel")
            .setDescription(
                    "Disable parallel computation. (Ignored if timeout option is specified, as computations with timeout are always non-parallel.)");

    public static final Option<Duration> TIMEOUT_OPTION = Option.newOption(
                    "timeout", s -> Duration.ofSeconds(Long.parseLong(s)))
            .setDescription("Timeout in seconds. (Disables parallel computing.)")
            .setValidator(timeout -> !timeout.isNegative())
            .setDefaultValue(Duration.ZERO);

    /**
     * Output option for execution time.
     */
    public static final Option<Path> TIME_OPTION = Option.newOption("write-time-to-file", Option.PathParser)
            .setDescription("Path to file containig the execution time");

    /**
     * ZIP compression option for saving files.
     */
    public static final Option<Boolean> OUTPUT_COMPRESSION_OPTION =
            Option.newFlag("zip-output").setDescription("Stores output as zip file. (Requires to set an output path.)");

    /**
     * ZIP compression option for reading files.
     */
    public static final Option<Boolean> INTPUT_COMPRESSION_OPTION =
            Option.newFlag("zip-input").setDescription("Reads input as zip file.");

    protected IOMapperOptions[] ioInputOptions = new IOMapperOptions[0];
    protected IOMapperOptions[] ioOutputOptions = new IOMapperOptions[0];

    @Override
    public int run(OptionList optionParser) {
        boolean browseCache = optionParser.getResult(BROWSE_CACHE_OPTION).get();
        boolean parallel = !optionParser.getResult(NON_PARALLEL).get();
        Duration timeout = optionParser.getResult(TIMEOUT_OPTION).get();
        Path outputPath = optionParser.getResult(OUTPUT_OPTION).orElse(null);
        Path timePath = optionParser.getResult(TIME_OPTION).orElse(null);
        if (optionParser.getResult(INTPUT_COMPRESSION_OPTION).get()) {
            ioInputOptions = new IOMapperOptions[] {IOMapperOptions.ZIP_COMPRESSION};
        }
        if (optionParser.getResult(OUTPUT_COMPRESSION_OPTION).get()) {
            ioOutputOptions = new IOMapperOptions[] {IOMapperOptions.ZIP_COMPRESSION};
        }

        IComputation<T> computation;
        try {
            computation = newComputation(optionParser);
        } catch (Exception e) {
            FeatJAR.log().error(e);
            FeatJAR.log().plainMessage(OptionList.printHelp(this));
            return FeatJAR.ERROR_COMPUTING_RESULT;
        }
        FeatJAR.log().debug("running computation %s", computation.print());

        final Result<T> result;
        final long timeNeeded;
        if (!timeout.isZero()) {
            final long localTime = System.nanoTime();
            result = computation.computeResult(true, true, timeout);
            timeNeeded = System.nanoTime() - localTime;
        } else if (parallel) {
            final long localTime = System.nanoTime();
            result = computation.computeFutureResult(true, true).get();
            timeNeeded = System.nanoTime() - localTime;
        } else {
            final long localTime = System.nanoTime();
            result = computation.computeResult(true, true);
            timeNeeded = System.nanoTime() - localTime;
        }
        if (timePath == null) {
            FeatJAR.log().info("time needed for computation: " + ((timeNeeded / 1_000_000) / 1000.0) + "s");
        } else {
            try {
                IO.write(String.valueOf(timeNeeded), timePath);
            } catch (IOException e) {
                FeatJAR.log().error(e);
            }
        }

        if (result.isPresent()) {
            IFormat<T> ouputFormat = getOuputFormat(optionParser);
            if (outputPath == null) {
                if (ouputFormat == null || !ouputFormat.isTextual()) {
                    FeatJAR.log().plainMessage(String.valueOf(result.get()));
                } else {
                    ouputFormat
                            .serialize(result.get())
                            .ifEmpty(FeatJAR.log()::problems)
                            .ifPresent(FeatJAR.log()::plainMessage);
                }
            } else {
                if (Files.isDirectory(outputPath)) {
                    FeatJAR.log().error(new IOException(outputPath.toString() + " is a directory"));
                    return FeatJAR.ERROR_WRITING_RESULT;
                } else if (ouputFormat == null) {
                    FeatJAR.log().warning(new IOException(outputPath.toString() + " not output format specified"));
                    try {
                        Files.write(
                                outputPath,
                                String.valueOf(result.get()).getBytes(StandardCharsets.UTF_8),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING);
                    } catch (IOException e) {
                        FeatJAR.log().error(e);
                        return FeatJAR.ERROR_WRITING_RESULT;
                    }
                } else {
                    try {
                        IO.save(result.get(), outputPath, ouputFormat, ioOutputOptions);
                    } catch (IOException e) {
                        FeatJAR.log().error(e);
                        return FeatJAR.ERROR_WRITING_RESULT;
                    }
                }
            }
        } else {
            FeatJAR.log().problems(result.getProblems());
            FeatJAR.log().error("Could not compute result.");
            return FeatJAR.ERROR_TIMEOUT;
        }
        if (browseCache) {
            FeatJAR.cache().browse(new GraphVizComputationTreeFormat());
        }
        return 0;
    }

    protected abstract IComputation<T> newComputation(OptionList optionParser);

    protected IFormat<T> getOuputFormat(OptionList optionParser) {
        return new GenericTextFormat<>();
    }
}
