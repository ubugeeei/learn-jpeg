# 7. Reading defensively

A decoder processes untrusted lengths, table identifiers, and entropy symbols.
Every read therefore goes through a bounded cursor. Segment cursors cannot escape
their declared length, and malformed data becomes `JpegError`, not an accidental
array exception.

The parser skips unknown APP and COM segments, as interoperability requires, but
rejects unsupported coding processes explicitly. In particular SOF2 means
progressive DCT and needs multiple scans plus coefficient refinement; interpreting
it as SOF0 would silently corrupt output. Marker assignments are listed in
[T.81 Table B.1](https://www.w3.org/Graphics/JPEG/itu-t81.pdf#page=40).

Entropy decoding reverses encoding: canonical Huffman lookup, magnitude sign
extension, AC run expansion, inverse zig-zag, dequantization, IDCT, and crop back
to the frame dimensions.
