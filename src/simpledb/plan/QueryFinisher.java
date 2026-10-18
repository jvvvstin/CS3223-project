package simpledb.plan;

import java.util.*;
import java.sql.Types;
import simpledb.materialize.*;
import simpledb.parse.*;
import simpledb.query.OrderBy;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

/** Shared validation and GROUP BY / DISTINCT / ORDER BY for all planners. */
public final class QueryFinisher {
   private QueryFinisher() {}
   public static Plan finish(Plan input, QueryData data, Transaction tx) {
      Schema schema = input.schema();
      Set<String> outputs = new HashSet<>();
      List<AggregationFn> functions = new ArrayList<>();
      for (String field : data.groupFields()) requireField(schema, field);
      for (AggregateFnData agg : data.aggregates()) {
         requireField(schema, agg.fieldName());
         if ((agg.function().equals("sum") || agg.function().equals("avg"))
               && schema.type(agg.fieldName()) != Types.INTEGER)
            throw new BadSyntaxException(agg.function() + " requires an integer field");
         if (schema.hasField(agg.outputFieldName()))
            throw new BadSyntaxException("Aggregate output conflicts with field: " + agg.outputFieldName());
         if (outputs.add(agg.outputFieldName())) functions.add(AggregationFnFactory.create(agg));
      }
      boolean grouped = data.hasGroupBy() || data.hasAggregates();
      List<String> referenced = new ArrayList<>(data.fields());
      for (OrderBy order : data.sortFields()) referenced.add(order.fieldName());
      for (String field : referenced) {
         if (outputs.contains(field)) continue;
         requireField(schema, field);
         if (grouped && !data.groupFields().contains(field))
            throw new BadSyntaxException("Field must appear in GROUP BY: " + field);
      }
      Plan result = input;
      if (grouped) result = new GroupByPlan(tx, result, data.groupFields(), functions);
      if (data.isDistinct()) {
         for (OrderBy key : data.sortFields())
            if (!data.fields().contains(key.fieldName()))
               throw new BadSyntaxException("ORDER BY field must appear in the select list with DISTINCT: "
                     + key.fieldName());
         List<OrderBy> keys = new ArrayList<>(data.sortFields());
         Set<String> seen = new HashSet<>();
         for (OrderBy key : keys) seen.add(key.fieldName());
         for (String field : data.fields())
            if (seen.add(field)) keys.add(new OrderBy(field));
         return new DistinctPlan(tx, new ProjectPlan(result, data.fields()), keys);
      }
      if (data.hasSortFields()) result = new SortPlan(tx, result, data.sortFields());
      return new ProjectPlan(result, data.fields());
   }
   private static void requireField(Schema schema, String field) {
      if (!schema.hasField(field)) throw new BadSyntaxException("Unknown field: " + field);
   }
}
