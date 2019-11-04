package sql_evaluator;

import java.util.ArrayList;

public abstract class ResolvedTerm {
    public final SqlType type;

    public ResolvedTerm(final SqlType type) {
        this.type = type;
    }

    public abstract Object getValueForRow(final ArrayList<Object> row);
}
