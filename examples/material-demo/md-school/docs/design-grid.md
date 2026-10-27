# School design grid

How a visual slice of the School app is scored. Look at the bench (see [README](../README.md#the-evaluation-bench)),
score each screen on each criterion from 1 to 5, and write the top three fixes, each naming the
component or token to change. A slice is judged by re-running the bench and comparing scorecards.

Scale: 1 broken or default, 2 weak, 3 acceptable, 4 good, 5 exemplary. Score what is on the stills; only
motion intent is scored from the code.

| Criterion | What a 5 looks like |
| --- | --- |
| Hierarchy | A clear type scale, one focal point per screen, and the eye finds the title, the primary data and the action in that order. |
| Spacing rhythm | Every gap is a multiple of 4 or 8 dp, related items sit closer than unrelated ones, and nothing touches an edge. |
| Accent discipline | One accent colour per screen; tones (success, warning, error) appear only where they carry meaning. |
| Surfaces | Depth comes from tonal surface layers (container roles), not from borders or outlines; no box inside a box. |
| Shape language | Radii come from one scale; expressive shapes are used where they carry meaning (selection, primary action), not as decoration. |
| Density and touch targets | Targets are at least 48 dp, rows are scannable, and the density suits the viewport (tighter on desktop, roomier on phone). |
| State design | Empty, loading and error faces are designed (illustration or skeleton, message, recovery action), not a default blank or a bare string. |
| Dark mode quality | Dark is its own tonal ladder, not an inversion: text contrast holds (AA), accents stay calm, surfaces keep their separation. |
| Adaptive layout | Phone, tablet and desktop each use the space: bar to rail, single pane to list-detail, no stretched phone layout and no empty half-screen. |
| Motion intent | Read from code: motion explains a change (enter, exit, selection, feedback), uses the shared spring or tween tokens, and respects reduced motion. |
| Polish | Alignment on shared baselines, no clipped or ellipsised text that hides meaning, icons from one family at one weight and size. |

## Scorecard template

Copy it per slice, one block per run (`before`, then `after`).

```markdown
### <slice>, <before|after>, bench <dir>

| Screen | Hierarchy | Spacing | Accent | Surfaces | Shape | Density | States | Dark | Adaptive | Motion | Polish | Mean |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| dashboard | | | | | | | | | | | | |
| students | | | | | | | | | | | | |
| grades | | | | | | | | | | | | |
| timetable | | | | | | | | | | | | |
| messages | | | | | | | | | | | | |
| settings | | | | | | | | | | | | |
| student-detail | | | | | | | | | | | | |
| thread | | | | | | | | | | | | |

Overall: <mean of the Mean column, one decimal>

Top 3 fixes (each names the component or token):
1. <criterion, screen>: change `<Component or token>` to <what>.
2. ...
3. ...
```

Rules: a score of 3 or less needs a note saying what is wrong, in the fix list or beside the table;
the overall score is the plain mean; a slice should raise it and lower no criterion.
