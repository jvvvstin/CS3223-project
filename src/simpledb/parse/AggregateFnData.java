package simpledb.parse;

/**
 * Data for one aggregate function in a query, as the parser read it:
 * the function name and the field it applies to.
 * QueryFinisher validates it and AggregationFnFactory turns it
 * into the matching AggregationFn.
 */
public class AggregateFnData {
   private String fn;
   private String fldname;

   /**
    * @param fn the function name in lower case, e.g. "count"
    * @param fldname the field the function applies to
    */
   public AggregateFnData(String fn, String fldname) {
      this.fn = fn;
      this.fldname = fldname;
   }

   /** Returns the function name, e.g. "count". */
   public String function() {
      return fn;
   }

   /** Returns the name of the field the function applies to. */
   public String fieldName() {
      return fldname;
   }

   /**
    * Returns the name of the output column, e.g. "countofsid".
    * A query can also use this name in its ORDER BY clause.
    */
   public String outputFieldName() {
      return fn + "of" + fldname;
   }

   public String toString() {
      return fn.toUpperCase() + "(" + fldname + ")";
   }
}
