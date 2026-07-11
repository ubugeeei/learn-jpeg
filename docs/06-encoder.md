# 6. Writing a baseline codestream

The encoder is a pipeline whose order mirrors [T.81 Figure A.1](https://www.w3.org/Graphics/JPEG/itu-t81.pdf#page=31):

1. split samples into 8×8 blocks and level shift;
2. compute the FDCT;
3. quantize and visit coefficients in zig-zag order;
4. difference-code DC and run-length-code AC;
5. Huffman-code symbols and magnitude bits;
6. stuff entropy bytes and wrap them in marker segments.

The marker writer centralizes the easily confused length rule. `segment` receives
only payload bytes, then writes `payload.length + 2` in big-endian order. SOI and
EOI are standalone markers and therefore bypass it.

This implementation fixes the luminance quantizer and Annex K Huffman tables to
keep the learning surface compact. A quality setting can be added by scaling and
clamping quantizer entries to `1…255`; it must not scale Huffman tables.
