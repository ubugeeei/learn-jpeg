---
layout: home
title: learn-jpeg
titleTemplate: Build JPEG from one pixel
hero:
  name: learn-jpeg
  text: Build JPEG from one pixel
  tagline: A standards-driven, executable book for implementing a baseline JPEG codec in Scala 3.
  actions:
    - theme: brand
      text: Start with one pixel
      link: /00-introduction/01-why-jpeg
    - theme: alt
      text: View the roadmap
      link: /00-introduction/03-roadmap
features:
  - title: Build, do not merely use
    details: Every abstraction is introduced only when the file format forces us to need it.
  - title: Read the real standard
    details: Chapters point to T.81 clauses, tables, figures, and JFIF color conventions.
  - title: Make mistakes executable
    details: Tests expose byte order, zig-zag, padding, sign extension, and interoperability traps.
---

## What this is

Most JPEG explanations jump from “split into 8×8 blocks” to a finished codec.
That gap hides the interesting engineering: marker lengths, canonical Huffman
codes, MCU ordering, signed magnitude values, edge extension, and the boundary
between JPEG and JFIF.

This book crosses that gap one working program at a time. The final library is
small enough to read, but it is not pseudocode: the JDK's independent JPEG
implementation reads our output, and our decoder reads its grayscale output.

The structure takes inspiration from [The chibivue Book](https://ubugeeei.github.io/chibivue/):
begin with the smallest satisfying result, revisit it with deeper machinery, and
keep the learner oriented inside the finished architecture.

## Current executable milestones

- validated grayscale and RGB raster types;
- reference FDCT/IDCT with level shifting;
- Annex K quantization and conventional quality scaling;
- zig-zag serialization;
- canonical Huffman construction and entropy bit I/O;
- baseline grayscale encoder and defensive decoder;
- baseline 4:4:4 RGB/JFIF encoder;
- ImageIO interoperability and malformed-input tests.

Start at [No image expertise required](00-introduction/00-no-prerequisites.md).
