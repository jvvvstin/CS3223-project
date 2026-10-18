package simpledb.materialize;

import java.util.*;
import simpledb.query.*;
import simpledb.record.Schema;

/** Streams matches from one partition at a time; never materializes join output. */
public class HashJoinScan implements Scan {
   private final List<TempTable> buildParts, probeParts;
   private final Schema buildSchema, probeSchema;
   private final String buildKey, probeKey;
   private final boolean buildIsLeft;
   private final Map<Constant, List<Map<String, Constant>>> table = new HashMap<>();
   private Scan probe;
   private Iterator<Map<String, Constant>> matches = Collections.emptyIterator();
   private Map<String, Constant> current;
   private int partition;

   public HashJoinScan(List<TempTable> buildParts, List<TempTable> probeParts,
         Schema buildSchema, Schema probeSchema, String buildKey, String probeKey, boolean buildIsLeft) {
      this.buildParts = buildParts;
      this.probeParts = probeParts;
      this.buildSchema = buildSchema;
      this.probeSchema = probeSchema;
      this.buildKey = buildKey;
      this.probeKey = probeKey;
      this.buildIsLeft = buildIsLeft;
      beforeFirst();
   }
   public void beforeFirst() {
      close();
      partition = 0;
   }
   private boolean nextPartition() {
      if (probe != null) { probe.close(); probe = null; }
      table.clear();
      if (partition >= buildParts.size()) return false;
      Scan build = buildParts.get(partition).open();
      try {
         while (build.next()) {
            Map<String, Constant> row = new HashMap<>();
            for (String field : buildSchema.fields()) row.put(field, build.getVal(field));
            table.computeIfAbsent(build.getVal(buildKey), k -> new ArrayList<>()).add(row);
         }
      } finally { build.close(); }
      probe = probeParts.get(partition++).open();
      return true;
   }
   public boolean next() {
      try {
         while (true) {
            if (matches.hasNext()) { current = matches.next(); return true; }
            if (probe != null && probe.next()) {
               List<Map<String, Constant>> rows = table.get(probe.getVal(probeKey));
               matches = rows == null ? Collections.emptyIterator() : rows.iterator();
            } else if (!nextPartition()) { current = null; return false; }
         }
      } catch (RuntimeException e) { close(); throw e; }
   }
   public Constant getVal(String field) {
      if (current == null) throw new IllegalStateException("No current hash join row");
      if (buildSchema.hasField(field) && (buildIsLeft || !probeSchema.hasField(field)))
         return current.get(field);
      return probe.getVal(field);
   }
   public int getInt(String field) { return getVal(field).asInt(); }
   public String getString(String field) { return getVal(field).asString(); }
   public boolean hasField(String field) { return buildSchema.hasField(field) || probeSchema.hasField(field); }
   public void close() {
      if (probe != null) { probe.close(); probe = null; }
      table.clear();
      matches = Collections.emptyIterator();
      current = null;
      partition = buildParts.size();
   }
}
