/*
 * Copyright 2019 Frederic Thevenet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fthevenet.binjr.sources.adapters.elitebgs;

import com.google.gson.Gson;
import eu.binjr.core.data.codec.Decoder;
import eu.binjr.core.data.exceptions.DecodingDataFromAdapterException;
import eu.binjr.core.data.timeseries.DoubleTimeSeriesProcessor;
import eu.binjr.core.data.timeseries.TimeSeriesProcessor;
import eu.binjr.core.data.workspace.TimeSeriesInfo;
import eu.fthevenet.binjr.sources.adapters.elitebgs.api.v4.FactionsPage;
import javafx.scene.chart.XYChart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EliteBgsDecoder implements Decoder {
    private static final Logger logger = LogManager.getLogger(EliteBgsDecoder.class);
    private final Gson gson;

    public EliteBgsDecoder() {
        gson = new Gson();
    }

    @Override
    public Map<TimeSeriesInfo, TimeSeriesProcessor> decode(InputStream in, List<TimeSeriesInfo> seriesNames)
            throws IOException, DecodingDataFromAdapterException {
        Map<TimeSeriesInfo, TimeSeriesProcessor> map = new HashMap<>();
        try (InputStreamReader reader = new InputStreamReader(in)) {
            var factionPages = gson.fromJson(reader, FactionsPage.class);
            for (var f : factionPages.docs) {
                for (var info : seriesNames) {
                    var proc = new DoubleTimeSeriesProcessor();
                    Path hierarchy = Paths.get(info.getBinding().getTreeHierarchy());
                    String parentSystem = hierarchy.getFileName().toString();
                    if (parentSystem.equalsIgnoreCase(f.name)) {
                        parentSystem = hierarchy.getParent().getFileName().toString();
                    }
                    if (logger.isTraceEnabled()) {
                        logger.trace("Parent system for faction " + f.name + ": " + parentSystem);
                    }
                    // Add influence values from history
                    for (var h : f.history) {
                        if (parentSystem.equalsIgnoreCase(h.system)) {
                            proc.addSample(new XYChart.Data<>(
                                    ZonedDateTime.parse(h.updated_at),
                                    Double.isNaN(h.influence) ? 0.0 : h.influence * 100));
                        }
                    }
                    // Add current influence value
                    for (var c : f.faction_presence) {
                        if (parentSystem.equalsIgnoreCase(c.system_name)) {
                            proc.addSample(new XYChart.Data<>(
                                    ZonedDateTime.parse(c.updated_at),
                                    Double.isNaN(c.influence) ? 0.0 : c.influence * 100));
                        }
                    }
                    map.put(info, proc);
                }
            }
        }
        return map;
    }
}
