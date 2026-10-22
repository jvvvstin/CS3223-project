package simpledb.parse;

import java.util.*;

import simpledb.query.*;

/**
 * Data for the SQL <i>select</i> statement.
 * @author Edward Sciore
 */
public class QueryData {
   private List<String> fields;
   private Collection<String> tables;
   private Predicate pred;
   private List<OrderBy> sortFields;
   private List<String> groupFields;
   private List<AggregateFnData> aggregates;
   private boolean distinct;
   public boolean isDistinct() { return distinct; }
   public void setDistinct(boolean distinct) { this.distinct = distinct; }
   
   /**
    * Creates the data for a query with no order by clause, no group by
    * clause and no aggregates.
    * @param fields the select fields
    * @param tables the tables in the from clause
    * @param pred the query predicate
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred) {
      this(fields, tables, pred, new ArrayList<OrderBy>());
   }

   /**
    * Creates the data for a query with an order by clause but no group by
    * clause and no aggregates.
    * @param fields the select fields
    * @param tables the tables in the from clause
    * @param pred the query predicate
    * @param sortFields the order by items, in order of priority
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                     List<OrderBy> sortFields) {
       this(fields, tables, pred, sortFields, new ArrayList<String>(), new ArrayList<AggregateFnData>());
   }

   /**
    * Creates the data for a query with all optional clauses.
    * @param fields the select fields. An aggregate is represented here by
    * its output field name (for example "sumofgrade").
    * @param tables the tables in the from clause
    * @param pred the query predicate
    * @param sortFields the order by items, in order of priority
    * @param groupFields the group by fields
    * @param aggregates the aggregate functions used in the select list
    * or order by clause, without duplicates
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                    List<OrderBy> sortFields,  List<String> groupFields, List<AggregateFnData> aggregates) {
       this.fields = fields;
       this.tables = tables;
       this.pred = pred;
       this.sortFields = sortFields;
       this.groupFields = groupFields;
       this.aggregates = aggregates;
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

   /**
    * Returns the items of the order by clause.
    * @return a list of order by items, empty if there is no order by clause
    */
   public List<OrderBy> sortFields() {
      return sortFields;
   }

   /**
    * Returns the fields of the group by clause.
    * @return a list of field names, empty if there is no group by clause
    */
   public List<String> groupFields() {
      return groupFields;
   }

   /**
    * Returns the aggregate functions used in the select list or the
    * order by clause.
    * @return a list of aggregate functions, empty if there are none
    */
    public List<AggregateFnData> aggregates() {
      return aggregates;
   }

   /**
    * Returns whether the query has an order by clause.
    * @return true if there is at least one order by item
    */
   public boolean hasSortFields() {
      return sortFields != null && !sortFields.isEmpty();
   }

   /**
    * Returns whether the query has a group by clause.
    * @return true if there is at least one group by field
    */
   public boolean hasGroupBy() {
      return groupFields != null && !groupFields.isEmpty();
   }

   /**
    * Returns whether the query uses any aggregate function.
    * @return true if there is at least one aggregate
    */
   public boolean hasAggregates() {
      return aggregates != null && !aggregates.isEmpty();
   }
   
   /**
    * Returns the query as an SQL string. The select list and the order
    * by clause show each aggregate in its original form, for example
    * sum(grade), rather than by its output field name.
    * The distinct keyword, where clause, group by clause and order by
    * clause appear only if they are present in the query.
    * This string is used as the stored definition of a view.
    * @return the SQL text of the query
    */
   public String toString() {
      String result = distinct ? "select distinct " : "select ";
      for (String fldname : fields) {
         String expression = fldname;
         for (AggregateFnData agg : aggregates)
            if (agg.outputFieldName().equals(fldname)) {
               expression = agg.toString();
               break;
            }
         result += expression + ", ";
      }
      result = result.substring(0, result.length()-2);
      result += " from ";
      for (String tblname : tables)
         result += tblname + ", ";
      result = result.substring(0, result.length()-2);
      String predstring = pred.toString();
      if (!predstring.equals(""))
         result += " where " + predstring;
      if (hasGroupBy()) {
          result += " group by ";
          for (String groupby : groupFields)
             result += groupby + ", ";
          result = result.substring(0, result.length()-2);
      }
      if (hasSortFields()) {
         result += " order by ";
         for (OrderBy ob : sortFields)
            {
               String expression = ob.fieldName();
               for (AggregateFnData agg : aggregates)
                  if (agg.outputFieldName().equals(expression)) { expression = agg.toString(); break; }
               result += expression + (ob.isAscending() ? "" : " desc") + ", ";
            }
         result = result.substring(0, result.length()-2);
      }
      return result;
   }
}
