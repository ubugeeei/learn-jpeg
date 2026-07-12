# 4. Quantization: where JPEG deliberately loses detail

The DCT changed representation but did not make many real photographs small.
Quantization rounds frequency coefficients so that unimportant values become zero.
Long runs of zeros are cheap to encode later.

## One coefficient by hand

```text
DCT coefficient:       47
quantizer entry:        10
47 / 10:               4.7
stored coefficient:      5
decoded approximation:  5 × 10 = 50
```

The difference from 47 to 50 is the loss. A larger divisor produces more loss and
usually more zeros.

## A table, not one divisor

Every one of the 64 frequency positions has its own divisor. Low frequencies
usually receive smaller divisors because broad changes are visually important.
High frequencies often receive larger divisors because fine detail can tolerate
more rounding.

T.81 Annex K.1 provides an **example** luminance table. It is not mandated by the
format. This project uses it as a recognizable base table.

```scala
quantized(i) = round(coefficients(i) / table(i))
decoded(i)   = quantized(i) * table(i)
```

## Zig-zag is an ordering, not another transform

The 8×8 coefficients live in row/column order, but JPEG transmits them diagonally
from low to high frequency. This is called **zig-zag order**.

```text
 0 →  1    5 →  6   ...
      ↓  ↗
 2    4    7
 ↓  ↗
 3
```

After quantization, zeros tend to gather near the high-frequency end. Zig-zag
turns that two-dimensional cluster into one long one-dimensional zero tail.
T.81 Figure A.6 defines the exact permutation.

Both scan coefficients and DQT payload entries use zig-zag order. Forgetting the
second case produces files that parse correctly but reconstruct with the wrong
divisor at each frequency.

## Quality is encoder policy

JPEG files store the final table, not “quality 85.” `Quality` scales the example
table using the conventional Independent JPEG Group curve, then clamps every
entry to `1..255`.

- quality 50: base table unchanged;
- quality 100: every entry becomes 1;
- low quality: entries grow and more detail becomes zero.

Quality 100 is still not the separate lossless JPEG process.

## Scala invariants

`Block` ensures every table has exactly 64 entries. `Quality` is an opaque type,
so an arbitrary width or coefficient cannot be passed accidentally as quality.
The quantize and dequantize functions accept tables explicitly, making table
selection visible to callers.

## Executable checkpoints

Tests verify that natural→zig-zag→natural is the identity, quality 50 preserves
Annex K, quality endpoints remain legal, and higher quality generally produces a
larger stream for a textured image.
