package simpledb.parse;

import java.util.*;

import simpledb.query.*;
import simpledb.record.*;

/**
 * The SimpleDB parser.
 * @author Edward Sciore
 */
public class Parser {
   private Lexer lex;
   
   /**
    * Creates a parser for the specified SQL statement.
    * @param s the SQL statement
    */
   public Parser(String s) {
      lex = new Lexer(s);
   }
   
// Methods for parsing predicates, terms, expressions, constants, and fields
   
   /**
    * Parses a field name.
    * @return the field name
    */
   public String field() {
      return lex.eatId();
   }
   
   /**
    * Parses a constant, either a string or an integer.
    * @return the constant
    */
   public Constant constant() {
      if (lex.matchStringConstant())
         return new Constant(lex.eatStringConstant());
      else
         return new Constant(lex.eatIntConstant());
   }
   
   /**
    * Parses an expression, which is either a field name or a constant.
    * @return the expression
    */
   public Expression expression() {
      if (lex.matchId())
         return new Expression(field());
      else
         return new Expression(constant());
   }
   
   /**
    * Parses a term: an expression, a comparison operator, and an expression.
    * @return the term
    */
   public Term term() {
	   Expression lhs = expression();
	   String opr = lex.eatOpr();
	   Expression rhs = expression();
	   return new Term(lhs, opr, rhs);
	}
   
   /**
    * Parses a predicate: one or more terms joined by "and".
    * @return the predicate
    */
   public Predicate predicate() {
      Predicate pred = new Predicate(term());
      if (lex.matchKeyword("and")) {
         lex.eatKeyword("and");
         pred.conjoinWith(predicate());
      }
      return pred;
   }
   
// Methods for parsing queries
   
   /**
    * Parses a SELECT query:
    * select [distinct] fields from tables [where predicate]
    * [group by fields] [order by fields].
    * Aggregate calls in the select list and the order by clause are
    * collected into the aggregate list of the returned QueryData, without
    * duplicates.
    * This method does not check that the select fields are consistent with
    * the group by clause.
    * @return the data of the query
    * @throws BadSyntaxException if a required keyword, delimiter or
    * identifier is missing (thrown by the Lexer), or if an unknown
    * aggregate function is used
    */
   public QueryData query() {
      lex.eatKeyword("select");
      boolean distinct = lex.matchKeyword("distinct");
      if (distinct) lex.eatKeyword("distinct");
      List<AggregateFnData> aggregates = new ArrayList<>();
      List<String> fields = selectList(aggregates);
      lex.eatKeyword("from");
      Collection<String> tables = tableList();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      List<String> groupByFields = new ArrayList<>();
      if (lex.matchKeyword("group")) {
          lex.eatKeyword("group");
          lex.eatKeyword("by");
          groupByFields = groupList();
      }
      List<OrderBy> sortFields = new ArrayList<>();
      if (lex.matchKeyword("order")) {
         lex.eatKeyword("order");
         lex.eatKeyword("by");
         sortFields = sortList(aggregates);
      }
      QueryData data = new QueryData(fields, tables, pred, sortFields, groupByFields, aggregates);
      data.setDistinct(distinct);
      return data;
   }

   /**
    * Parses an EXPLAIN statement: the keyword "explain" followed by a
    * query.
    * @return the data of the query to be explained
    * @throws BadSyntaxException if the statement does not start with
    * "explain" or the query is not valid
    */
   public QueryData explainQuery() {
      lex.eatKeyword("explain");
      return query();
   }

