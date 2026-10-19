package simpledb.query;

/**
 * A single field + direction pair for an order by clause.
 * @author Eng Juan
 */
public class OrderBy {
   private String fldname;
   private boolean ascending;

   /**
    * Creates an order by item that sorts in ascending order.
    * @param fldname the name of the field to sort on
    */
   public OrderBy(String fldname) {
      this(fldname, true); // default: ascending
   }

   /**
    * Creates an order by item with the specified direction.
    * @param fldname the name of the field to sort on
    * @param ascending true to sort in ascending order, false for descending
    */
   public OrderBy(String fldname, boolean ascending) {
      this.fldname = fldname;
      this.ascending = ascending;
   }

   /**
    * Returns the name of the field to sort on.
    * @return the field name, or the output field name of an aggregate
    */
   public String fieldName() {
      return fldname;
   }

   /**
    * Returns the direction of the sort.
    * @return true if the sort is ascending, false if it is descending
    */
   public boolean isAscending() {
      return ascending;
   }

   /**
    * Returns the item as it would appear in an order by clause: the field
    * name, followed by " desc" for a descending sort. Ascending is the
    * default, so nothing is added for it.
    * An aggregate is shown by its output field name (for example
    * "sumofgrade"), not in the form sum(grade).
    * @return the string form of the order by item
    */
   public String toString() {
      return fldname + (ascending ? "" : " desc");
   }
}