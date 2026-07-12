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

## Component planes and sampling factors

For color frames the decoder derives MCU width and height from the maximum
horizontal and vertical sampling factors. Each component receives `H × V` blocks
per MCU and maintains its own DC predictor. A `2×2, 1×1, 1×1` frame is therefore
decoded as four Y blocks followed by one Cb and one Cr block.

Reconstructed component planes have different resolutions. The current baseline
decoder uses nearest-neighbor chroma upsampling when mapping Cb and Cr into output
pixels. This is valid and deterministic, but higher-quality production codecs use
filtered upsampling to reduce block boundaries. Keeping `Plane` explicit makes
that future replacement local.

The interoperability suite decodes a subsampled color JPEG produced by the JDK,
so MCU ordering and table selection are checked against an independent codec.
