# Bolt's Journal

## 2026-03-31 - Pre-compiling Regex & sequence iteration in Unidiff parser
**Learning:** Re-instantiating `Regex("^diff --git ", RegexOption.MULTILINE)` on every `UnidiffParser.parse()` call and invoking `block.lines()` allocates unnecessary intermediate `Regex` instances and `List<String>` line collections on every git changeset received during polling or UI rendering.
**Action:** Always extract top-level multiline `Regex` instances to `private val` properties and use `Sequence` (`lineSequence()`) when processing line-by-line text blocks.
