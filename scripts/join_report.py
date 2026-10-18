"""Rebuild the three-page report from the preserved benchmark measurements."""
import csv
import json
import math
import re
import statistics
from collections import defaultdict
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, Preformatted
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "output/joins"
PDF = ROOT / "output/pdf/simpledb-join-experiments.pdf"
samples = defaultdict(list)
forks = defaultdict(dict)
orders = defaultdict(set)
for folder in sorted(DATA.glob("fork-*")):
    local = defaultdict(list)
    with (folder / "samples.csv").open() as stream:
        for row in csv.DictReader(stream):
            assert int(row["rows"]) == 6
            local[row["configuration"]].append(int(row["execution_ns"]) / 1e6)
    for key, values in local.items():
        samples[key].extend(values)
        forks[key][folder.name] = statistics.median(values)
    for section in (folder / "plans.txt").read_text().split("=== ")[1:]:
        key = section.split(" ===")[0]
        orders[key].add(tuple(re.findall(r"TableScan \[([^\]]+)\]", section)))
assert len(samples) == 20 and all(len(v) == 90 for v in samples.values())
assert all(len(v) == 1 for v in orders.values()), "Join order changed across forks"

summary = {}
for key, values in sorted(samples.items()):
    values.sort()
    summary[key] = dict(n=len(values), median_ms=statistics.median(values),
        p95_ms=values[math.ceil(.95 * len(values))-1], min_ms=values[0],
        fork_medians_ms=forks[key], table_order=list(next(iter(orders[key]))))
(DATA / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
lines = ["Configuration                 Median ms    P95 ms    n"]
for key, row in summary.items():
    lines.append(f"{key:29s} {row['median_ms']:9.3f} {row['p95_ms']:9.3f} {row['n']:4d}")
(DATA / "summary.txt").write_text("\n".join(lines) + "\n")
print("\n".join(lines))

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="BodySmall", fontName="Helvetica", fontSize=9.3, leading=13, spaceAfter=7))
styles.add(ParagraphStyle(name="SectionTitle", fontName="Helvetica-Bold", fontSize=13, leading=17,
                         textColor=colors.HexColor("#183b56"), spaceBefore=10, spaceAfter=7))
styles.add(ParagraphStyle(name="ReportTitle", fontName="Helvetica-Bold", fontSize=21, leading=25, spaceAfter=9))
styles.add(ParagraphStyle(name="CodeSmall", fontName="Courier", fontSize=8, leading=11, spaceAfter=8))
story = []
def para(text):
    story.append(Paragraph(text, styles["BodySmall"]))
def heading(text):
    story.append(Paragraph(text, styles["SectionTitle"]))
def code(text):
    story.append(Preformatted(text, styles["CodeSmall"]))
def table(rows, widths):
    cells = [[Paragraph(escape(str(c)), styles["BodySmall"]) for c in row] for row in rows]
    t = Table(cells, colWidths=widths, repeatRows=1, hAlign="LEFT")
    t.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,0), colors.HexColor("#e4edf5")),
        ("VALIGN", (0,0), (-1,-1), "TOP"),
        ("LEFTPADDING", (0,0), (-1,-1), 7), ("RIGHTPADDING", (0,0), (-1,-1), 7),
        ("TOPPADDING", (0,0), (-1,-1), 4), ("BOTTOMPADDING", (0,0), (-1,-1), 3),
        ("LINEBELOW", (0,0), (-1,0), .7, colors.HexColor("#63809a")),
        ("ROWBACKGROUNDS", (0,1), (-1,-1), [colors.white, colors.HexColor("#f5f7fa")])
    ]))
    story.extend([t, Spacer(1,8)])
def fmt(key, value="median_ms"):
    return f"{summary[key][value]:.3f}"
def footer(canvas, doc):
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#536575"))
    canvas.drawString(40, 24, "SimpleDB+ | Local studentdb measurements | 18 October 2026")
    canvas.drawRightString(A4[0]-40, 24, str(doc.page))

story.append(Paragraph("SimpleDB+ join experiments", styles["ReportTitle"]))
para("Measured baseline on the current student database. These are local measurements from one machine, "
     "not results collected from multiple team members.")
