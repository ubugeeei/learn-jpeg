# Specification map

This page connects code concepts to their normative or conventional sources.
Page fragments are convenient hints; clause identifiers remain authoritative
across differently paginated copies.

| Concept | Source | Location | Implementation |
|---|---|---|---|
| sample precision and blocks | T.81 | A.3.1, F.1.1 | `model/Block.scala` |
| level shift | T.81 | A.3.1 | `Dct.forward` / `inverse` |
| FDCT and IDCT | T.81 | A.3.3, Annex F | `transform/Transform.scala` |
| zig-zag order | T.81 | Figure A.6 | `Quantization.ZigZag` |
| quantizer syntax | T.81 | B.2.4.1 | DQT writer/parser |
| example quantizers | T.81 | K.1 | `Quantization.Luminance` |
| marker assignments | T.81 | Table B.1 | encoder/decoder marker matches |
| marker segments | T.81 | Annex B | `segment`, `Cursor.segment` |
| byte stuffing | T.81 | B.1.1.5 | `BitWriter`, `entropyUntilEoi` |
| canonical Huffman derivation | T.81 | Annex C | `entropy/HuffmanTable` |
| DC difference coding | T.81 | F.1.2.1 | encoder/decoder scan loops |
| AC run/category coding | T.81 | F.1.2.2 | encoder/decoder scan loops |
| baseline scan fields | T.81 | F.1.1.3 | SOS writer/validation |
| YCbCr conversion | JFIF 1.02 | section 3 | `Color.scala` |
| JFIF APP0 segment | JFIF 1.02 | section 4.1 | encoder APP0 payload |

## Primary documents

- [ITU-T T.81 / ISO/IEC 10918-1](https://www.w3.org/Graphics/JPEG/itu-t81.pdf)
- [JPEG File Interchange Format 1.02](https://www.w3.org/Graphics/JPEG/jfif3.pdf)
- [W3C JPEG resource page](https://www.w3.org/Graphics/JPEG/)

Annex K tables are examples, not required defaults. JFIF is a convention layered
on JPEG, not a synonym for the coding standard. These two distinctions eliminate
many misleading statements found in short JPEG tutorials.
