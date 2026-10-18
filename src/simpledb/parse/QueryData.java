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
   
   /**
    * Saves the field and table list and predicate.
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred) {
      this(fields, tables, pred, new ArrayList<OrderBy>());
   }

   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                     List<OrderBy> sortFields) {
       this(fields, tables, pred, sortFields, new ArrayList<String>(), new ArrayList<AggregateFnData>());
   }

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

   public List<OrderBy> sortFields() {
      return sortFields;
   }

    public List<String> groupFields() {
        return groupFields;
    }

    public List<AggregateFnData> aggregates() {
        return aggregates;
    }

   public boolean hasSortFields() {
      return sortFields != null && !sortFields.isEmpty();
   }

   public boolean hasGroupBy() {
      return groupFields != null && !groupFields.isEmpty();
   }

   public boolean hasAggregates() {
      return aggregates != null && !aggregates.isEmpty();
   }
   
   public String toString() {
      String result = "select ";
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
