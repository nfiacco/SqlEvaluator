package sql_evaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class Executor {

    static Table executeQuery(final List<Table> tables, final Query query) {
        checkQueryValidity(tables, query);

        Table outputTable = null;
        for (Table table : tables) {
            Table filteredTable = performFilter(table, query.where);

            if (outputTable == null) {
                outputTable = filteredTable;
            } else {
                outputTable = performJoin(outputTable, filteredTable, query.where);
            }
        }

        return outputTable.select(query.select);
    }

    private static void checkQueryValidity(final List<Table> tables, final Query query) {
        for (Condition condition : query.where) {
            checkConditionValidity(tables, condition);
        }

        for (Selector selector : query.select) {
            checkSelectorValidity(tables, selector);
        }
    }

    private static void checkConditionValidity(final List<Table> tables, final Condition condition) {
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

    private static void checkSelectorValidity(final List<Table> tables, final Selector selector) {
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

    private static Table performFilter(final Table table, final List<Condition> conditions) {
        List<ResolvedCondition> filterConditions = getFilterConditions(table, conditions);
        return table.filter(filterConditions);
    }

    private static List<ResolvedCondition> getFilterConditions(final Table table, final List<Condition> conditions) {
        List<ResolvedCondition> resolvedConditions = new ArrayList<>();
        for (Condition condition : conditions) {
            Optional<ResolvedCondition> resolvedCondition = getFilterConditionIfApplicable(table, condition);
            resolvedCondition.ifPresent(resolvedConditions::add);
        }

        return resolvedConditions;
    }

    /**
     * returns a resolved condition if both tables are referenced in the condition, otherwise an empty optional
     */
    private static Optional<ResolvedCondition> getFilterConditionIfApplicable(final Table first,
                                                                              final Condition condition) {
        // Only support expressions with the column reference on the left-hand side
        if (condition.left instanceof Term.Column && condition.right instanceof Term.Literal) {
            ColumnRef leftColumnRef = ((Term.Column) condition.left).ref;
            ResolvedColumn leftTerm = null;
            ResolvedLiteral rightTerm = null;
            for (int i = 0; i < first.columns.size(); i++) {
                Table.ColumnDef columnDef = first.columns.get(i);
                if (columnDef.matchesReference(leftColumnRef)) {
                    leftTerm = new ResolvedColumn(i, columnDef.type);
                    rightTerm = ResolvedLiteral.fromLiteral((Term.Literal) condition.right);
                }
            }

            if (leftTerm != null) {
                // The order of the terms matters, it's used to determine which table is which when evaluating
                return Optional.of(new ResolvedCondition(condition.op, leftTerm, rightTerm, rightTerm.type));
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    private static Table performJoin(final Table first, final Table second, final List<Condition> conditions) {
        List<ResolvedCondition> joinConditions = getJoinConditions(first, second, conditions);
        if (joinConditions.size() > 0) {
            // perform nested loop join when there is at least one condition
            return first.innerJoin(second, joinConditions);
        } else {
            // perform cross join when there is no condition to join on
            return first.crossJoin(second);
        }
    }

    // find best initial join, perform it, then find subsequent best join using the output table as the input
    // heuristic for best join: perform join with most conditions that apply first (since the result set will be smaller)
    private static List<ResolvedCondition> getJoinConditions(final Table first, final Table second,
                                                             final List<Condition> conditions) {
        List<ResolvedCondition> resolvedConditions = new ArrayList<>();
        for (Condition condition : conditions) {
            Optional<ResolvedCondition> resolvedCondition = getJoinConditionIfApplicable(first, second, condition);
            resolvedCondition.ifPresent(resolvedConditions::add);
        }

        return resolvedConditions;
    }

    /**
     * returns a resolved condition if both tables are referenced in the condition, otherwise an empty optional
     */
    private static Optional<ResolvedCondition> getJoinConditionIfApplicable(final Table first, final Table second,
                                                                            final Condition condition) {
        if (condition.left instanceof Term.Column && condition.right instanceof Term.Column) {
            ColumnRef leftColumnRef = ((Term.Column) condition.left).ref;
            ColumnRef rightColumnRef = ((Term.Column) condition.right).ref;
            ResolvedColumn leftTerm = null;
            ResolvedColumn rightTerm = null;
            Condition.Op op = condition.op;
            for (int i = 0; i < first.columns.size(); i++) {
                Table.ColumnDef columnDef = first.columns.get(i);
                if (columnDef.matchesReference(leftColumnRef)) {
                    leftTerm = new ResolvedColumn(i, columnDef.type);
                } else if (columnDef.matchesReference(rightColumnRef)) {
                    leftTerm = new ResolvedColumn(i, columnDef.type);

                    // need to reverse the operation type if the order of the terms is switching
                    op = op.reverse();
                }
            }

            for (int i = 0; i < second.columns.size(); i++) {
                Table.ColumnDef columnDef = second.columns.get(i);
                if (columnDef.matchesReference(leftColumnRef) || columnDef.matchesReference(rightColumnRef)) {
                    rightTerm = new ResolvedColumn(i, columnDef.type);
                }
            }

            if (leftTerm != null && rightTerm != null) {
                return Optional.of(new ResolvedCondition(op, leftTerm, rightTerm, rightTerm.type));
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }
}