heading("Setup and measurement")
para("Database: student=9, enroll=6, section=5, course=6, dept=3 rows. Each query returned six rows. "
     "Platform: macOS 15.7, aarch64, 8 reported processors; OpenJDK 17.0.14. "
     "Database blocks: 400 bytes; buffer pool: 8 blocks. JVM: -Xms256m -Xmx512m.")
para("For each of 20 configurations, three separate JVM runs each performed 10 discarded warm-ups and "
     "30 measured executions: 90 measurements per configuration, 1,800 measured executions total. "
     "Configurations were shuffled using seeds 20261018, 20261019 and 20261020. Each configuration used "
     "its own database copy and transaction; the source database was never opened by SimpleDB.")
para("System.nanoTime measured Plan.open() through full result consumption and Scan.close(). This includes "
     "sorting, partitioning, temporary-table work, and a common row-serialization consumer. Parsing/planning, "
     "database copying/recovery, validation, printing and transaction rollback were excluded. "
     "These are warmed, buffered runs, not cold-disk latency or pure inner-loop CPU measurements.")
heading("Experiment 1: two-table join")
code("select sid, sname, eid, grade\nfrom student, enroll\nwhere sid = studentid")
para("Every algorithm used the same orientation: student as the left/outer input and enroll as the right "
     "input. Each physical plan ends in the same projection. The method restriction is enforced, with no "
     "fallback to a different join.")
labels = {"NESTED_LOOP":"Tuple nested loop", "HASH":"Partitioned hash", "MERGE":"Sort-merge",
          "INDEX":"Index nested loop", "MULTIBUFFER":"Multi-buffer product + filter"}
rows = [["Algorithm", "Median (ms)", "P95 (ms)"]]
for method in ["NESTED_LOOP","INDEX","MERGE","HASH","MULTIBUFFER"]:
    key = "E1_" + method
    rows.append([labels[method], fmt(key), fmt(key,"p95_ms")])
table(rows, [295,110,110])
para("Plans: nested loop tests sid=studentid while scanning; index join probes the existing enroll.studentid "
     "hash index for each student; sort-merge sorts both join keys; hash partitions and probes; multi-buffer "
     "materializes inputs, forms chunked products, then filters equality. Duplicate enrollments are preserved.")

story.append(PageBreak())
heading("Experiment 2: four-table join")
code("select sid, sname, eid, title, prof, grade\nfrom student, enroll, section, course\nwhere sid = studentid and sectionid = sectid\n  and courseid = cid")
para("Notation: S=student, E=enroll, T=section, C=course. All plans are left-deep and apply the same "
     "three equalities as soon as the relevant tables are joined, followed by the same projection.")
heading("Three plans selected by restricting the optimizer")
rows = [["Allowed method", "Actual join order", "Median ms", "P95 ms"]]
abbr = {"student":"S","enroll":"E","section":"T","course":"C"}
for method in ["NESTED_LOOP","HASH","MERGE"]:
    key = "E2_OPT_" + method
    order = [abbr[x] for x in summary[key]["table_order"]]
    shape = f"((({order[0]} + {order[1]}) + {order[2]}) + {order[3]})"
    rows.append([labels[method], shape, fmt(key), fmt(key,"p95_ms")])
table(rows, [150,165,100,100])
para("These three configurations retain heuristic table ordering and allow only the named method. "
     "Their physical trees were checked to contain exactly three joins of that method. "
     "The different estimated output cardinalities also affect the heuristic's next-table choice.")
heading("Controlled comparison: three join orders x four algorithms")
para("P1 = (((S + E) + T) + C); P2 = (((C + T) + E) + S); "
     "P3 = (((T + E) + S) + C). Here table order is fixed and only the allowed join method changes. "
     "This extra comparison separates method effects from optimizer-chosen order.")
rows = [["Order", "Nested loop", "Hash", "Sort-merge", "Multi-buffer"]]
for plan in ["P1","P2","P3"]:
    rows.append([plan] + [fmt("E2_"+plan+"_"+m) for m in ["NESTED_LOOP","HASH","MERGE","MULTIBUFFER"]])
table(rows, [55,115,115,115,115])
para("Entries are median milliseconds over 90 executions. P95 values and each JVM's median are retained "
     "in summary.json; every raw duration is in samples.csv.")
