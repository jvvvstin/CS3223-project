package simpledb.test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import simpledb.opt.*;
import simpledb.parse.Parser;
import simpledb.plan.*;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * Run as Java application from the project root.
 * Args: sourceDB outputDirectory warmups repetitions seed [tablePrefix] [configurationRegex]
 * Every configuration uses a private database copy; sourceDB is never opened.
 */
public class JoinExperiments {
   public static final String TWO = "select sid, sname, eid, grade from student, enroll where sid = studentid";
   public static final String FOUR = "select sid, sname, eid, title, prof, grade from student, enroll, section, course "
         + "where sid = studentid and sectionid = sectid and courseid = cid";
   private static volatile int sink;

   private static class Configuration {
      final String id, sql;
      final JoinMethod method;
      final List<String> order;
      Configuration(String id, String sql, JoinMethod method, String... order) {
         this.id = id; this.sql = sql; this.method = method;
         this.order = order.length == 0 ? null : Arrays.asList(order);
      }
   }
   public static void main(String[] args) throws Exception {
      Locale.setDefault(Locale.ROOT);
      Path source = Paths.get(args.length > 0 ? args[0] : "studentdb").toAbsolutePath();
      Path output = Paths.get(args.length > 1 ? args[1] : "output/joins/run-" + System.currentTimeMillis());
      int warmups = args.length > 2 ? Integer.parseInt(args[2]) : 10;
      int repetitions = args.length > 3 ? Integer.parseInt(args[3]) : 30;
      long seed = args.length > 4 ? Long.parseLong(args[4]) : 20261018;
      String prefix = args.length > 5 ? args[5] : "";
      if (!prefix.matches("[a-z_]*")) throw new IllegalArgumentException("Invalid table prefix");
      String filter = args.length > 6 ? args[6] : ".*";
      if (warmups < 0 || repetitions < 1) throw new IllegalArgumentException("Invalid repetition count");
      if (!Files.isRegularFile(source.resolve("tblcat.tbl"))) throw new IllegalArgumentException("Not a database: " + source);
      Files.createDirectories(output);
      // Refuse overwrites so earlier measurements remain reviewable.
      Path samples = output.resolve("samples.csv"), plans = output.resolve("plans.txt");
      List<Configuration> configs = new ArrayList<>();
      for (JoinMethod method : JoinMethod.values())
         configs.add(new Configuration("E1_" + method, TWO, method, "student", "enroll"));
      for (JoinMethod method : new JoinMethod[]{JoinMethod.NESTED_LOOP, JoinMethod.HASH, JoinMethod.MERGE})
         configs.add(new Configuration("E2_OPT_" + method, FOUR, method));
      String[][] orders = {{"student", "enroll", "section", "course"},
                           {"course", "section", "enroll", "student"},
                           {"section", "enroll", "student", "course"}};
      for (int i = 0; i < orders.length; i++)
         for (JoinMethod method : new JoinMethod[]{JoinMethod.NESTED_LOOP, JoinMethod.HASH, JoinMethod.MERGE, JoinMethod.MULTIBUFFER})
            configs.add(new Configuration("E2_P" + (i + 1) + "_" + method, FOUR, method, orders[i]));
      List<Configuration> selected = new ArrayList<>();
      for (Configuration c : configs) {
         if (!c.id.matches(filter)) continue;
         String sql = c.sql;
         for (String name : Arrays.asList("student","enroll","section","course"))
            sql = sql.replaceAll("\\b" + name + "\\b", prefix + name);
         String[] order = c.order == null ? new String[0] : c.order.stream().map(t -> prefix+t).toArray(String[]::new);
         selected.add(new Configuration(c.id, sql, c.method, order));
      }
      configs = selected;
      if (configs.isEmpty()) throw new IllegalArgumentException("No configurations match " + filter);
      Collections.shuffle(configs, new Random(seed));
      Map<String, List<String>> reference = new HashMap<>();
      String environment = "Java=" + System.getProperty("java.version") + "\nVM=" + System.getProperty("java.vm.name")
            + "\nOS=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
            + "\nArch=" + System.getProperty("os.arch") + "\nCPUs=" + Runtime.getRuntime().availableProcessors()
            + "\nMax heap=" + Runtime.getRuntime().maxMemory() + "\nBlock bytes=" + SimpleDB.BLOCK_SIZE
            + "\nBuffers=" + SimpleDB.BUFFER_SIZE + "\nWarmups=" + warmups + "\nRepetitions=" + repetitions
            + "\nSeed=" + seed + "\nSource=" + source + "\nPrefix=" + prefix + "\nFilter=" + filter + "\n";
      Files.write(output.resolve("environment.txt"), environment.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
      try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(samples, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW));
           PrintWriter planLog = new PrintWriter(Files.newBufferedWriter(plans, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW))) {
         csv.println("configuration,method,round,planning_ns,execution_ns,rows");
         for (Configuration config : configs) {
            Path copy = Files.createTempDirectory("simpledb-benchmark-").resolve("db");
            copyDatabase(source, copy);
            SimpleDB db = new SimpleDB(copy.toString());
            Transaction tx = db.newTx();
            try {
               if (reference.isEmpty()) {
                  planLog.println(environment);
                  for (String table : Arrays.asList("student", "enroll", "section", "course", "dept")) {
                     Scan scan = new TablePlan(tx, prefix + table, db.mdMgr()).open();
                     int count = 0;
                     try { while (scan.next()) count++; } finally { scan.close(); }
                     planLog.println("Actual rows " + table + "=" + count);
                  }
               }
               // Independent in-memory FK lookup avoids a huge four-way Cartesian reference.
               if (!reference.containsKey(config.sql))
                  reference.put(config.sql, referenceRows(db, tx, prefix, config.id.startsWith("E1_")));
               planLog.println("\n=== " + config.id + " ===\nSQL: " + config.sql
                     + "\nAllowed methods: [" + config.method + "]\nOrder: " + (config.order == null ? "heuristic" : config.order));
               HeuristicQueryPlanner planner = new HeuristicQueryPlanner(db.mdMgr(), EnumSet.of(config.method), config.order);
               long[] times = new long[repetitions];
               for (int round = -warmups; round < repetitions; round++) {
                  long start = System.nanoTime();
                  Plan plan = planner.createPlan(new Parser(config.sql).query(), tx);
                  long planned = System.nanoTime() - start;
                  verifyMethods(plan, config.method, config.id.startsWith("E1_") ? 1 : 3);
                  if (round == -warmups) planLog.println(plan.explain());
                  int buffers = tx.availableBuffs();
                  start = System.nanoTime();
                  List<String> actual = consume(plan);
                  long elapsed = System.nanoTime() - start;
                  Collections.sort(actual); // Validation and printing are outside the timed interval.
                  if (!actual.equals(reference.get(config.sql))) throw new AssertionError(config.id
                        + ": result mismatch; actual rows=" + actual.size()
                        + ", expected rows=" + reference.get(config.sql).size());
                  if (buffers != tx.availableBuffs()) throw new AssertionError(config.id + ": buffer leak");
                  sink ^= actual.hashCode();
                  if (round >= 0) {
                     times[round] = elapsed;
                     csv.printf("%s,%s,%d,%d,%d,%d%n", config.id, config.method, round, planned, elapsed, actual.size());
                  }
               }
               Arrays.sort(times);
               System.out.printf("%-27s median=%9.3f ms  min=%9.3f  p95=%9.3f  rows=%d%n",
                     config.id, median(times) / 1e6, times[0] / 1e6, times[(int)Math.ceil(.95 * times.length) - 1] / 1e6,
                     reference.get(config.sql).size());
               csv.flush(); planLog.flush();
            } finally {
               try { tx.rollback(); } finally { db.fileMgr().close(); }
            }
         }
      }
      System.out.println("Results: " + output.toAbsolutePath());
   }
   private static double median(long[] values) {
      int n = values.length;
      return n % 2 == 1 ? values[n / 2] : (values[n / 2 - 1] / 2.0 + values[n / 2] / 2.0);
   }
   private static void copyDatabase(Path source, Path destination) throws IOException {
      Files.createDirectory(destination);
      try (Stream<Path> files = Files.list(source)) {
         for (Path file : (Iterable<Path>) files::iterator)
            if (Files.isRegularFile(file) && !file.getFileName().toString().startsWith("temp"))
               Files.copy(file, destination.resolve(file.getFileName()));
      }
   }
   private static List<String> referenceRows(SimpleDB db, Transaction tx, String prefix, boolean two) {
      Map<String, Map<String, String>> students = keyed(db, tx, prefix+"student", "sid");
      Map<String, Map<String, String>> enroll = keyed(db, tx, prefix+"enroll", "eid");
      Map<String, Map<String, String>> sections = two ? Collections.emptyMap() : keyed(db, tx, prefix+"section", "sectid");
      Map<String, Map<String, String>> courses = two ? Collections.emptyMap() : keyed(db, tx, prefix+"course", "cid");
      List<String> result = new ArrayList<>();
      for (Map<String,String> e : enroll.values()) {
         Map<String,String> s = students.get(e.get("studentid"));
         if (s == null) continue;
         List<String> values = new ArrayList<>(Arrays.asList(s.get("sid"), s.get("sname"), e.get("eid")));
         if (!two) {
            Map<String,String> t = sections.get(e.get("sectionid"));
            if (t == null) continue;
            Map<String,String> c = courses.get(t.get("courseid"));
            if (c == null) continue;
            values.add(c.get("title")); values.add(t.get("prof"));
         }
         values.add(e.get("grade"));
         StringBuilder row = new StringBuilder();
         for (String value : values) row.append(value.length()).append(':').append(value).append(';');
         result.add(row.toString());
      }
      Collections.sort(result);
      return result;
   }
   private static Map<String, Map<String,String>> keyed(SimpleDB db, Transaction tx, String name, String key) {
      Plan plan = new TablePlan(tx, name, db.mdMgr());
      Scan scan = plan.open();
      Map<String, Map<String,String>> result = new HashMap<>();
      try {
         while (scan.next()) {
            Map<String,String> row = new HashMap<>();
            for (String field : plan.schema().fields()) row.put(field, scan.getVal(field).toString());
            if (result.put(row.get(key), row) != null) throw new IllegalArgumentException("Reference requires unique " + name+"."+key);
         }
      } finally { scan.close(); }
      return result;
   }
   private static List<String> consume(Plan plan) {
      List<String> rows = new ArrayList<>();
      Scan scan = plan.open(); // Includes sort/partition/materialization work.
      try {
         while (scan.next()) {
            StringBuilder row = new StringBuilder();
            for (String field : plan.schema().fields()) {
               String value = scan.getVal(field).toString();
               row.append(value.length()).append(':').append(value).append(';');
            }
            rows.add(row.toString());
         }
      } finally { scan.close(); }
      return rows;
   }
   private static void verifyMethods(Plan plan, JoinMethod method, int expected) {
      int count = countMethods(plan, method);
      if (count != expected) throw new AssertionError("Expected " + expected + " " + method + " joins; found " + count);
   }
   private static int countMethods(Plan plan, JoinMethod method) {
      String name = plan.getClass().getSimpleName();
      Map<String, JoinMethod> names = new HashMap<>();
      names.put("HashJoinPlan", JoinMethod.HASH); names.put("MergeJoinPlan", JoinMethod.MERGE);
      names.put("IndexJoinPlan", JoinMethod.INDEX); names.put("NestedLoopsPlan", JoinMethod.NESTED_LOOP);
      names.put("MultibufferProductPlan", JoinMethod.MULTIBUFFER);
      if (names.containsKey(name) && names.get(name) != method)
         throw new AssertionError("Disallowed join: " + name);
      int result = names.containsKey(name) ? 1 : 0;
      for (Plan child : plan.children()) result += countMethods(child, method);
      return result;
   }
}
