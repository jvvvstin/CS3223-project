package simpledb.test;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import simpledb.parse.BadSyntaxException;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * Checks that a statement naming a table or field that does not
 * exist is rejected with a readable message, rather than failing
 * inside the record or buffer manager.
 */
public class ValidationTest {
   private static SimpleDB db;
   private static Transaction tx;
   private static int passed = 0;

   public static void main(String[] args) throws Exception {
      Path source = Paths.get(args.length > 0 ? args[0] : "studentdb").toAbsolutePath();
      Path copy = Files.createTempDirectory("validation-").resolve("db");
      copyDatabase(source, copy);
      db = new SimpleDB(copy.toString());
      tx = db.newTx();
      try {
         // A query naming a table that was never created.
         reject("select sname from nosuchtable", "Unknown table: nosuchtable");
         reject("select sname from student, nosuchtable where sid = 1", "Unknown table: nosuchtable");
         reject("explain select sname from nosuchtable", "Unknown table: nosuchtable");

         // An update statement naming a table that was never created.
         reject("insert into nosuchtable(a) values (1)", "Unknown table: nosuchtable");
         reject("delete from nosuchtable", "Unknown table: nosuchtable");
         reject("update nosuchtable set a = 1", "Unknown table: nosuchtable");
         reject("create index badidx on nosuchtable(a)", "Unknown table: nosuchtable");

         // An update statement naming a field the table does not have.
         reject("insert into student(sid, nosuchfield) values (99, 1)",
               "Unknown field in table student: nosuchfield");
         reject("update student set nosuchfield = 1", "Unknown field in table student: nosuchfield");
         reject("create index badidx on student(nosuchfield)",
               "Unknown field in table student: nosuchfield");

         // A query naming a field no table has is still caught downstream.
         reject("select nosuchfield from student", "Unknown field: nosuchfield");

         // A where clause naming a field no table has. The planner used to
         // drop such a term, returning every row or, in a join, the product.
         reject("select sname from student where nosuchfield = 1",
               "Unknown field in where clause: nosuchfield=1");
         reject("select sname, dname from student, dept where majorid = nosuchfield",
               "Unknown field in where clause: majorid=nosuchfield");
         reject("explain select sname from student where nosuchfield > 1",
               "Unknown field in where clause: nosuchfield>1");
         reject("delete from student where nosuchfield = 1",
               "Unknown field in where clause: nosuchfield=1");
         reject("update student set gradyear = 2000 where nosuchfield = 1",
               "Unknown field in where clause: nosuchfield=1");

         // Valid statements are unaffected.
         accept("select sname from student where majorid = 10");
         accept("select dname, count(sid) from student, dept where majorid = did group by dname");
         accept("select distinct majorid from student order by majorid desc");
         // A view name is accepted by the verifier, because the tables it
         // mentions are verified when its definition is planned.  Querying
         // a view still needs a planner that expands view definitions,
         // which the heuristic planner does not do.
         accept("create view v as select sname from student");
         accept("insert into student(sid, sname, majorid, gradyear) values (99, 'zoe', 10, 2024)");

         System.out.println("PASS: " + passed + " validation cases; unknown tables and fields rejected with messages.");
      } finally {
         tx.rollback();
      }
   }

   private static void reject(String sql, String expected) {
      try {
         if (sql.startsWith("select") || sql.startsWith("explain"))
            plan(sql);
         else
            db.planner().executeUpdate(sql, tx);
      }
      catch (BadSyntaxException e) {
         if (e.getMessage() == null || !e.getMessage().contains(expected))
            throw new AssertionError(sql + ": expected \"" + expected + "\", got \"" + e.getMessage() + "\"");
         passed++;
         return;
      }
      throw new AssertionError("Invalid statement accepted: " + sql);
   }

   private static void accept(String sql) {
      if (sql.startsWith("select"))
         plan(sql);
      else
         db.planner().executeUpdate(sql, tx);
      passed++;
   }

   private static void plan(String sql) {
      if (sql.startsWith("explain"))
         db.planner().explainQuery(sql, tx);
      else
         db.planner().createQueryPlan(sql, tx);
   }

   private static void copyDatabase(Path source, Path destination) throws Exception {
      Files.createDirectories(destination);
      try (Stream<Path> files = Files.list(source)) {
         for (Path file : (Iterable<Path>) files::iterator)
            Files.copy(file, destination.resolve(file.getFileName()));
      }
   }
}
