# Why build JPEG?

Open a JPEG and you meet nearly every layer of systems programming in one small
file: binary framing, transforms, lossy numerical methods, canonical codes,
bit-level packing, stateful prediction, color science, and hostile-input parsing.
The reward is immediate—a file that ordinary image viewers can open.

## Our first finish line

We will not begin with the DCT. Our first mental program is smaller:

```text
write SOI → describe one component → encode one block → write EOI
```

An 8×8 constant gray image contains one block. After subtracting 128, an image
whose samples are all 128 becomes 64 zeros. Its DCT, quantized coefficients, DC
difference, and AC tail are all zero. That makes it the JPEG equivalent of
“Hello, World”: the container and entropy machinery remain real, but the math is
temporarily quiet.

## JPEG is not one thing

The word *JPEG* is overloaded:

1. **JPEG coding processes** specify how components are transformed and coded.
2. **JPEG interchange format** specifies marker segments carrying tables, frame
   metadata, scans, and entropy data.
3. **JFIF** establishes common conventions such as YCbCr interpretation and an
   APP0 identification segment.
4. **Exif** is another application-level convention, commonly carrying camera
   metadata and orientation in APP1.

Our core authority is [ITU-T T.81 / ISO/IEC 10918-1](https://www.w3.org/Graphics/JPEG/itu-t81.pdf).
For RGB conversion we use [JFIF 1.02](https://www.w3.org/Graphics/JPEG/jfif3.pdf).
Keeping those responsibilities separate prevents folklore from masquerading as
the format specification.

## Deliberate scope

The executable core implements 8-bit baseline sequential DCT with Huffman coding.
Grayscale works in both directions. RGB encoding currently uses three full-size
YCbCr components (4:4:4). Progressive scans, arithmetic coding, hierarchical mode,
and lossless JPEG are separate processes—not switches hidden in this decoder.

## How to read this book

Every milestone follows the same loop:

1. state the observable result;
2. find the relevant bytes or equation in the specification;
3. introduce the smallest Scala type that makes an invalid state harder to express;
4. write a focused test;
5. connect the local mechanism to the full codec;
6. list the shortcut we will remove in a later milestone.

If you already know signal processing, focus on marker and entropy chapters. If
you know binary formats but not transforms, run the DCT examples by hand. The
[specification map](../reference/specification-map.md) supports non-linear reading.

## Checkpoint

Before continuing, you should be able to explain why “JPEG is lossy” does **not**
mean “the DCT is inherently lossy.” We will make that distinction executable in
the transform tests.
