# Build a JPEG Codec in Scala 3

This book develops a standards-oriented baseline JPEG codec from first
principles. It is written to be read in order; every chapter pairs domain
knowledge with executable Scala.

## Primary references

- [ITU-T T.81 | ISO/IEC 10918-1](https://www.w3.org/Graphics/JPEG/itu-t81.pdf)
- [W3C JPEG overview](https://www.w3.org/Graphics/JPEG/)

T.81 clause and figure references in this book refer to the linked PDF.

## Chapters

1. [The JPEG interchange format](01-format.md)
2. [Samples, level shifting, and 8×8 blocks](02-blocks.md)
3. [The discrete cosine transform](03-dct.md)
4. [Quantization and zig-zag order](04-quantization.md)
5. [Huffman entropy coding](05-huffman.md)
6. [Writing a baseline codestream](06-encoder.md)
7. [Reading defensively](07-decoder.md)
8. [Testing a lossy codec](08-testing.md)
