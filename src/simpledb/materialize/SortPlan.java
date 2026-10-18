package simpledb.materialize;
import java.util.*;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.*;
import simpledb.tx.Transaction;

/** Bounded initial runs and multiway external merging. */
public class SortPlan implements Plan {
   private final Transaction tx;
   private final Plan p;
   private final Schema sch;
   private final RecordComparator comp;
   private final List<OrderBy> keys;
   private final int finalLimit, estimatedBuffers;
   public SortPlan(Transaction tx, Plan p, List<OrderBy> keys) {
      this(tx, p, keys, Integer.MAX_VALUE);
   }
   /** Limit final pinned runs when a parent needs buffers for sibling operators. */
   public SortPlan(Transaction tx, Plan p, List<OrderBy> keys, int finalLimit) {
      if (finalLimit < 1) throw new IllegalArgumentException("Invalid final run limit");
      this.tx=tx; this.p=p; this.keys=new ArrayList<>(keys);
      this.finalLimit=finalLimit; sch=p.schema(); comp=new RecordComparator(this.keys);
      estimatedBuffers=Math.max(3, tx.availableBuffs());
   }
   public String description() { return "Sort " + keys; }
   public List<Plan> children() { return Collections.singletonList(p); }
   public Scan open() {
      Scan src=p.open();
      List<TempTable> runs;
      try {
         if (tx.availableBuffs()<1) throw new IllegalStateException("Sort needs an output buffer");
         runs=splitIntoRuns(src, tx.availableBuffs());
      } finally { src.close(); }
      if (runs.isEmpty()) runs.add(new TempTable(tx,sch));
      int fanIn=tx.availableBuffs()-1;
      int finalRuns=Math.max(1, Math.min(finalLimit,fanIn));
      if (runs.size()>finalRuns && fanIn<2)
         throw new IllegalStateException("Sort merging needs three available buffers");
      while (runs.size()>finalRuns) runs=mergePass(runs,fanIn);
      return new SortScan(runs,comp);
   }
   private int recordsPerBlock() {
      int n=tx.blockSize()/new Layout(sch).slotSize();
      if (n<1) throw new IllegalArgumentException("Sort record exceeds block size");
      return n;
   }
   /**
    * Input execution + initial writes + intermediate reads/writes + final reads.
    * Assumes B-1 payload blocks per initial run. Parent buffer pressure and
    * duplicate-key join replays may increase actual I/O; this is not elapsed time.
    */
   public int blocksAccessed() {
      long n=Math.max(0,p.recordsOutput()), rpb=recordsPerBlock();
      long blocks=(n+rpb-1)/rpb;
      int fanIn=estimatedBuffers-1, passes=0;
      long capacity=fanIn*rpb, runs=(n+capacity-1)/capacity;
      while (runs>Math.min(finalLimit,fanIn)) {
         runs=(runs+fanIn-1)/fanIn; passes++;
      }
      return (int)Math.min(Integer.MAX_VALUE,(long)p.blocksAccessed()+2L*blocks*(1+passes));
   }
   public int recordsOutput() { return p.recordsOutput(); }
   public int distinctValues(String f) { return p.distinctValues(f); }
   public Schema schema() { return sch; }
   private List<TempTable> splitIntoRuns(Scan src,int memoryBlocks) {
      // Bound payload by block equivalents; Java object overhead is additional.
      int capacity=(int)Math.min(Integer.MAX_VALUE,(long)memoryBlocks*recordsPerBlock());
      List<TempTable> runs=new ArrayList<>();
      src.beforeFirst();
      boolean more=src.next();
      while (more) {
         List<Map<String,Constant>> rows=new ArrayList<>();
         do {
            Map<String,Constant> row=new HashMap<>();
            for (String f:sch.fields()) row.put(f,src.getVal(f));
            rows.add(row); more=src.next();
         } while (more && rows.size()<capacity);
         rows.sort((a,b)-> {
            for (OrderBy key:keys) {
               int c=a.get(key.fieldName()).compareTo(b.get(key.fieldName()));
               if (c!=0) return key.isAscending()?c:-c;
            }
            return 0;
         });
         TempTable run=new TempTable(tx,sch);
         UpdateScan dest=run.open();
         try {
            for (Map<String,Constant> row:rows) {
               dest.insert();
               for (String f:sch.fields()) dest.setVal(f,row.get(f));
            }
         } finally { dest.close(); }
         runs.add(run);
      }
      return runs;
   }
   private List<TempTable> mergePass(List<TempTable> runs,int fanIn) {
      List<TempTable> result=new ArrayList<>();
      for (int start=0; start<runs.size(); start+=fanIn) {
         List<TempTable> group=runs.subList(start,Math.min(runs.size(),start+fanIn));
         if (group.size()==1) { result.add(group.get(0)); continue; }
         SortScan src=new SortScan(group,comp);
         TempTable run=new TempTable(tx,sch);
         try {
            UpdateScan dest=run.open();
            try {
               while (src.next()) {
                  dest.insert();
                  for (String f:sch.fields()) dest.setVal(f,src.getVal(f));
               }
            } finally { dest.close(); }
         } finally { src.close(); }
         result.add(run);
      }
      return result;
   }
}
