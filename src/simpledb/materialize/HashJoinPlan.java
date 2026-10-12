package simpledb.materialize;

import java.util.*;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.*;
import simpledb.tx.Transaction;

public class HashJoinPlan implements Plan {
   private Transaction tx;
   private Plan probe, build;
   private String probefld, buildfld;
   private Schema sch = new Schema();

   public HashJoinPlan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
      this.tx = tx;
      // the smaller input (by estimated blocks) is the build side
      if (p2.blocksAccessed() <= p1.blocksAccessed()) {
         probe = p1; probefld = fldname1;
         build = p2; buildfld = fldname2;
      }
      else {
         probe = p2; probefld = fldname2;
         build = p1; buildfld = fldname1;
      }
      sch.addAll(p1.schema());
      sch.addAll(p2.schema());
   }

   public Scan open() {
      // k output partitions need k pinned buffers, plus the source scan
      int k = Math.max(2, tx.availableBuffs() - 3);
      List<TempTable> probeparts = partition(probe, probefld, k);
      List<TempTable> buildparts = partition(build, buildfld, k);
      return new HashJoinScan(probeparts, probe.schema(), probefld,
                              buildparts, build.schema(), buildfld);
   }

   private List<TempTable> partition(Plan p, String fldname, int k) {
      Schema psch = p.schema();
      List<TempTable> parts = new ArrayList<>();
      List<UpdateScan> dests = new ArrayList<>();
      for (int i = 0; i < k; i++) {
         TempTable t = new TempTable(tx, psch);
         parts.add(t);
         dests.add(t.open());
      }
      Scan src = p.open();
      while (src.next()) {
         UpdateScan dest = dests.get(HashJoinScan.partitionOf(src.getVal(fldname), k));
         dest.insert();
         for (String f : psch.fields())
            dest.setVal(f, src.getVal(f));
      }
      src.close();
      for (UpdateScan d : dests)
         d.close();
      return parts;
   }

   // read+write while partitioning, then read while probing: 3(B1 + B2)
   public int blocksAccessed() {
      return 3 * (probe.blocksAccessed() + build.blocksAccessed());
   }

   public int recordsOutput() {
      int maxvals = Math.max(1, Math.max(probe.distinctValues(probefld),
                                         build.distinctValues(buildfld)));
      return (probe.recordsOutput() * build.recordsOutput()) / maxvals;
   }

   public int distinctValues(String fldname) {
      if (probe.schema().hasField(fldname))
         return probe.distinctValues(fldname);
      else
         return build.distinctValues(fldname);
   }

   public Schema schema() {
      return sch;
   }
}