para("Index-only four-table plans are not applicable with the existing indexes: enroll.studentid is indexed, "
     "but section.sectid and course.cid are not. No extra indexes were created just to make that comparison work. "
     "Experiment 1 still measures the available index join directly.")
para("All three orders produce small intermediate results: P1 has 6, 6, 6 joined rows; "
     "P2 has 5, 6, 6; P3 has 6, 6, 6. Record widths and which table is rescanned also differ. "
     "Detailed operator trees, join keys, restrictions and estimates are preserved in each fork's plans.txt.")

story.append(PageBreak())
heading("Discussion and interpretation")
para(f"<b>Two-table result.</b> Tuple nested loop was fastest by pooled median "
     f"({fmt('E1_NESTED_LOOP')} ms), followed by the existing index join "
     f"({fmt('E1_INDEX')} ms). With only 9 x 6 candidate pairs, repeatedly scanning "
     "a tiny buffered table is inexpensive. Index setup/probes add overhead, while partitioning, sorting "
     "and materialization create temporary files. The file manager opens files in Java's synchronous "
     "'rws' mode, making temporary writes relevant even for tiny relations.")
para(f"<b>Four-table result.</b> The restricted nested-loop optimizer plan took "
     f"{fmt('E2_OPT_NESTED_LOOP')} ms versus {fmt('E2_OPT_HASH')} ms for hash and "
     f"{fmt('E2_OPT_MERGE')} ms for merge. This does not demonstrate that nested loop scales better: "
     "it reflects a dataset where the entire useful input is only a few blocks. "
     "Hash and merge pay setup costs at each of the three join stages.")
para("<b>Join order.</b> P2 had the lowest pooled median for fixed-order nested loop, hash and multi-buffer; "
     "P1 had the lowest for merge in this run. P2's first intermediate has five rows rather than six, "
     "but the small differences in size alone cannot establish causality. Sort order, intermediate width, "
     "temporary writes, JVM compilation and system load can all affect the observed timings.")
para("<b>Variability.</b> The same physical plan is sometimes represented both by an optimizer-selected "
     "configuration and a fixed-order configuration. Their timings differ because they ran in separate "
     "blocks at different points in the JVM and system's execution. For example, the hash optimizer plan "
     "has the same T-E-S-C order as P3, yet their pooled medians differ. "
     "Therefore small rankings are descriptive, not statistically established. We retain outliers, "
     "report P95, and do not treat 90 within-process measurements as 90 independent JVM replications.")
para("<b>Planner limitations.</b> EXPLAIN's rows and blocks are estimates, not measured counts or I/O. "
     "The existing distinct-value guesses and repeated selectivity reductions can even estimate zero rows "
     "for this nonempty join. This experiment preserves those estimates rather than changing the optimizer "
     "while measuring it. Actual result multisets were independently validated on every execution.")
heading("Reproduce and extend")
code("javac -d /tmp/simpledb-benchmark-classes $(find src -name '*.java')\njava -Xms256m -Xmx512m -cp /tmp/simpledb-benchmark-classes \\\n  simpledb.test.JoinExperiments studentdb output/joins/new-run \\\n  10 30 20261018")
para("In Eclipse, run JoinExperiments as a Java application with the same five program arguments; "
     "use the project root as the working directory. Supply a new output directory each time. "
     "The runner prints median/min/P95 and saves raw samples, plans and environment information. "
     "Its explicit method restrictions reject unavailable joins instead of silently substituting another method.")
para("For a team submission, add separately labelled teammate/machine measurements if required; none have "
     "been invented here. A useful follow-up is a larger generated student database and repeated trials "
     "under controlled cache conditions. These results support the implementation comparison on the current "
     "studentdb only, not a general ranking of database join algorithms.")
PDF.parent.mkdir(parents=True, exist_ok=True)
SimpleDocTemplate(str(PDF), pagesize=A4, rightMargin=40, leftMargin=40,
                  topMargin=36, bottomMargin=38, title="SimpleDB+ Join Experiments",
                  author="SimpleDB+ project").build(story, onFirstPage=footer, onLaterPages=footer)
pages = len(PdfReader(PDF).pages)
assert pages <= 4, f"Report exceeds the page limit: {pages}"
print(f"Report: {PDF} ({pages} pages)")
