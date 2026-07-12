# Image-specific Huffman tables

The Annex K Huffman tables work for every baseline image, but they describe an
average workload. A particular image may use a much smaller subset of symbols.
An optimized encoder counts its symbols first and builds shorter codes for the
ones that occur most often.

## Why this needs two passes

```text
pass 1: blocks → DC/AC symbols → frequency histogram
pass 2: histogram → Huffman table → encoded scan
```

The first pass must perform the same DC prediction, zero-run splitting, EOB, and
ZRL decisions as the real writer. Counting raw coefficients would optimize the
wrong alphabet.

## A normal Huffman tree is not enough

The textbook algorithm repeatedly joins the two least-frequent nodes. Its code
lengths can exceed 16 bits for a large, skewed alphabet. JPEG DHT syntax and the
baseline decoder permit at most 16.

`HuffmanOptimizer` therefore performs four stages:

1. add a pseudo-symbol so no real symbol receives the forbidden all-one code;
2. build a deterministic frequency tree;
3. count leaves at each depth;
4. redistribute counts deeper than 16 using T.81 Annex K.2;
5. remove the pseudo-symbol and construct the canonical DHT order.

## Why canonical order still matters

The optimizer does not store tree edges. It returns the same representation used
by a DHT segment: 16 code-length counts and symbols ordered by length. The regular
`HuffmanTable` then derives encoder and decoder maps exactly as it does for tables
read from a file.

## Determinism

Equal-weight nodes are ordered by their smallest symbol. This tie-break makes the
same histogram produce byte-for-byte identical tables across runs. Deterministic
output is useful for reproducible builds and golden tests even though multiple
Huffman trees could have the same cost.

## Executable properties

`HuffmanOptimizerSuite` checks:

- every supplied symbol encodes and decodes;
- counts always contain exactly 16 length buckets;
- one-symbol and 256-symbol alphabets are valid;
- a heavily skewed alphabet never exceeds 16 bits;
- common symbols are not assigned longer codes than rare symbols.

## Prepared scans: avoiding duplicate work

`PreparedScan` performs DCT, quantization, DC prediction, zero-run splitting,
EOB, and ZRL decisions exactly once. It stores entropy tokens grouped by MCU.
Those tokens serve two consumers:

1. frequency maps used by `HuffmanOptimizer`;
2. the final bit writer using the generated tables.

Restart boundaries reset predictors while preparing tokens and split bit writers
while emitting them. Both phases use the same MCU index and interval, so the
histogram describes the symbols that are actually written.

## Public policy and measured effect

`EncoderOptions.optimizeHuffmanTables` defaults to true. Setting it to false emits
the Annex K tables, which is useful for teaching and comparisons. Tests encode a
160×120 grayscale texture and a 128×96 color gradient in 4:4:4, 4:2:2, and 4:2:0.
In every case optimized output is smaller, decodes locally, and remains readable
by ImageIO. Tiny images can be larger after optimization because DHT overhead is
fixed; the option leaves that trade-off under caller control.
