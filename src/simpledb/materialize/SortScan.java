package simpledb.materialize;
import java.util.*;
import simpledb.query.*;
import simpledb.record.RID;

/**
 * The Scan class for the sort operator: a k-way merge of sorted runs.
 * The priority queue holds the index of every run that still has a
 * current record, ordered by that record. Equal records are ordered by
 * run index, so the output order is the same every time the scan is read.
 * The run that supplied the current record is not in the queue;
 * next() advances it and puts it back before taking the smallest.
 */
public class SortScan implements Scan {
   private final List<UpdateScan> scans = new ArrayList<>();
   private final PriorityQueue<Integer> ready;
   private final boolean[] available;
   private int current = -1, savedCurrent;
   private RID[] savedPositions;
   private boolean[] savedAvailable;

   /**
    * Opens every run and positions the scan before the first record.
    * @param runs the sorted runs produced by SortPlan
    * @param comparator the sort order the runs were sorted by
    */
   public SortScan(List<TempTable> runs, RecordComparator comparator) {
      available = new boolean[runs.size()];
      ready = new PriorityQueue<>((a,b) -> {
         int c = comparator.compare(scans.get(a), scans.get(b));
         return c != 0 ? c : Integer.compare(a,b);
      });
      try {
         for (TempTable run : runs) scans.add(run.open());
         beforeFirst();
      } catch (RuntimeException e) { close(); throw e; }
   }
   public void beforeFirst() {
      ready.clear(); current = -1; savedPositions = null;
      for (int i=0; i<scans.size(); i++) {
         scans.get(i).beforeFirst();
         available[i] = scans.get(i).next();
         if (available[i]) ready.add(i);
      }
   }
   public boolean next() {
      if (current >= 0) {
         available[current] = scans.get(current).next();
         if (available[current]) ready.add(current);
      }
      current = ready.isEmpty() ? -1 : ready.remove();
      return current >= 0;
   }
   /**
    * Saves the whole cursor, so that merge join can come back to it
    * when the right input has several records with the same join value.
    * The state is: which run supplied the current record, which runs
    * are not yet exhausted, and the record each of those runs is on.
    */
   public void savePosition() {
      if (current < 0) throw new IllegalStateException("No current sort row");
      savedCurrent = current; savedAvailable = available.clone();
      savedPositions = new RID[scans.size()];
      for (int i=0; i<scans.size(); i++)
         if (available[i]) savedPositions[i] = scans.get(i).getRid();
   }
   /**
    * Returns every run to its saved record and rebuilds the queue
    * from the runs that were not exhausted, leaving out the current run
    * as next() would.
    */
   public void restorePosition() {
      if (savedPositions == null) throw new IllegalStateException("No saved position");
      ready.clear(); current = savedCurrent;
      for (int i=0; i<scans.size(); i++) {
         available[i] = savedAvailable[i];
         if (available[i]) scans.get(i).moveToRid(savedPositions[i]);
      }
      for (int i=0; i<scans.size(); i++)
         if (available[i] && i != current) ready.add(i);
   }
   public Constant getVal(String f) {
      if (current < 0) throw new IllegalStateException("No current sort row");
      return scans.get(current).getVal(f);
   }
   public int getInt(String f) { return getVal(f).asInt(); }
   public String getString(String f) { return getVal(f).asString(); }
   public boolean hasField(String f) { return !scans.isEmpty() && scans.get(0).hasField(f); }
   public void close() {
      for (UpdateScan s : scans) s.close();
      scans.clear(); ready.clear(); current = -1;
   }
}