   /**
    * Parses the comma-separated items of an ORDER BY clause.
    * Aggregates found here are added to the given list.
    * @param aggregates the aggregates found so far (modified by this method)
    * @return the list of sort fields
    */
   private List<OrderBy> sortList(List<AggregateFnData> aggregates) {
      List<OrderBy> L = new ArrayList<>();
      L.add(sortField(aggregates));
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(sortList(aggregates));
      }
      return L;
   }

   /**
    * Parses the comma-separated field names of a GROUP BY clause.
    * @return the list of group-by fields
    */
   private List<String> groupList() {
       List<String> L = new ArrayList<>();
       L.add(groupField());
       if (lex.matchDelim(',')) {
           lex.eatDelim(',');
           L.addAll(groupList());
       }
       return L;
   }

   /**
    * Parses a single GROUP BY field.
    * @return the field name
    */
   private String groupField() {
       String fldname = field();
       return fldname;
   }

   /**
    * Parses one ORDER BY item: a field or aggregate, optionally followed
    * by "asc" or "desc". The default order is ascending.
    * @param aggregates the aggregates found so far (modified by this method)
    * @return the sort field with its direction
    */
   private OrderBy sortField(List<AggregateFnData> aggregates) {
      String fldname = fieldOrAggregate(aggregates);
      boolean ascending = true;
      if (lex.matchKeyword("asc")) {
         lex.eatKeyword("asc");
         ascending = true;
      }
      else if (lex.matchKeyword("desc")) {
         lex.eatKeyword("desc");
         ascending = false;
      }
      return new OrderBy(fldname, ascending);
   }
   
   /**
    * Parses either a plain field name or an aggregate call such as
    * sum(grade). Only count, sum, avg, min and max are accepted, and the
    * argument must be a single field.
    * A new aggregate is added to the given list unless an identical one is
    * already present, so an aggregate used in both the select list and the
    * order by clause is recorded once.
    * @param aggregates the aggregates found so far (modified by this method)
    * @return the field name, or for an aggregate its output field name
    * (for example "sumofgrade")
    * @throws BadSyntaxException if the function name is not a known
    * aggregate, or if the parentheses or argument are malformed
    */
   private String fieldOrAggregate(List<AggregateFnData> aggregates) {
      String name = field();
      if (!lex.matchDelim('(')) return name;
      if (!Arrays.asList("count", "sum", "avg", "min", "max").contains(name))
         throw new BadSyntaxException("Unknown aggregate: " + name);
      lex.eatDelim('(');
      String source = field();
      lex.eatDelim(')');
      AggregateFnData agg = new AggregateFnData(name, source);
      boolean exists = false;
      for (AggregateFnData previous : aggregates)
         if (previous.outputFieldName().equals(agg.outputFieldName())) exists = true;
      if (!exists) aggregates.add(agg);
      return agg.outputFieldName();
   }

   /**
    * Parses the comma-separated select list. Each item is a field or an
    * aggregate call. Aggregates are added to the given list.
    * @param aggregates the aggregates found so far (modified by this method)
    * @return the output field names, in select order
    */
   private List<String> selectList(List<AggregateFnData> aggregates) {
      List<String> fields = new ArrayList<>();
      fields.add(fieldOrAggregate(aggregates));
      while (lex.matchDelim(',')) {
         lex.eatDelim(',');
         fields.add(fieldOrAggregate(aggregates));
      }
      return fields;
   }

   /**
    * Parses the comma-separated table names of a FROM clause.
    * @return the collection of table names
    */
   private Collection<String> tableList() {
      Collection<String> L = new ArrayList<String>();
      L.add(lex.eatId());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(tableList());
      }
      return L;
   }
   
// Methods for parsing the various update commands
   
   /**
    * Parses an update command: insert, delete, update or create.
    * @return the corresponding data object (InsertData, DeleteData,
    * ModifyData, or the data for a create table, view or index command)
    */
   public Object updateCmd() {
      if (lex.matchKeyword("insert"))
         return insert();
      else if (lex.matchKeyword("delete"))
         return delete();
      else if (lex.matchKeyword("update"))
         return modify();
      else
         return create();
   }
   
   /**
    * Parses a CREATE command and dispatches on table, view or index.
    * @return the data object for the specific create command
    */
   private Object create() {
      lex.eatKeyword("create");
      if (lex.matchKeyword("table"))
         return createTable();
      else if (lex.matchKeyword("view"))
         return createView();
      else
         return createIndex();
   }
   
// Method for parsing delete commands
   
   /**
    * Parses a DELETE statement.
    * @return the delete data
    */
   public DeleteData delete() {
      lex.eatKeyword("delete");
      lex.eatKeyword("from");
      String tblname = lex.eatId();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      return new DeleteData(tblname, pred);
   }
   
