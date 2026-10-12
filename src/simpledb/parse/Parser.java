package simpledb.parse;

import java.util.*;

import simpledb.query.*;
import simpledb.record.*;
import simpledb.materialize.*;

/**
 * The SimpleDB parser.
 * @author Edward Sciore
 */
public class Parser {
   private Lexer lex;
   private List<AggregationFn> aggFns = new ArrayList<>();
   
   public Parser(String s) {
      lex = new Lexer(s);
   }
   
// Methods for parsing predicates, terms, expressions, constants, and fields
   
   public String field() {
      return lex.eatId();
   }
   
   public Constant constant() {
      if (lex.matchStringConstant())
         return new Constant(lex.eatStringConstant());
      else
         return new Constant(lex.eatIntConstant());
   }
   
   public Expression expression() {
      if (lex.matchId())
         return new Expression(field());
      else
         return new Expression(constant());
   }
   
   public Term term() {
	   Expression lhs = expression();
	   String opr = lex.eatOpr();
	   Expression rhs = expression();
	   return new Term(lhs, opr, rhs);
	}
   
   public Predicate predicate() {
      Predicate pred = new Predicate(term());
      if (lex.matchKeyword("and")) {
         lex.eatKeyword("and");
         pred.conjoinWith(predicate());
      }
      return pred;
   }
   
// Methods for parsing queries
   
   public QueryData query() {
      aggFns = new ArrayList<>();
      lex.eatKeyword("select");
      List<String> fields = selectList();
      lex.eatKeyword("from");
      Collection<String> tables = tableList();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      List<String> groupFields = new ArrayList<>();
      if (lex.matchKeyword("group")) {
         lex.eatKeyword("group");
         lex.eatKeyword("by");
         groupFields = groupList();
      }
      List<OrderBy> sortFields = new ArrayList<>();
      if (lex.matchKeyword("order")) {
         lex.eatKeyword("order");
         lex.eatKeyword("by");
         sortFields = sortList();
      }
      // with aggregation, every plain select field must be a group field
      if (!aggFns.isEmpty() || !groupFields.isEmpty())
         for (String f : fields)
            if (!groupFields.contains(f) && !isAggField(f))
               throw new BadSyntaxException();
      return new QueryData(fields, tables, pred, groupFields, aggFns, sortFields);
   }

   private boolean isAggField(String fldname) {
      for (AggregationFn fn : aggFns)
         if (fn.fieldName().equals(fldname))
            return true;
      return false;
   }

   private List<String> groupList() {
      List<String> L = new ArrayList<>();
      L.add(field());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(groupList());
      }
      return L;
   }

   // Parses a plain field or an aggregate like sum(grade). Aggregates are
   // registered once in aggFns; the output name (e.g. "sumofgrade") is returned.
   private String fieldOrAggregate() {
      String name = field();
      if (!lex.matchDelim('('))
         return name;
      lex.eatDelim('(');
      String arg = field();
      lex.eatDelim(')');
      AggregationFn fn;
      switch (name) {
         case "sum":   fn = new SumFn(arg);   break;
         case "count": fn = new CountFn(arg); break;
         case "avg":   fn = new AvgFn(arg);   break;
         case "min":   fn = new MinFn(arg);   break;
         case "max":   fn = new MaxFn(arg);   break;
         default: throw new BadSyntaxException();
      }
      if (!isAggField(fn.fieldName()))
         aggFns.add(fn);
      return fn.fieldName();
   }

   private List<OrderBy> sortList() {
      List<OrderBy> L = new ArrayList<>();
      L.add(sortField());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(sortList());
      }
      return L;
   }

   private OrderBy sortField() {
      String fldname = fieldOrAggregate();
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
   
   private List<String> selectList() {
      List<String> L = new ArrayList<String>();
      L.add(fieldOrAggregate());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(selectList());
      }
      return L;
   }
   
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
   
   private List<String> fieldList() {
      List<String> L = new ArrayList<String>();
      L.add(field());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(fieldList());
      }
      return L;
   }
   
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
   
   public CreateTableData createTable() {
      lex.eatKeyword("table");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      Schema sch = fieldDefs();
      lex.eatDelim(')');
      return new CreateTableData(tblname, sch);
   }
   
   private Schema fieldDefs() {
      Schema schema = fieldDef();
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         Schema schema2 = fieldDefs();
         schema.addAll(schema2);
      }
      return schema;
   }
   
   private Schema fieldDef() {
      String fldname = field();
      return fieldType(fldname);
   }
   
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
   
   public CreateViewData createView() {
      lex.eatKeyword("view");
      String viewname = lex.eatId();
      lex.eatKeyword("as");
      QueryData qd = query();
      return new CreateViewData(viewname, qd);
   }
   
   
//  Method for parsing create index commands
   
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

