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
   /**
    * Creates a sort plan that leaves at most finalLimit sorted runs for
    * the final merge. Each run in the final merge keeps a buffer pinned
    * while the scan is open, so a parent operator that needs buffers for
    * its other inputs can use this to reserve them.
    * @param tx the calling transaction
    * @param p the plan for the underlying query
    * @param keys the sort keys, in order of priority
    * @param finalLimit the maximum number of runs merged by the returned scan
    * @throws IllegalArgumentException if finalLimit is less than 1
    */
   public SortPlan(Transaction tx, Plan p, List<OrderBy> keys, int finalLimit) {
      if (finalLimit < 1) throw new IllegalArgumentException("Invalid final run limit");
      this.tx=tx; this.p=p; this.keys=new ArrayList<>(keys);
      this.finalLimit=finalLimit; sch=p.schema(); comp=new RecordComparator(this.keys);
      estimatedBuffers=Math.max(3, tx.availableBuffs());
   }
   /**
    * Returns a short description of this operator, for use in a plan
    * display.
    * @return the text "Sort" followed by the sort keys
    */
   public String description() { return "Sort " + keys; }

   /**
    * Returns the plans that feed this operator.
    * @return a list containing the single underlying plan
    */
   public List<Plan> children() { return Collections.singletonList(p); }

   /**
    * Executes the sort and returns a scan over the sorted output.
    * The input is read once and written out as sorted runs, which are then
    * merged in passes until at most min(finalLimit, fan-in) runs remain,
    * where the fan-in is the number of available buffers minus one.
    * The returned scan merges the remaining runs as it is read.
    * The underlying scan is closed before this method returns. If the input
    * is empty, the scan has no records.
    * @return a scan that produces the records in sorted order
    * @throws IllegalStateException if no buffer is available for sorting,
    * or if merging is needed but fewer than three buffers are available
    */
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

   /**
    * Computes how many records of the input schema fit in one block.
    * @return the number of records per block
    * @throws IllegalArgumentException if a single record is larger than a block
    */
   private int recordsPerBlock() {
      int n=tx.blockSize()/new Layout(sch).slotSize();
      if (n<1) throw new IllegalArgumentException("Sort record exceeds block size");
      return n;
   }

   /**
    * Estimates the number of block accesses to produce the sorted output:
    * the cost of executing the input, plus writing the initial runs, plus
    * reading and writing the data once for each intermediate merge pass,
    * plus reading the final runs. Each initial run is assumed to hold
    * (buffers - 1) blocks of records, using the number of buffers that were
    * available when the plan was created.
    * Pressure on buffers from parent operators and the replaying of
    * duplicate keys in a merge join can make the actual cost higher.
    * The result is a count of block accesses, not elapsed time.
    * @return the estimated number of block accesses
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

   /**
    * Returns the number of output records, which is the same as in the
    * underlying plan, since sorting does not add or remove records.
    * @return the estimated number of output records
    */
   public int recordsOutput() { return p.recordsOutput(); }

   /**
    * Returns the number of distinct values for the specified field, which
    * is the same as in the underlying plan.
    * @param f the name of the field
    * @return the estimated number of distinct values
    */
   public int distinctValues(String f) { return p.distinctValues(f); }

   /**
    * Returns the schema of the sorted output, which is the same as the
    * schema of the underlying plan.
    * @return the schema
    */
   public Schema schema() { return sch; }

   /**
    * Reads the input and writes it out as a list of sorted runs. Records
    * are collected in memory until the equivalent of memoryBlocks blocks
    * has been read, sorted by the sort keys (honouring each key's
    * direction), and then written to a new temporary table.
    * The caller is responsible for closing the source scan.
    * @param src the input scan
    * @param memoryBlocks the number of blocks' worth of records per run
    * @return the sorted runs, empty if the input has no records
    */
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

   /**
    * Performs one merge pass. The runs are taken in groups of up to fanIn,
    * and each group is merged into a single, longer sorted run.
    * A group consisting of one run is carried over unchanged.
    * @param runs the current sorted runs
    * @param fanIn the maximum number of runs merged into one
    * @return the runs produced by the pass
    */
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
