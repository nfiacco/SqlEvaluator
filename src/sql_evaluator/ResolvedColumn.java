package sql_evaluator;

import java.util.List;

public class ResolvedColumn extends ResolvedTerm {
    public final int columnIndex;

    public ResolvedColumn(final int columnIndex, final SqlType type) {
        super(type);
        this.columnIndex = columnIndex;
    }

    public Object getValueForRow(final List<Object> row) {
        return row.get(columnIndex);
    }
}
