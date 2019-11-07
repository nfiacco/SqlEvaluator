package sql_evaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Validator {

    static void checkQueryValidity(final List<Table> tables, final Query query) {
        for (Condition condition : query.where) {
            checkConditionValidity(tables, condition);
        }

        for (Selector selector : query.select) {
            checkSelectorValidity(tables, selector);
        }
    }

    static void checkConditionValidity(final List<Table> tables, final Condition condition) {
        SqlType leftType;
        if (condition.left instanceof Term.Column) {
            Table.ColumnDef matchingColumn = getMatchingColumn(tables, ((Term.Column) condition.left).ref);
            leftType = matchingColumn.type;
        } else {
            throw new RuntimeException("ERROR: Literal not permitted on left-hand side of expression");
        }

        SqlType rightType;
        if (condition.right instanceof Term.Column) {
            Table.ColumnDef matchingColumn = getMatchingColumn(tables, ((Term.Column) condition.right).ref);
            rightType = matchingColumn.type;
        } else {
            rightType = ((Term.Literal) condition.right).type;
        }

        if (leftType != rightType) {
            throw new RuntimeException("ERROR: Incompatible types to \"" + condition.op.symbol + "\": "
                    + leftType.name.toLowerCase() + " and " + rightType.name.toLowerCase() + ".");
        }
    }

    static void checkSelectorValidity(final List<Table> tables, final Selector selector) {
        getMatchingColumn(tables, selector.source);
    }

    private static Table.ColumnDef getMatchingColumn(final List<Table> tables, final ColumnRef columnRef) {
        List<Table.ColumnDef> matches = new ArrayList<>();
        for (Table table : tables) {
            Optional<Table.ColumnDef> match = table.getMatchingColumn(columnRef);
            match.ifPresent(matches::add);
        }

        if (matches.size() == 1) {
            return matches.get(0);
        } else if (matches.size() > 1) {
            String matchesString = "";
            boolean first = true;
            for (Table.ColumnDef match : matches) {
                if (!first) {
                    matchesString = matchesString + ", ";
                }
                first = false;
                matchesString = matchesString + "\"" + match.qualifier + "\"";
            }
            throw new RuntimeException("ERROR: Column reference \"" + columnRef.name
                    + "\" is ambiguous; present in multiple tables: " + matchesString + ".");
        } else {
            throw new RuntimeException("ERROR: Unknown table name \"" + columnRef.table + "\".");
        }
    }
}
