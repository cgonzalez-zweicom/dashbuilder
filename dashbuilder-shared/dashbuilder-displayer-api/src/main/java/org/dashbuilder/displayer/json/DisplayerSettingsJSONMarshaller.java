/*
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dashbuilder.displayer.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dashbuilder.dataset.DataSet;
import org.dashbuilder.dataset.DataSetLookup;
import org.dashbuilder.dataset.json.DataSetJSONMarshaller;
import org.dashbuilder.dataset.json.DataSetLookupJSONMarshaller;
import org.dashbuilder.displayer.ColumnSettings;
import org.dashbuilder.displayer.DisplayerSettings;
import org.dashbuilder.json.Json;
import org.dashbuilder.json.JsonArray;
import org.dashbuilder.json.JsonObject;
import org.dashbuilder.json.JsonString;
import org.dashbuilder.json.JsonValue;

public class DisplayerSettingsJSONMarshaller {

    private static final String DATASET_PREFIX = "dataSet";
    private static final String DATASET_LOOKUP_PREFIX = "dataSetLookup";
    private static final String COLUMNS_PREFIX = "columns";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_EXPRESSION = "expression";
    private static final String COLUMN_PATTERN = "pattern";
    private static final String COLUMN_EMPTY = "empty";
    private static final String SETTINGS_UUID = "uuid";

    private static DisplayerSettingsJSONMarshaller SINGLETON = new DisplayerSettingsJSONMarshaller();

    private Map<String, String> dictionary = new HashMap<String, String>();

    public static DisplayerSettingsJSONMarshaller get() {
        return SINGLETON;
    }

    private DataSetJSONMarshaller dataSetJsonMarshaller;
    private DataSetLookupJSONMarshaller dataSetLookupJsonMarshaller;

    public DisplayerSettingsJSONMarshaller() {
        this(DataSetJSONMarshaller.get(), DataSetLookupJSONMarshaller.get());

        dictionary.put("uuid",              "0");
        dictionary.put("dataSet",           "1");
        dictionary.put("dataSetLookup",     "2");
        dictionary.put("id",                "3");
        
        // Displayer attributes
        dictionary.put("type",              "4");
        dictionary.put("subtype",           "5");
        dictionary.put("renderer",          "6");
        dictionary.put("expression",        "7");
        dictionary.put("pattern",           "8");
        dictionary.put("empty",             "9");
        dictionary.put("title",             "a");
        dictionary.put("visible",           "b");
        dictionary.put("allow_csv",         "c");
        dictionary.put("allow_excel",       "d");
        dictionary.put("staleData",         "e");
        dictionary.put("interval",          "f");
        dictionary.put("enabled",           "g");
        dictionary.put("selfapply",         "h");
        dictionary.put("notification",      "i");
        dictionary.put("listening",         "j");
        dictionary.put("width",             "k");
        dictionary.put("height",            "l");
        dictionary.put("resizable",         "m");
        dictionary.put("maxWidth",          "n");
        dictionary.put("maxHeight",         "o");
        dictionary.put("bgColor",           "p");
        dictionary.put("3d",                "q");
        dictionary.put("top",               "r");
        dictionary.put("bottom",            "s");
        dictionary.put("left",              "t");
        dictionary.put("right",             "u");
        dictionary.put("show",              "v");
        dictionary.put("position",          "w");
        dictionary.put("pageSize",          "x");
        dictionary.put("columnId",          "y");
        dictionary.put("order",             "z");
        dictionary.put("labels_show",       "A");
        dictionary.put("start",             "B");
        dictionary.put("warning",           "C");
        dictionary.put("critical",          "D");
        dictionary.put("end",               "E");
        
        // DataSetLookup
        dictionary.put("dataSetUuid",       "F");
        dictionary.put("rowCount",          "G");
        dictionary.put("rowOffset",         "H");
        dictionary.put("column",            "I");
        dictionary.put("source",            "J");
        dictionary.put("filterOps",         "K");
        dictionary.put("function",          "L");
        dictionary.put("args",              "M");
        dictionary.put("groupOps",          "N");
        dictionary.put("columnGroup",       "O");
        dictionary.put("groupStrategy",     "P");
        dictionary.put("maxIntervals",      "Q");
        dictionary.put("intervalSize",      "R");
        dictionary.put("emptyIntervals",    "S");
        dictionary.put("asc",               "T");
        dictionary.put("firstMonthOfYear",  "U");
        dictionary.put("firstDayOfWeek",    "V");
        dictionary.put("groupFunctions",    "W");
        dictionary.put("selected",          "X");
        dictionary.put("name",              "Y");
        dictionary.put("index",             "Z");
        dictionary.put("min",               "00");
        dictionary.put("max",               "01");
        dictionary.put("join",              "02");
        dictionary.put("sortOps",           "03");
        dictionary.put("sortOrder",         "04");

        // Displayer attributes groups
        dictionary.put("general",           "05");
        dictionary.put("columns",           "06");
        dictionary.put("refresh",           "07");
        dictionary.put("filter",            "08");
        dictionary.put("chart",             "09");
        dictionary.put("table",             "0a");
        dictionary.put("axis",              "0b");
        dictionary.put("meter",             "0c");
        dictionary.put("margin",            "0d");
        dictionary.put("legend",            "0e");
        dictionary.put("sort",              "0f");
        dictionary.put("x",                 "0g");
        dictionary.put("y",                 "0h");
    }

    public DisplayerSettingsJSONMarshaller(DataSetJSONMarshaller dataSetJsonMarshaller, DataSetLookupJSONMarshaller dataSetLookupJsonMarshaller) {
        this.dataSetJsonMarshaller = dataSetJsonMarshaller;
        this.dataSetLookupJsonMarshaller = dataSetLookupJsonMarshaller;
    }

    public DisplayerSettings fromJsonString( String jsonString ) {
        DisplayerSettings ds = new DisplayerSettings();

        if (!isBlank(jsonString)) {

            JsonObject parseResult = Json.parse(jsonDecode(jsonString));

            if ( parseResult != null ) {

                // UUID
                ds.setUUID(parseResult.getString(SETTINGS_UUID));
                parseResult.put(SETTINGS_UUID, (String) null);

                // First look if a dataset 'on-the-fly' has been specified
                JsonObject data = parseResult.getObject(DATASET_PREFIX);
                if (data != null) {
                    DataSet dataSet = dataSetJsonMarshaller.fromJson(data);
                    ds.setDataSet(dataSet);

                    // Remove from the json input so that it doesn't end up in the settings map.
                    parseResult.put(DATASET_PREFIX, (JsonValue) null);

                // If none was found, look for a dataset lookup definition
                } else if ((data = parseResult.getObject(DATASET_LOOKUP_PREFIX)) != null) {
                    DataSetLookup dataSetLookup = dataSetLookupJsonMarshaller.fromJson(data);
                    ds.setDataSetLookup(dataSetLookup);

                    // Remove from the json input so that it doesn't end up in the settings map.
                    parseResult.put(DATASET_LOOKUP_PREFIX, (JsonValue) null);
                }
                else {
                    throw new RuntimeException("Displayer settings dataset lookup not specified");
                }

                // Parse the columns settings
                JsonArray columns = parseResult.getArray(COLUMNS_PREFIX);
                if (columns != null) {
                    List<ColumnSettings> columnSettingsList = parseColumnsFromJson(columns);
                    ds.setColumnSettingsList(columnSettingsList);

                    // Remove from the json input so that it doesn't end up in the settings map.
                    parseResult.put(COLUMNS_PREFIX, (JsonValue) null);
                }

                // Now parse all other settings
                ds.setSettingsFlatMap( parseSettingsFromJson(parseResult));
            }
        }
        return ds;
    }

    public String toJsonString(DisplayerSettings displayerSettings) {
        return jsonEncode(toJsonObject(displayerSettings).toString());
    }

    public JsonObject toJsonObject( DisplayerSettings displayerSettings ) {
        JsonObject json = Json.createObject();

        // UUID
        json.put(SETTINGS_UUID, displayerSettings.getUUID());

        for (Map.Entry<String, String> entry : displayerSettings.getSettingsFlatMap().entrySet()) {
            setNodeValue(json, entry.getKey(), entry.getValue());
        }

        // Data set
        DataSetLookup dataSetLookup = displayerSettings.getDataSetLookup();
        DataSet dataSet = displayerSettings.getDataSet();
        if (dataSet != null) {
            json.put(DATASET_PREFIX, dataSetJsonMarshaller.toJson(dataSet));
        }
        else if (dataSetLookup != null) {
            json.put(DATASET_LOOKUP_PREFIX, dataSetLookupJsonMarshaller.toJson(dataSetLookup));
        }
        else {
            throw new RuntimeException("Displayer settings dataset lookup not specified");
        }

        // Column settings
        List<ColumnSettings> columnSettingsList = displayerSettings.getColumnSettingsList();
        if (!columnSettingsList.isEmpty()) {
            json.put(COLUMNS_PREFIX, formatColumnSettings(columnSettingsList));
        }

        return json;
    }

    private void setNodeValue(JsonObject node, String path, String value) {
        if (node == null || isBlank(path) || value == null) {
            return;
        }

        int separatorIndex = path.lastIndexOf('.');
        String nodesPath = separatorIndex > 0 ? path.substring(0, separatorIndex) : "";
        String leaf = separatorIndex > 0 ? path.substring(separatorIndex + 1) : path;

        JsonObject _node = findNode(node, nodesPath, true);
        _node.put(leaf, value);
    }

    private JsonObject findNode(JsonObject parent, String path, boolean createPath) {
        if (parent == null) {
            return null;
        }
        if (isBlank(path)) {
            return parent;
        }

        int separatorIndex = path.indexOf('.');
        String strChildNode = separatorIndex > 0 ? path.substring(0, separatorIndex) : path;
        String remainingNodes = separatorIndex > 0 ? path.substring(separatorIndex + 1) : "";

        JsonObject childNode = parent.getObject(strChildNode);
        if (childNode == null && createPath) {
            childNode = Json.createObject();
            parent.put(strChildNode, childNode);
        }
        return findNode(childNode, remainingNodes, createPath);
    }

    private JsonArray formatColumnSettings(List<ColumnSettings> columnSettingsList) {
        JsonArray jsonArray = Json.createArray();
        for (int i=0; i<columnSettingsList.size(); i++) {
            ColumnSettings columnSettings = columnSettingsList.get(i);
            String id = columnSettings.getColumnId();
            String name = columnSettings.getColumnName();
            String expression = columnSettings.getValueExpression();
            String pattern = columnSettings.getValuePattern();
            String empty = columnSettings.getEmptyTemplate();

            JsonObject columnJson = Json.createObject();
            if (!isBlank(id)) {
                columnJson.put(COLUMN_ID, id);
                if (!isBlank(name)) columnJson.put(COLUMN_NAME, name);
                if (!isBlank(expression)) columnJson.put(COLUMN_EXPRESSION, expression);
                if (!isBlank(pattern)) columnJson.put(COLUMN_PATTERN, pattern);
                if (!isBlank(empty)) columnJson.put(COLUMN_EMPTY, empty);
                jsonArray.set(i, columnJson);
            }
        }
        return jsonArray;
    }

    private List<ColumnSettings> parseColumnsFromJson(JsonArray columnsJsonArray) {
        List<ColumnSettings> columnSettingsList = new ArrayList<ColumnSettings>();
        if (columnsJsonArray == null) {
            return columnSettingsList;
        }

        for (int i = 0; i < columnsJsonArray.length(); i++) {
            JsonObject columnJson = columnsJsonArray.getObject(i);
            ColumnSettings columnSettings = new ColumnSettings();
            columnSettingsList.add(columnSettings);

            String columndId = columnJson.getString(COLUMN_ID);
            if (columndId == null) {
                throw new RuntimeException("Column settings null column id");
            }
            columnSettings.setColumnId(columndId);
            columnSettings.setColumnName(columnJson.getString(COLUMN_NAME));
            columnSettings.setValueExpression(columnJson.getString(COLUMN_EXPRESSION));
            columnSettings.setValuePattern(columnJson.getString(COLUMN_PATTERN));
            columnSettings.setEmptyTemplate(columnJson.getString(COLUMN_EMPTY));
        }
        return columnSettingsList;
    }

    private Map<String, String> parseSettingsFromJson(JsonObject settingsJson) {
        Map<String, String> flatSettingsMap = new HashMap<String, String>(30);

        if (settingsJson != null && settingsJson.size() > 0) {
            fillRecursive("", settingsJson, flatSettingsMap);
        }
        return flatSettingsMap;
    }

    private void fillRecursive(String parentPath, JsonObject json, Map<String, String> settings) {
        String sb = isBlank(parentPath) ? "" : parentPath + ".";
        for (String key : json.keys()) {
            String path = sb + key;
            JsonValue value = json.get( key );
            if (value instanceof JsonObject) {
                fillRecursive(path, (JsonObject) value, settings);
            }
            else if (value instanceof JsonString) {
                settings.put(path, ((JsonString) value).getString());
            }
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    private String jsonEncode(String source) {
        String compressed = source;

        for (Map.Entry<String, String> entry : dictionary.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            compressed = compressed.replaceAll("\\s*\\n\\s*\"" + key + "\":\\s", value + ":");
        }

        compressed = compressed.replaceAll("\\s*\\n\\s*", "");
        compressed = compressed.replaceAll(":\"([a-zA-Z_0-9-]+)\"", ":$1");

        return compressed;
    }

    private String jsonDecode(String source) {
        String uncompressed = source;
        String keys = "";

        for (Map.Entry<String, String> entry : dictionary.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            uncompressed = uncompressed.replaceAll("," + value + ":", ",\"" + key + "\": ");
            uncompressed = uncompressed.replaceAll("{" + value + ":", "{\"" + key + "\": ");
            keys += key + "|";
        }

        keys = keys.substring(0, keys.length() - 1);
        uncompressed = uncompressed.replaceAll("\"(" + keys + ")\": ([a-zA-Z_0-9-]+)", "\"$1\": \"$2\"");

        return uncompressed;
    }
}
