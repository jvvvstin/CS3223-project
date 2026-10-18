"""Write a timing comparison without overwriting historical measurements.

Usage: python3 scripts/compare_join_runs.py NEW_ROOT OLD_ROOT EXPECTED_ROWS
"""
import csv
import statistics
import sys
from pathlib import Path

new_root, old_root = map(Path, sys.argv[1:3])
expected_rows = int(sys.argv[3])


def samples(root):
    groups = {}
    forks = sorted(root.glob('fork-*'))
    if not forks:
        raise ValueError(f'No forks: {root}')
    for fork in forks:
        env = dict(line.split('=', 1) for line in
                   (fork / 'environment.txt').read_text().splitlines() if '=' in line)
        with (fork / 'samples.csv').open() as stream:
            rows = list(csv.DictReader(stream))
        configs = {row['configuration'] for row in rows}
        if len(configs) != 20:
            raise ValueError(f'Incomplete suite: {fork}')
        for config in configs:
            selected = [row for row in rows if row['configuration'] == config]
            if sorted(int(row['round']) for row in selected) != list(range(int(env['Repetitions']))):
                raise ValueError(f'Incomplete repetitions: {fork}/{config}')
            if any(int(row['rows']) != expected_rows for row in selected):
                raise ValueError(f'Incorrect result count: {fork}/{config}')
            groups.setdefault(config, []).extend(int(row['execution_ns']) / 1e6 for row in selected)
    return groups


new, old = samples(new_root), samples(old_root)
lines = [
    'Join experiments after multiway-sort fixes',
    '=========================================',
    'Code revision: b605f19 (experiment branch); Lab 6 fix: 7316d23.',
    f'New measurements: {new_root}; baseline: {old_root}.',
    f'All timed executions validated the full result multiset ({expected_rows} rows).',
    'See fork-*/environment.txt for JVM, warmup, repetition and dataset settings.',
    'Execution includes open, full consumption/serialization and close.',
    'Planning, copying, correctness checks and rollback are excluded.',
    'OS caches were not flushed. These are separate sessions, not a controlled',
    'paired trial: machine load and JVM/cache effects also influence differences.',
    '',
    'Configuration                 Old median  New median     New min     New max     n',
    '                                         (all times in milliseconds)',
]
for name, values in sorted(new.items()):
    lines.append(f'{name:28} {statistics.median(old[name]):10.3f} '
                 f'{statistics.median(values):11.3f} {min(values):11.3f} '
                 f'{max(values):11.3f} {len(values):5}')
lines.extend([
    '', 'Plans (table prefix bench_ on the larger dataset):',
    'E1: student -> enroll; five join methods.',
    'E2 OPT: heuristic order with the named method restriction.',
    'E2 P1: student -> enroll -> section -> course.',
    'E2 P2: course -> section -> enroll -> student.',
    'E2 P3: section -> enroll -> student -> course.',
    'Exact executed trees, updated I/O estimates and SQL: fork-*/plans.txt.',
    'The three fixed plans each test nested loop, hash, merge and multibuffer.',
    '', 'Interpretation:',
    'Sort now uses block-budgeted initial runs and k-way intermediate merges.',
    'Merge joins cap their final sorted cursors at two runs to leave sibling',
    'buffer space. EXPLAIN now includes estimated sorting I/O, not elapsed time.',
    'Hash and index joins do not directly benefit from the sorting change.',
    'Do not attribute changes in their timings solely to the sort optimization.',
    'Old reports and measurements are retained unchanged; their timings describe',
    'the old implementation. This summary does not update the historical PDF.',
])
report = '\n'.join(lines) + '\n'
(new_root / 'summary.txt').write_text(report)
print(report)
