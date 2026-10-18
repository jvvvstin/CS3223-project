package simpledb.test;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import simpledb.materialize.*;
import simpledb.parse.*;
import simpledb.plan.*;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.opt.HeuristicQueryPlanner;

/** Screenshot regressions, isolated from the user's studentdb. */
public class ReviewRegressionTest {
   private static int checks;
   public static void main(String[] args) throws Exception {
      Path dir = Files.createTempDirectory("simpledb-review-").resolve("studentdb");
      Files.createDirectory(dir);
      try (Stream<Path> files = Files.list(Paths.get("studentdb"))) {
         for (Path file : (Iterable<Path>) files::iterator)
            if (Files.isRegularFile(file)) Files.copy(file, dir.resolve(file.getFileName()));
      }
      SimpleDB db = new SimpleDB(dir.toString());
      Transaction tx = db.newTx();
      try {
         db.planner().executeUpdate("create table kw(count int, sum int, avg int, min int, max int, total int)", tx);
         db.planner().executeUpdate("insert into kw(count, sum, avg, min, max, total) values (2, 3, 4, 5, 6, 7)", tx);
         QueryPlanner[] planners = {new HeuristicQueryPlanner(db.mdMgr()),
               new BasicQueryPlanner(db.mdMgr()), new BetterQueryPlanner(db.mdMgr())};
         for (QueryPlanner qp : planners) {
            check(qp, tx, "select sum(gradyear) from student where sid > 100");
            check(qp, tx, "select count(sid) from student where sid > 100");
            check(qp, tx, "select count(sid), sum(gradyear), avg(gradyear), min(sname), max(sname) from student where sid > 100");
            check(qp, tx, "select majorid, count(sid) from student where sid > 100 group by majorid");
            check(qp, tx, "select sname from student where sid > 100 order by sname");
            check(qp, tx, "select avg(gradyear) from student", "2021");
            check(qp, tx, "select min(sname), max(sname) from student", "amy|sue");
            check(qp, tx, "select majorid, count(sid) from student group by majorid order by count(sid) desc", "20|4", "10|3", "30|2");
            check(qp, tx, "select majorid from student group by majorid order by count(sid) desc", "20", "10", "30");
            check(qp, tx, "select count, sum, avg, min, max from kw", "2|3|4|5|6");
            check(qp, tx, "select count(count), sum(sum), avg(avg), min(min), max(max) from kw", "1|3|4|5|6");
            reject(qp, tx, "select sid, count(sid) from student");
            reject(qp, tx, "select sname, count(sid) from student group by majorid");
            reject(qp, tx, "select majorid, count(sid) from student group by majorid order by sid");
            reject(qp, tx, "select sum(sname) from student");
            reject(qp, tx, "select count(missing) from student");
            // Parsing once and planning repeatedly must not retain mutable aggregate state.
            QueryData data = new Parser("select majorid, count(sid) from student group by majorid order by count(sid) desc").query();
            check(qp, tx, data.toString(), "20|4", "10|3", "30|2");
            Plan plan = qp.createPlan(new Parser("select majorid, count(sid) from student group by majorid").query(), tx);
            Scan a = plan.open(), b = plan.open();
            try {
               if (!a.next() || !b.next() || !b.next() || a.getInt("countofsid") != 3 || b.getInt("countofsid") != 4)
                  throw new AssertionError("Aggregate state shared between scans");
            } finally { a.close(); b.close(); }
            checks++;
         }
         hashChecks(db, tx, dir);
         duplicateHashChecks(db, tx);
         System.out.println("PASS: " + checks + " screenshot regression checks across all three planners.");
      } finally { tx.rollback(); }
   }

