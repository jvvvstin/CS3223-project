package simpledb.parse;

import java.util.*;

import simpledb.query.*;
import simpledb.materialize.AggregationFn;

/**
 * Data for the SQL <i>select</i> statement.
 * @author Edward Sciore
 */
public class QueryData {
   private List<String> fields;
   private Collection<String> tables;
   private Predicate pred;
   private List<OrderBy> sortFields;
   private List<String> groupFields = new ArrayList<>();
   private List<AggregationFn> aggFns = new ArrayList<>();

   
   /**
    * Saves the field and table list and predicate.
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred) {
      this(fields, tables, pred, new ArrayList<OrderBy>());
   }

   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                     List<OrderBy> sortFields) {
      this.fields = fields;
      this.tables = tables;
      this.pred = pred;
      this.sortFields = sortFields;
   }

   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                    List<String> groupFields, List<AggregationFn> aggFns,
                    List<OrderBy> sortFields) {
      this(fields, tables, pred, sortFields);
      this.groupFields = groupFields;
      this.aggFns = aggFns;
   }

   public List<String> groupFields() {
      return groupFields;
   }

   public List<AggregationFn> aggFns() {
      return aggFns;
   }

   public boolean hasAggregation() {
      return !aggFns.isEmpty() || !groupFields.isEmpty();
   }

   // turns an output name like "sumofx" back into "sum(x)"
   private String display(String fldname) {
      for (AggregationFn fn : aggFns)
         if (fn.fieldName().equals(fldname)) {
            int i = fldname.indexOf("of");
            return fldname.substring(0, i) + "(" + fldname.substring(i + 2) + ")";
         }
      return fldname;
   }
   
   /**
    * Returns the fields mentioned in the select clause.
    * @return a list of field names
    */
   public List<String> fields() {
      return fields;
   }
   
   /**
    * Returns the tables mentioned in the from clause.
    * @return a collection of table names
    */
   public Collection<String> tables() {
      return tables;
   }
   
   /**
    * Returns the predicate that describes which
    * records should be in the output table.
    * @return the query predicate
    */
   public Predicate pred() {
      return pred;
   }

   public List<OrderBy> sortFields() {
      return sortFields;
   }

   public boolean hasSortFields() {
      return sortFields != null && !sortFields.isEmpty();
   }
   
   public String toString() {
      String result = "select ";
      for (String fldname : fields)
         result += display(fldname) + ", ";
      result = result.substring(0, result.length()-2);
      result += " from ";
      for (String tblname : tables)
         result += tblname + ", ";
      result = result.substring(0, result.length()-2);
      String predstring = pred.toString();
      if (!predstring.equals(""))
         result += " where " + predstring;
      if (!groupFields.isEmpty()) {
         result += " group by ";
         for (String g : groupFields)
            result += g + ", ";
         result = result.substring(0, result.length()-2);
      }
      if (hasSortFields()) {
         result += " order by ";
         for (OrderBy ob : sortFields)
            result += display(ob.fieldName()) + (ob.isAscending() ? "" : " desc") + ", ";
         result = result.substring(0, result.length()-2);
      }
      return result;
   }
}
