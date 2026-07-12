# Before we begin: no image expertise required

You do **not** need to know signal processing, color science, or compression.
You only need basic Scala expressions, loops, collections, and functions. This
page introduces the small vocabulary used throughout the book.

## An image is a table of numbers

A grayscale image can be treated as graph paper. Every square contains a number:

- `0` means black;
- `255` means white;
- values between them are shades of gray.

![A grayscale image represented as a grid of sample values](/diagrams/sample-grid.svg)

We call one number a **sample**. A color pixel has three samples—red, green, and
blue. “Sample” is not a new mathematical object; it is simply the number stored
for one channel at one position.

## Compression means finding a shorter description

Imagine an 8×8 image where every square is gray. Writing `128` sixty-four times
is wasteful. A shorter description is “all 64 squares are 128.” JPEG uses more
powerful versions of the same idea:

1. describe smooth areas using a few frequency values;
2. discard subtle detail;
3. give common patterns shorter bit codes.

![The JPEG pipeline from pixels to a compressed file](/diagrams/pipeline.svg)

The three stages have different jobs. The DCT changes the description without
intentionally losing information. Quantization deliberately removes detail.
Huffman coding stores what remains using fewer bits.

## Six terms to learn now

| Term | Plain meaning | Everyday analogy |
|---|---|---|
| byte | one small integer from 0 to 255 | one box that holds eight switches |
| pixel | one position in an image | one square of graph paper |
| sample | one channel value at a pixel | the number written in that square |
| block | an 8×8 group of samples | one tile cut from the image |
| encode | turn pixels into JPEG bytes | pack a suitcase |
| decode | turn JPEG bytes back into pixels | unpack the suitcase |

Other terms are introduced only when we need them. You never need to memorize
the glossary before continuing.

## Lossy does not mean random

JPEG is usually **lossy**: decoded pixels may differ slightly from the original.
The loss is controlled. Quantization rounds carefully chosen values so that many
become zero. Quality settings change how aggressively that rounding happens.

```text
original value:       47
divide by 10:          4.7
store rounded value:   5
decode as 5 × 10:     50
```

The reconstructed value is 50 instead of 47. We traded exactness for a number
that is easier to compress.

## A mental model for the whole book

Keep asking three questions:

1. What numbers enter this step?
2. What numbers leave it?
3. Is this step reversible?

Those questions are enough to understand the entire baseline codec. Continue to
[Why build JPEG?](01-why-jpeg.md); unfamiliar abbreviations there now have a
place to land.
