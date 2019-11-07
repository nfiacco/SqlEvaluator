package sql_evaluator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.*;

/**
 * Represents the data loaded from a ".table.json" file.
 */
@JsonDeserialize(using=Table.Deserializer.class)
@JsonSerialize(using=Table.Serializer.class)
public final class Table extends Node {
    public final ArrayList<ColumnDef> columns;
    public final ArrayList<ArrayList<Object>> rows;  // Each value is either a String or Integer object.

    public Table(ArrayList<ColumnDef> columns, ArrayList<ArrayList<Object>> rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public void setQualifier(final String qualifier) {
        for (ColumnDef columnDef : columns) {
            columnDef.qualifier = qualifier;
        }
    }

    public Table filter(final List<ResolvedCondition> resolvedConditions) {
        ArrayList<ArrayList<Object>> matchingRows = new ArrayList<>();
        for (ArrayList<Object> row : rows) {
            if (rowMatches(row, null, resolvedConditions)) {
                matchingRows.add(row);
            }
        }

        return new Table(columns, matchingRows);
    }

    public Table select(final List<Selector> selectors) {
        ArrayList<ColumnDef> selectedColumns = new ArrayList<>();
        ArrayList<Integer> indicesToSelect = new ArrayList<>();
        for (Selector selector : selectors) {
            int index = getMatchingColumnIndex(selector.source);
            ColumnDef columnDef = columns.get(index);
            selectedColumns.add(new ColumnDef(selector.name, columnDef.type));
            indicesToSelect.add(index);
        }

        ArrayList<ArrayList<Object>> selectedRows = new ArrayList<>();
        for (ArrayList<Object> row : rows) {
            ArrayList<Object> selectedRow = new ArrayList<>();
            for (int index : indicesToSelect) {
                selectedRow.add(row.get(index));
            }
            selectedRows.add(selectedRow);
        }

        return new Table(selectedColumns, selectedRows);
    }

    Optional<ColumnDef> getMatchingColumn(final ColumnRef columnRef) {
        int index = getMatchingColumnIndex(columnRef);
        if (index >= 0) {
            return Optional.of(columns.get(index));
        } else {
            return Optional.empty();
        }
    }

    private int getMatchingColumnIndex(final ColumnRef columnRef) {
        for (int i = 0; i < columns.size(); i++) {
            ColumnDef columnDef = columns.get(i);
            if (columnDef.matchesReference(columnRef)) {
                return i;
            }
        }

        return -1;
    }

    private boolean rowMatches(final List<Object> leftRow, final List<Object> rightRow,
                               final List<ResolvedCondition> conditions) {
        boolean match = true;
        for (ResolvedCondition condition : conditions) {
            if (!condition.evaluate(leftRow, rightRow)) {
                match = false;
            }
        }

        return match;
    }

    Table hashJoin(final Table other, final ResolvedCondition indexCondition,
                   final List<ResolvedCondition> remainingConditions) {
        ArrayList<ColumnDef> outputColumns = new ArrayList<>();
        outputColumns.addAll(columns);
        outputColumns.addAll(other.columns);

        Map<Object, List<List<Object>>> index = new HashMap<>();
        for (List<Object> leftRow : rows) {
            Object key = indexCondition.left.getValueForRow(leftRow);
            List<List<Object>> rowList = index.getOrDefault(key, new ArrayList<>());
            rowList.add(leftRow);
            index.put(key, rowList);
        }

        ArrayList<ArrayList<Object>> outputRows = new ArrayList<>();
        for (List<Object> rightRow : other.rows) {
            Object key = indexCondition.right.getValueForRow(rightRow);
            if (index.containsKey(key)) {
                List<List<Object>> matchingRows = index.get(key);
                for (List<Object> leftRow : matchingRows) {
                    ArrayList<Object> outputRow = new ArrayList<>();
                    outputRow.addAll(leftRow);
                    outputRow.addAll(rightRow);

                    if (rowMatches(leftRow, rightRow, remainingConditions)) {
                        outputRows.add(outputRow);
                    }
                }
            }
        }

        return new Table(outputColumns, outputRows);
    }

    Table innerJoin(final Table other, final List<ResolvedCondition> conditions) {
        ArrayList<ColumnDef> outputColumns = new ArrayList<>();
        outputColumns.addAll(columns);
        outputColumns.addAll(other.columns);

        ArrayList<ArrayList<Object>> outputRows = new ArrayList<>();
        for (List<Object> leftRow : rows) {
            for (List<Object> rightRow : other.rows) {
                ArrayList<Object> outputRow = new ArrayList<>();
                outputRow.addAll(leftRow);
                outputRow.addAll(rightRow);

                if (rowMatches(leftRow, rightRow, conditions)) {
                    outputRows.add(outputRow);
                }
            }
        }

        return new Table(outputColumns, outputRows);
    }

    Table crossJoin(final Table other) {
        ArrayList<ColumnDef> crossJoinColumns = new ArrayList<>();
        crossJoinColumns.addAll(columns);
        crossJoinColumns.addAll(other.columns);

        ArrayList<ArrayList<Object>> crossJoinRows = new ArrayList<>();
        for (ArrayList<Object> row : rows) {
            for (ArrayList<Object> otherRow : other.rows) {
                ArrayList<Object> crossJoinRow = new ArrayList<>();
                crossJoinRow.addAll(row);
                crossJoinRow.addAll(otherRow);
                crossJoinRows.add(crossJoinRow);
            }
        }

        return new Table(crossJoinColumns, crossJoinRows);
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    public static final class ColumnDef extends Node {
        public final String name;
        public final SqlType type;

        @JsonIgnore
        public String qualifier;

        @JsonCreator
        public ColumnDef(@JsonProperty("name") String name, @JsonProperty("type") SqlType type) {
            if (name == null) throw new IllegalArgumentException("'name' can't be null");
            if (type == null) throw new IllegalArgumentException("'type' can't be null");
            this.qualifier = null;
            this.name = name;
            this.type = type;
        }

        public boolean matchesReference(final ColumnRef columnRef) {
            return ((columnRef.table == null || columnRef.table.equals(qualifier)) && (columnRef.name.equals(name)));
        }
    }

    public static final class Deserializer extends StdDeserializer<Table> {
        public Deserializer() {
            super(Table.class);
        }

        @Override
        public Table deserialize(JsonParser jp, DeserializationContext ctx) throws IOException {
            if (!jp.isExpectedStartArrayToken()) {
                throw new JsonParseException(jp, "expecting start of an array (for table)");
            }
            jp.nextToken();

            ArrayList<ColumnDef> columns = jp.readValueAs(new TypeReference<ArrayList<ColumnDef>>() {});
            jp.nextToken();

            ArrayList<ArrayList<Object>> rows = new ArrayList<>();
            while (jp.getCurrentToken() != JsonToken.END_ARRAY) {
                rows.add(readRow(jp, columns));
            }
            jp.nextToken();

            return new Table(columns, rows);
        }

        private ArrayList<Object> readRow(JsonParser jp, ArrayList<ColumnDef> columns) throws IOException {
            if (!jp.isExpectedStartArrayToken()) {
                throw new JsonParseException(jp, "expecting start of an array (for table row), got" + jp.getCurrentToken());
            }
            jp.nextToken();

            ArrayList<Object> row = new ArrayList<>();

            for (int i = 0; i < columns.size(); i++) {
                // If there aren't enough cells...
                if (jp.currentToken() == JsonToken.END_ARRAY) {
                    throw new JsonParseException(jp, "row only has " + i + " values, but there are " + columns.size() + " columns");
                }

                row.add(readCell(jp, columns.get(i)));;
            }

            // If there are too many cells...
            if (jp.currentToken() != JsonToken.END_ARRAY) {
                throw new JsonParseException(jp, "row has more than " + columns.size() + " values, but there are only " + columns.size() + " columns");
            }
            jp.nextToken();

            return row;
        }

        private Object readCell(JsonParser jp, ColumnDef columnDef) throws IOException {
            Object value;

            switch (columnDef.type) {
                case STR:
                    if (jp.currentToken() != JsonToken.VALUE_STRING) {
                        throw new JsonParseException(jp, "got invalid cell value for column \"" + columnDef.name + "\"; expecting a string");
                    }
                    value = jp.getText();
                    jp.nextToken();
                    break;
                case INT:
                    if (jp.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                        throw new JsonParseException(jp, "got invalid cell value for column \"" + columnDef.name + "\"; expecting an integer");
                    }
                    value = jp.getIntValue();
                    jp.nextToken();
                    break;
                default:
                    throw new AssertionError("unhandled SqlType: " + columnDef.type);
            }

            return value;
        }
    }

    public static final class Serializer extends StdSerializer<Table> {
        public Serializer() {
            super(Table.class);
        }

        @Override
        public void serialize(Table t, JsonGenerator g, SerializerProvider serializerProvider) throws IOException {
            g.writeStartArray(t.rows.size() + 1);

            g.writeObject(t.columns);

            for (int i = 0; i < t.rows.size(); i++) {
                List<Object> row = t.rows.get(i);
                if (row.size() != t.columns.size()) {
                    throw new AssertionError("row " + (i+1) + " has " + row.size() + " cells, but the table has " + t.columns.size() + " columns");
                }

                g.writeStartArray(row.size());
                for (Object cell : row) {
                    if (cell instanceof String) {
                        g.writeString((String) cell);
                    } else if (cell instanceof Integer) {
                        g.writeNumber((Integer) cell);
                    } else {
                        throw new AssertionError("row " + (i+1) + " has bad cell value type: " + cell.getClass().getName());
                    }
                }
                g.writeEndArray();
            }

            g.writeEndArray();
        }
    }
}
