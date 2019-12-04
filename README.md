## SQL Evaluator - Java

### Overview
This project implements a basic SQL engine, which parses and executes a subset of SQL. There are several optimizations
implemented to improve the performance of queries, such as implicitly converting cross joins to inner joins where
possible.

### Instructions
To run, build the project by navigating to the root of the project and running `mvn compile`.

Then, execute the program using the `sql_evaluator` script. For example, to run the "simple-1.sql" example query:
```
./sql-to-json examples/simple-1.sql  # Writes to "examples/simple-1.sql.json"
./sql_evaluator examples examples/simple-1.sql.json examples/simple-1.out
diff examples/simple-1.expected examples/simple-1.out
```

You can then execute all three of these steps by using the `check` command:

```
./check ./sql_evaluator -- examples examples/simple-1.sql
```

Using this script, you can also run multiple tests:

```
./check ./sql_evaluator -- examples examples/*.sql
```

### Design Choices
I started with the straightforward Cartesian product (cross join) approach. The program has four phases:
1) Verify conditions and selectors
2) Iterate table by table in the "from" clause
    1) Apply any relevant filters
    2) Perform a join with the output of the previous iteration:
        1) If the join has any relevant conditions:
            1) If there is an equality condition, perform a hash join using that condition's attributes
            2) Otherwise, perform a nested loop join
        2) If not, perform a simple cross join
3) Select the output columns

To make the bookkeeping of evaluating conditions easier, I added a new class `ResolvedCondition` containing
two `ResolvedTerm` fields and the type of the values being compared. `ResolvedTerm implements a method `getValueForRow`,
which abstracts away the complexity of whether the term refers to a column or literal.

## Future Optimizations
This implementation doesn't take advantage of several query optimization techniques used widely in commercial SQL engines.

1) Join ordering - could find an optimal ordering of joins so that minimal row iterations are necessary.

2) Implement sorted merge join-- and re-use the sorted indices for future queries

3) Do some analysis on the statistical properties of the tables and use that as input for the query optimization.

4) Use a cost-based query plan evaluation algorithm.

To implement several of these optimizations, some re-architecting would need to be done in order to build query plan
trees, which are the structures that most SQL optimizers work with. This would entail generating some relational
algebra from the SQL query, then constructing the tree, then optimizing based on heuristics and estimated cost.

### Credits
The query parsing and execution scripts were both provided as part of the assignment. My work involved adding the
`Executor.java` and `Validator.java` classes, all of the `Resolved\*` classes, and modifications to the `Table.java`
class to support various join, filter, and select operations. This enables execution of the actual parsed query.