// Methods for parsing insert commands
   
   /**
    * Parses an INSERT statement.
    * @return the insert data
    */
   public InsertData insert() {
      lex.eatKeyword("insert");
      lex.eatKeyword("into");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      List<String> flds = fieldList();
      lex.eatDelim(')');
      lex.eatKeyword("values");
      lex.eatDelim('(');
      List<Constant> vals = constList();
      lex.eatDelim(')');
      return new InsertData(tblname, flds, vals);
   }
   
   /**
    * Parses a comma-separated list of field names.
    * @return the list of field names
    */   
   private List<String> fieldList() {
      List<String> L = new ArrayList<String>();
      L.add(field());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(fieldList());
      }
      return L;
   }
   
   /**
    * Parses a comma-separated list of constants.
    * @return the list of constants
    */
   private List<Constant> constList() {
      List<Constant> L = new ArrayList<Constant>();
      L.add(constant());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(constList());
      }
      return L;
   }
   
// Method for parsing modify commands
   
   /**
    * Parses an UPDATE statement.
    * @return the modify data
    */
   public ModifyData modify() {
      lex.eatKeyword("update");
      String tblname = lex.eatId();
      lex.eatKeyword("set");
      String fldname = field();
      lex.eatDelim('=');
      Expression newval = expression();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      return new ModifyData(tblname, fldname, newval, pred);
   }
   
// Method for parsing create table commands
   
   /**
    * Parses a CREATE TABLE statement, after "create" has been consumed.
    * @return the create table data
    */
   public CreateTableData createTable() {
      lex.eatKeyword("table");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      Schema sch = fieldDefs();
      lex.eatDelim(')');
      return new CreateTableData(tblname, sch);
   }
   
   /**
    * Parses a comma-separated list of field definitions.
    * @return a schema containing all the defined fields
    */
   private Schema fieldDefs() {
      Schema schema = fieldDef();
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         Schema schema2 = fieldDefs();
         schema.addAll(schema2);
      }
      return schema;
   }
   
   /**
    * Parses one field definition: a field name and its type.
    * @return a schema containing that field
    */
   private Schema fieldDef() {
      String fldname = field();
      return fieldType(fldname);
   }
   
   /**
    * Parses a field type, either int or varchar(n).
    * @param fldname the name of the field being defined
    * @return a schema containing the field with that type
    */
   private Schema fieldType(String fldname) {
      Schema schema = new Schema();
      if (lex.matchKeyword("int")) {
         lex.eatKeyword("int");
         schema.addIntField(fldname);
      }
      else {
         lex.eatKeyword("varchar");
         lex.eatDelim('(');
         int strLen = lex.eatIntConstant();
         lex.eatDelim(')');
         schema.addStringField(fldname, strLen);
      }
      return schema;
   }
   
// Method for parsing create view commands
   
   /**
    * Parses a CREATE VIEW statement, after "create" has been consumed.
    * @return the create view data
    */
   public CreateViewData createView() {
      lex.eatKeyword("view");
      String viewname = lex.eatId();
      lex.eatKeyword("as");
      QueryData qd = query();
      return new CreateViewData(viewname, qd);
   }
   
   
//  Method for parsing create index commands
   
   /**
    * Parses a CREATE INDEX statement, after "create" has been consumed.
    * The index type defaults to hash. "using btree" selects a B-tree index.
    * @return the create index data
    * @throws BadSyntaxException if "using" is followed by anything other
    * than hash or btree
    */
   public CreateIndexData createIndex() {
      lex.eatKeyword("index");
      String idxname = lex.eatId();
      lex.eatKeyword("on");
         String tblname = lex.eatId();
         lex.eatDelim('(');
         String fldname = field();
         lex.eatDelim(')');

         // Default to using hash indexes
         String idxtype = "hash";
         if (lex.matchKeyword("using")) {
            lex.eatKeyword("using");
            if (lex.matchKeyword("hash")) {
               lex.eatKeyword("hash");
            }
            else if (lex.matchKeyword("btree")) {
               lex.eatKeyword("btree");
               idxtype = "btree";
            }
         else
            throw new BadSyntaxException(); // Invalid index type
         }
      return new CreateIndexData(idxname, tblname, fldname, idxtype);
   }
}