   private static void hashChecks(SimpleDB db, Transaction tx, Path dir) throws Exception {
      db.planner().executeUpdate("create table hl(lkey int, lid int)", tx);
      db.planner().executeUpdate("create table hr(rkey int, rid int)", tx);
      for (int i = 0; i < 90; i++)
         db.planner().executeUpdate("insert into hl(lkey, lid) values (" + (i % 3 - 1) + ", " + i + ")", tx);
      for (int i = 0; i < 3; i++)
         db.planner().executeUpdate("insert into hr(rkey, rid) values (" + (i - 1) + ", " + i + ")", tx);
      Plan left = new TablePlan(tx, "hl", db.mdMgr()), right = new TablePlan(tx, "hr", db.mdMgr());
      for (boolean reverse : new boolean[]{false, true}) {
         Plan hash = new HashJoinPlan(tx, reverse ? right : left, reverse ? left : right,
               reverse ? "rkey" : "lkey", reverse ? "lkey" : "rkey");
         int buffers = tx.availableBuffs();
         Scan scan = hash.open();
         long size = tableBytes(dir);
         try {
            for (int pass = 0; pass < 2; pass++) {
               scan.beforeFirst();
               Set<Integer> ids = new HashSet<>();
               while (scan.next()) {
                  if (scan.getInt("lkey") != scan.getInt("rkey")) throw new AssertionError("Hash mismatch");
                  ids.add(scan.getInt("lid"));
               }
               if (ids.size() != 90) throw new AssertionError("Missing duplicate matches");
            }
            if (size != tableBytes(dir)) throw new AssertionError("Hash probing wrote table output");
         } finally { scan.close(); }
         if (buffers != tx.availableBuffs()) throw new AssertionError("Hash buffer leak");
         checks++;
      }
      check(new HeuristicQueryPlanner(db.mdMgr()), tx,
            "select lid from hl, hr where lkey = rkey and rid = 99");
   }
   private static long tableBytes(Path dir) throws Exception {
      long size = 0;
      try (Stream<Path> files = Files.list(dir)) {
         for (Path file : (Iterable<Path>) files::iterator)
            if (file.toString().endsWith(".tbl")) size += Files.size(file);
      }
      return size;
   }
   private static void duplicateHashChecks(SimpleDB db, Transaction tx) {
      db.planner().executeUpdate("create table hs1(k1 varchar(8), n1 int)", tx);
      db.planner().executeUpdate("create table hs2(k2 varchar(8), n2 int)", tx);
      for (int i = 0; i < 90; i++) {
         String key = i % 2 == 0 ? "Aa" : "BB"; // Same Java hash; different values.
         db.planner().executeUpdate("insert into hs1(k1, n1) values ('" + key + "', " + i + ")", tx);
         if (i < 60)
            db.planner().executeUpdate("insert into hs2(k2, n2) values ('" + key + "', " + i + ")", tx);
      }
      Plan a = sized(new TablePlan(tx, "hs1", db.mdMgr()), 90);
      Plan b = sized(new TablePlan(tx, "hs2", db.mdMgr()), 60);
      for (boolean reverse : new boolean[]{false, true}) {
         HashJoinPlan plan = new HashJoinPlan(tx, reverse ? b : a, reverse ? a : b,
               reverse ? "k2" : "k1", reverse ? "k1" : "k2");
         int buffers = tx.availableBuffs();
         Scan scan = plan.open();
         try {
            for (int pass = 0; pass < 2; pass++) {
               scan.beforeFirst();
               int rows = 0;
               Set<String> pairs = new HashSet<>();
               while (scan.next()) {
                  if (!scan.getString("k1").equals(scan.getString("k2")))
                     throw new AssertionError("Hash collision produced a false match");
                  rows++;
                  pairs.add(scan.getInt("n1") + "|" + scan.getInt("n2"));
               }
               if (rows != 2700 || pairs.size() != 2700)
                  throw new AssertionError("Duplicate hash join rows: " + rows);
            }
         } finally { scan.close(); }
         if (buffers != tx.availableBuffs()) throw new AssertionError("Multi-partition buffer leak");
         // Closing before consuming all matches must also release the probe.
         scan = plan.open();
         scan.next();
         scan.close();
         if (buffers != tx.availableBuffs()) throw new AssertionError("Early-close buffer leak");
         checks++;
      }
   }
   private static Plan sized(final Plan input, final int count) {
      // Fixed test statistics avoid dependence on StatMgr's periodic refresh.
      return new Plan() {
         public Scan open() { return input.open(); }
         public int blocksAccessed() { return input.blocksAccessed(); }
         public int recordsOutput() { return count; }
         public int distinctValues(String field) { return input.distinctValues(field); }
         public simpledb.record.Schema schema() { return input.schema(); }
      };
   }
   private static void reject(QueryPlanner qp, Transaction tx, String sql) {
      try { qp.createPlan(new Parser(sql).query(), tx); }
      catch (BadSyntaxException expected) { checks++; return; }
      throw new AssertionError("Invalid query accepted: " + sql);
   }
   private static void check(QueryPlanner qp, Transaction tx, String sql, String... expected) {
      Plan p = qp.createPlan(new Parser(sql).query(), tx);
      Scan scan = p.open();
      try {
         for (int pass = 0; pass < 2; pass++) {
            scan.beforeFirst();
            List<String> rows = new ArrayList<>();
            while (scan.next()) {
               List<String> cells = new ArrayList<>();
               for (String field : p.schema().fields()) cells.add(scan.getVal(field).toString());
               rows.add(String.join("|", cells));
            }
            if (!rows.equals(Arrays.asList(expected))) throw new AssertionError(sql + ": " + rows);
         }
      } finally { scan.close(); }
      checks++;
   }
}
