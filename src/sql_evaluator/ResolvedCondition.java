package sql_evaluator;

import java.util.List;

public class ResolvedCondition {
    public final Condition.Op op;
    public final ResolvedTerm left;
    public final ResolvedTerm right;
    public final SqlType type;

    public ResolvedCondition(final Condition.Op op, final ResolvedTerm left, final ResolvedTerm right, final SqlType type) {
        if (op == null) throw new IllegalArgumentException("'op' can't be null");
        if (left == null) throw new IllegalArgumentException("'left' can't be null");
        if (right == null) throw new IllegalArgumentException("'right' can't be null");
        this.op = op;
        this.left = left;
        this.right = right;
        this.type = type;
    }

    public boolean evaluate(List<Object> leftRow, List<Object> rightRow) {
        Object valueLeft = left.getValueForRow(leftRow);
        Object valueRight = right.getValueForRow(rightRow);

        switch (type) {
            case INT:
                switch (op) {
                    case EQ:
                        return ((int) valueLeft) == ((int) valueRight);
                    case GE:
                        return ((int) valueLeft) >= ((int) valueRight);
                    case GT:
                        return ((int) valueLeft) > ((int) valueRight);
                    case LE:
                        return ((int) valueLeft) <= ((int) valueRight);
                    case LT:
                        return ((int) valueLeft) < ((int) valueRight);
                    case NE:
                        return ((int) valueLeft) != ((int) valueRight);
                    default:
                        throw new RuntimeException("Unexpected operation type for comparison: " + op);
                }
            case STR:
                switch (op) {
                    case EQ:
                        return ((String) valueLeft).equals((String) valueRight);
                    case NE:
                        return !((String) valueLeft).equals((String) valueRight);
                    default:
                        throw new RuntimeException("Unexpected operation type for comparison: " + op);
                }
            default:
                throw new RuntimeException("Unexpected SQL type for comparison: " + type);
        }
    }
}
