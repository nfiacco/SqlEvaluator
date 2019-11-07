## SQL Evaluator - Java

### Process
The assignment took me around 10 hours. It took me about 3 hours to get a basic MVP working with the cross
 join method, with some time spent to catch all the edge cases. Coming up with a plan to get clean abstractions
for bookkeeping to quite a while. I spent another 4 hours reading about various query optimization techniques and
how query planning works in real SQL engines, and selected a few optimizations I could easily apply here. Actually
implementing these optimizations cleanly took me another 3 hours.

### Instructions
To run, build the project by navigating to the root of the project and running `mvn compile`. You can then execute the
code with the `check` command. For example, to run all the tests in the `examples` folder:
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