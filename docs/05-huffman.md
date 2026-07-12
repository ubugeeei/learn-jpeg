# 5. Entropy coding: storing common values with fewer bits

After quantization, a block often looks like “one important DC value, a handful
of AC values, then many zeros.” JPEG prepares those numbers and Huffman-codes the
resulting symbols.

## Step 1: DC differences

Neighboring blocks often have similar average brightness. Instead of storing each
DC coefficient, JPEG stores the difference from the previous DC value of the same
component.

```text
DC values:       52, 55, 54, 60
stored changes:  52,  3, -1,  6
```

Color components have independent predictors. Y changing must not affect the next
Cb difference. T.81 F.1.2.1 defines this rule.

## Step 2: categories and magnitude bits

Huffman symbols do not directly represent every signed integer. They first state
how many bits follow. That bit count is the **category**:

| Value range | Category | Extra bits |
|---|---:|---:|
| 0 | 0 | none |
| -1, 1 | 1 | 1 |
| -3…-2, 2…3 | 2 | 2 |
| -7…-4, 4…7 | 3 | 3 |

Positive values use their ordinary binary form. Negative values use the lower
patterns in a complement-like mapping. `Magnitude.bits` and `Magnitude.value`
are inverses; the test checks every value from -1023 through 1023.

## Step 3: run-length coding AC zeros

An AC symbol packs two four-bit fields:

```text
high four bits: number of preceding zeros
low four bits:  category of the non-zero coefficient
```

Two symbols have special meanings:

- `00` — **EOB**, every remaining coefficient is zero;
- `F0` — **ZRL**, exactly sixteen zeros, with no value after them.

For example, three zeros followed by a category-two value produces symbol `32`,
then two magnitude bits.

## Step 4: canonical Huffman codes

A DHT segment does not list arbitrary bit strings. It stores 16 counts—how many
codes have lengths 1 through 16—followed by symbols in order. Annex C regenerates
the codes deterministically.

This is called **canonical Huffman coding**. Only code lengths and symbol order
need to be stored; encoder and decoder independently derive identical bit strings.

`HuffmanTable` builds both directions:

```scala
table.write(symbol, bitWriter)
val symbol = table.read(bitReader)
```

An unknown symbol on encode and a bit pattern longer than 16 on decode are domain
errors rather than silent corruption.

## Step 5: bits become bytes

Codes are concatenated without byte alignment. At the end of a scan the final
byte is padded with one-bits. If an entropy byte equals `FF`, the writer inserts
`00` after it:

```text
entropy bytes before stuffing:  2A FF 73
bytes in the JPEG file:         2A FF 00 73
```

Without stuffing, `FF 73` could be mistaken for a marker. The decoder removes
the zero before exposing bits. See T.81 B.1.1.5.

## What is and is not optimized

The decoder accepts tables supplied by the file. The encoder currently emits the
Annex K example tables rather than performing a first pass to build image-specific
code lengths. The stream is valid and interoperable, but optimized Huffman table
generation remains a real compression improvement listed in the support matrix.

## Executable checkpoints

`EntropySuite` covers signed magnitude, one-bit padding, `FF 00` stuffing, every
standard DC symbol, and invalid-symbol rejection. Codec tests cover EOB and ZRL
indirectly across smooth and textured blocks.
