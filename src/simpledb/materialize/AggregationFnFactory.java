package simpledb.materialize;

import simpledb.parse.AggregateFnData;
import simpledb.parse.BadSyntaxException;

/** Parse data stays immutable; each plan/scan gets fresh accumulators. */
public final class AggregationFnFactory {
   private AggregationFnFactory() {}
   public static AggregationFn create(AggregateFnData data) {
      String field = data.fieldName();
      switch (data.function()) {
         case "count": return new CountFn(field);
         case "sum": return new SumFn(field);
         case "avg": return new AvgFn(field);
         case "min": return new MinFn(field);
         case "max": return new MaxFn(field);
         default: throw new BadSyntaxException("Unknown aggregate: " + data.function());
      }
   }
}
