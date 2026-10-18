package simpledb.test;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import simpledb.plan.*;
import simpledb.parse.BadSyntaxException;
import simpledb.materialize.SortPlan;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/** Run from the project root. Tests a disposable copy of studentdb. */
public class DistinctExplainTest {
   private static SimpleDB db;
   private static Transaction tx;
   private static int passed;
   public static void main(String[] args) throws Exception {
      Path source = Paths.get(args.length == 0 ? "studentdb" : args[0]);
      Path copy = Files.createTempDirectory("distinct-explain-").resolve("studentdb");
      Files.createDirectory(copy);
      try (Stream<Path> files = Files.list(source)) {
         for (Path file : (Iterable<Path>) files::iterator)
            if (Files.isRegularFile(file)) Files.copy(file, copy.resolve(file.getFileName()));
      }
      db = new SimpleDB(copy.toString());
      tx = db.newTx();
      try {
         db.planner().executeUpdate("create table explain(explain int, val int)", tx);
         db.planner().executeUpdate("insert into explain(explain, val) values (7, 8)", tx);
         check("select explain, val from explain", "7|8");
         if (!db.planner().explainQuery("explain select explain from explain", tx).contains("TableScan [explain]"))
            throw new AssertionError("EXPLAIN identifier conflicts with command");
         check("select distinct majorid from student", "10", "20", "30");
         check("SELECT DISTINCT majorid FROM student ORDER BY majorid DESC", "30", "20", "10");
         check("select distinct majorid, gradyear from student order by majorid, gradyear",
               "10|2021", "10|2022", "20|2019", "20|2020", "20|2022", "30|2020", "30|2021");
         check("select distinct majorid, gradyear from student order by gradyear desc, majorid",
               "10|2022", "20|2022", "10|2021", "30|2021", "20|2020", "30|2020", "20|2019");
         check("select distinct majorid, gradyear from student order by gradyear desc",
               "10|2022", "20|2022", "10|2021", "30|2021", "20|2020", "30|2020", "20|2019");
         for (String query : Arrays.asList("select distinct majorid from student",
               "select distinct majorid, gradyear from student order by gradyear desc")) {
            Plan singleSort = db.planner().createQueryPlan(query, tx);
            if (sortCount(singleSort) != 1) throw new AssertionError(singleSort.explain());
         }
         check("select distinct prof from section", "brando", "einstein", "newton", "turing");
         check("select distinct sid from student where sid = 999");
         check("select distinct majorid from student where sid = 1", "10");
         check("select distinct count(sectid) from section group by prof", "1", "2");
         check("select distinct count(sid) from student", "9");
         check("select distinct title, prof from course, section where cid = courseid",
               "calculus|einstein", "calculus|newton", "db systems|turing", "elocution|brando");
         check("select majorid from student order by majorid", "10", "10", "10", "20", "20", "20", "20", "30", "30");
         boolean rejected = false;
         try { db.planner().createQueryPlan("select distinct majorid from student order by sname", tx); }
         catch (BadSyntaxException e) {
            rejected = true;
            if (!e.getMessage().contains("sname")) throw new AssertionError(e);
         }
         if (!rejected) throw new AssertionError("Unselected ORDER BY field accepted");

         Set<String> before = fileNames(copy);
         String plan = db.planner().explainQuery("EXPLAIN select distinct majorid from student", tx);
         if (!plan.contains("Distinct") || !plan.contains("Sort") || !plan.contains("Project")
               || !plan.contains("TableScan [student]") || !plan.contains("estimated blocks="))
            throw new AssertionError(plan);
         if (!before.equals(fileNames(copy))) throw new AssertionError("EXPLAIN created execution temp files");
         System.out.println(plan);
         String join = db.planner().explainQuery("explain select title, prof from course, section where cid = courseid", tx);
         if (!join.contains("Join") || !join.contains("TableScan [course]") || !join.contains("TableScan [section]"))
            throw new AssertionError(join);
         System.out.println(join);
         String grouped = db.planner().explainQuery("explain select majorid, count(sid) from student group by majorid", tx);
         if (!grouped.contains("GroupBy") || !grouped.contains("countofsid")) throw new AssertionError(grouped);
         String sql = "select distinct count(sectid) from section group by prof";
         String roundTrip = new simpledb.parse.Parser(sql).query().toString();
         check(roundTrip, "1", "2");
         System.out.println("PASS: " + passed + " result cases (each scanned twice), EXPLAIN trees, no execution temp files, invalid ORDER BY, SQL round trip.");
      } finally { tx.rollback(); }
   }
   private static Set<String> fileNames(Path dir) throws Exception {
      Set<String> names = new HashSet<>();
      try (Stream<Path> files = Files.list(dir)) { files.forEach(p -> names.add(p.getFileName().toString())); }
      return names;
   }
   private static int sortCount(Plan plan) {
      int count = plan instanceof SortPlan ? 1 : 0;
      for (Plan child : plan.children()) count += sortCount(child);
      return count;
   }
   private static void check(String sql, String... expected) {
      Plan p = db.planner().createQueryPlan(sql, tx);
      int buffers = tx.availableBuffs();
      Scan scan = p.open();
      try {
         for (int pass = 0; pass < 2; pass++) {
            scan.beforeFirst();
            List<String> rows = new ArrayList<>();
            while (scan.next()) {
               List<String> cells = new ArrayList<>();
               for (String f : p.schema().fields()) cells.add(scan.getVal(f).toString());
               rows.add(String.join("|", cells));
            }
            if (!rows.equals(Arrays.asList(expected))) throw new AssertionError(sql + ": " + rows);
         }
      } finally { scan.close(); }
      if (buffers != tx.availableBuffs()) throw new AssertionError("Pinned buffer leak: " + sql);
      passed++;
   }
}
