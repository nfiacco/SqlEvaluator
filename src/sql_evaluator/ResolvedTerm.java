package sql_evaluator;

import java.util.List;

public abstract class ResolvedTerm {
    public final SqlType type;

    public ResolvedTerm(final SqlType type) {
        this.type = type;
    }

    public abstract Object getValueForRow(final List<Object> row);
}
