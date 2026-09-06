package simpledb.query;

/**
 * A single field + direction pair for an order by clause.
 * @author Eng Juan
 */
public class OrderBy {
   private String fldname;
   private boolean ascending;

   public OrderBy(String fldname) {
      this(fldname, true); // default: ascending
   }

   public OrderBy(String fldname, boolean ascending) {
      this.fldname = fldname;
      this.ascending = ascending;
   }

   public String fieldName() {
      return fldname;
   }

   public boolean isAscending() {
      return ascending;
   }

   public String toString() {
      return fldname + (ascending ? "" : " desc");
   }
}