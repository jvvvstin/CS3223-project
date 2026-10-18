"""Summarize completed large-data runs; deliberately exclude diagnostic smoke runs."""
import csv
import statistics
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'output/joins-scaled')
groups = {}
for fork in sorted(root.glob('fork-*')):
    with (fork / 'samples.csv').open() as stream:
        rows = list(csv.DictReader(stream))
    configs = {row['configuration'] for row in rows}
    if len(configs) != 20:
        raise ValueError(f'{fork}: incomplete suite ({len(configs)}/20 configurations)')
    if any(sum(row['configuration'] == config for row in rows) != 3 for config in configs):
        raise ValueError(f'{fork}: expected exactly three measurements per configuration')
    for row in rows:
        if int(row['rows']) != 4000:
            raise ValueError(f'Wrong row count: {row}')
        groups.setdefault(row['configuration'], []).append(int(row['execution_ns']) / 1e6)
if not groups:
    raise ValueError('No measurements')
lines = [
    'Larger student-schema join experiments',
    '=====================================',
    'Dataset: 1000 students, 4000 enrollments, 200 sections, 50 courses.',
    'Separate database /tmp/simpledb-scaled-1000; bench_* tables; seed 20261018.',
    'Shuffled physical insertion order; 400-byte blocks; eight buffer frames.',
    'Each configuration: one warmup, three measured executions per JVM.',
    'Timing: open + consume/serialize all projected rows + close, including',
    'sorting/partitioning. Planning and correctness checks are excluded.',
    'Every execution returned the correct full multiset of 4000 rows.',
    'One JVM is a preliminary comparison: three samples are not enough for',
    'strong statistical claims. OS caches were not flushed.',
    '',
    'Configuration                 Median ms       Min ms       Max ms     n',
]
for name, values in sorted(groups.items()):
    lines.append(f'{name:28} {statistics.median(values):11.3f} {min(values):12.3f} {max(values):12.3f} {len(values):5}')
lines.extend(['', 'Measured comparisons (median execution time):'])
for experiment in ('E1', 'E2_P1', 'E2_P2', 'E2_P3', 'E2_OPT'):
    times = {name: statistics.median(values) for name, values in groups.items()
             if name.startswith(experiment + '_')}
    fastest = min(times, key=times.get)
    ratio = times[experiment + '_NESTED_LOOP'] / times[experiment + '_HASH']
    lines.append(f'{experiment}: fastest {fastest}; nested/hash time ratio {ratio:.2f}x.')
lines.extend([
    '', 'Plans:',
    'E1: student -> enroll (one join).',
    'E2 OPT: optimizer-selected order, restricted to the named method.',
    'E2 P1: student -> enroll -> section -> course.',
    'E2 P2: course -> section -> enroll -> student.',
    'E2 P3: section -> enroll -> student -> course.',
    'Names above omit bench_. Exact executed trees and SQL: fork-*/plans.txt.',
    'Index-only four-table joins are unavailable with this schema/index set.',
    '', 'Interpretation:',
    'Nested-loop startup is cheap on the original nine-student dataset, but',
    'repeated scans become costly as inputs grow. Hash partitioning has fixed',
    'overhead but can avoid those repeated full scans. Merge includes external',
    'sorting of shuffled input; its benefit depends on ordering and run count.',
    'Index join uses the explicitly maintained enrollment studentid index.',
    'Hash partitions use Java-heap hash tables outside the eight buffer frames;',
    'these are comparisons of these implementations, not equal-memory proofs.',
    'Table order changes intermediate widths/cardinalities and repeated work.',
    'This comparison changes both input size and physical ordering, so it does',
    'not isolate a pure size effect or establish a universal algorithm ranking.',
    '', 'Correctness finding:',
    'The first smoke run detected missing merge-join rows. SortScan restored',
    'record positions but not current-run identity/exhaustion flags. The fix',
    'restores all state; SortReplayTest and all measured result checks pass.',
    'smoke/ is a failed diagnostic and is excluded from this summary.',
    'The original output/pdf/simpledb-join-experiments.pdf covers tiny data only.',
])
report = '\n'.join(lines) + '\n'
(root / 'summary.txt').write_text(report)
print(report)
