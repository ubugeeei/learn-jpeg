# Learning roadmap

The implementation grows in observable milestones. Each row is something a
reader can test, inspect, or open with an independent tool.

| Milestone | New idea | Observable result |
|---|---|---|
| 0 | bytes and big-endian values | recognize `FF D8` and `FF D9` |
| 1 | marker segments | trace a JPEG without decoding pixels |
| 2 | validated samples and blocks | tile odd-sized images safely |
| 3 | level shift and FDCT | constant 128 becomes all-zero coefficients |
| 4 | quantization and zig-zag | high frequencies become zero runs |
| 5 | magnitude categories | round-trip every signed coefficient |
| 6 | canonical Huffman codes | rebuild codes from a DHT payload |
| 7 | entropy byte stuffing | emit `FF 00`, never a false marker |
| 8 | grayscale scan | ImageIO opens our first image |
| 9 | bounded parsing | malformed lengths become `JpegError` |
| 10 | inverse pipeline | decode independently produced grayscale JPEGs |
| 11 | quality scaling | trade file size for reconstruction error |
| 12 | RGB to YCbCr | encode interoperable three-component JPEG |
| 13 | chroma subsampling | implement MCU geometry for 4:2:0 ✓ |
| 14 | restart intervals | recover synchronization within a scan |
| 15 | progressive JPEG | retain coefficients across multiple scans |

Milestones 0–13 are represented in the current source and tests. Later rows are
the visible implementation roadmap; the book will not pretend they already work.

## Two passes through the material

The first pass builds a constant grayscale image and prioritizes structural
success. The second pass generalizes every shortcut: arbitrary dimensions,
non-zero coefficients, custom tables, defensive parsing, quality, and color.
This spiral is intentional. Seeing the entire path once makes the deeper version
easier to place.

## Definition of done for a chapter

A chapter is not complete merely because prose exists. It should contain:

- a concrete outcome and prerequisite list;
- links to exact specification clauses or figures;
- bytes, equations, or diagrams that can be checked by hand;
- a Scala fragment matching the repository's concepts;
- at least one failure mode;
- an executable test or an explicit planned test;
- a checkpoint question and a next step.

This definition is the standard against which the older short chapters are being
expanded.
