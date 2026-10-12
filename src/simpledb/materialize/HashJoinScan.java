package simpledb.materialize;

import java.util.*;
import simpledb.query.*;
import simpledb.record.Schema;

public class HashJoinScan implements Scan {
   private List<TempTable> probeparts, buildparts;
   private Schema probesch, buildsch;
   private String probefld, buildfld;
   private int part;
   private Scan probe;
   private Map<Constant, List<Map<String, Constant>>> table;
   private List<Map<String, Constant>> matches;
   private int matchidx;
   private Map<String, Constant> currbuild;

   public HashJoinScan(List<TempTable> probeparts, Schema probesch, String probefld,
                       List<TempTable> buildparts, Schema buildsch, String buildfld) {
      this.probeparts = probeparts;
      this.probesch = probesch;
      this.probefld = probefld;
      this.buildparts = buildparts;
      this.buildsch = buildsch;
      this.buildfld = buildfld;
      beforeFirst();
   }

   static int partitionOf(Constant val, int k) {
      return (val.hashCode() & 0x7fffffff) % k;
   }

   public void beforeFirst() {
      closeProbe();
      part = -1;
      table = null;
      matches = null;
      currbuild = null;
   }

   public boolean next() {
      while (true) {
         if (matches != null && matchidx < matches.size()) {
            currbuild = matches.get(matchidx++);
            return true;
         }
         if (probe != null && probe.next()) {
            matches = table.get(probe.getVal(probefld));
            matchidx = 0;
            continue;
         }
         if (!nextPartition())
            return false;
      }
   }

   private boolean nextPartition() {
      closeProbe();
      part++;
      if (part >= probeparts.size())
         return false;
      table = new HashMap<>();
      Scan bs = buildparts.get(part).open();
      while (bs.next()) {
         Map<String, Constant> row = new HashMap<>();
         for (String f : buildsch.fields())
            row.put(f, bs.getVal(f));
         table.computeIfAbsent(row.get(buildfld), x -> new ArrayList<>()).add(row);
      }
      bs.close();
      probe = probeparts.get(part).open();
      matches = null;
      return true;
   }

   private void closeProbe() {
      if (probe != null) {
         probe.close();
         probe = null;
      }
   }

   public int getInt(String fldname) {
      return getVal(fldname).asInt();
   }

   public String getString(String fldname) {
      return getVal(fldname).asString();
   }

   public Constant getVal(String fldname) {
      if (probesch.hasField(fldname))
         return probe.getVal(fldname);
      if (buildsch.hasField(fldname))
         return currbuild.get(fldname);
      throw new RuntimeException("field " + fldname + " not found.");
   }

   public boolean hasField(String fldname) {
      return probesch.hasField(fldname) || buildsch.hasField(fldname);
   }

   public void close() {
      closeProbe();
   }
}