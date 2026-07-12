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

## Integration status

The length-limited optimizer is implemented and tested. Encoder integration is
the next milestone: the encoder will prepare quantized MCU data once, collect DC
and AC symbol histograms with restart resets, emit optimized DHT segments, and
then write the same prepared scan. Until that connection lands, output continues
to use the Annex K tables and the support matrix says so explicitly.
