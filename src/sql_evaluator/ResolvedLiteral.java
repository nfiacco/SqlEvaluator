package sql_evaluator;

import java.util.List;

public class ResolvedLiteral extends ResolvedTerm {
    public final Object value;

    public static ResolvedLiteral fromLiteral(final Term.Literal term) {
        return new ResolvedLiteral(term.value, term.type);
    }

    public ResolvedLiteral(final Object value, final SqlType type) {
        super(type);
        this.value = value;
    }

    public Object getValueForRow(final List<Object> row) {
        return value;
    }
}
