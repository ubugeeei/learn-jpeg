# 5. Huffman entropy coding

JPEG stores Huffman tables compactly: 16 counts say how many codes have each
length, followed by symbols in canonical order. The decoder regenerates the
codes using [T.81 Annex C](https://www.w3.org/Graphics/JPEG/itu-t81.pdf#page=52).

DC coefficients are differences from the previous block's DC value. Both DC
differences and non-zero AC values are represented by a category (the number of
magnitude bits) plus signed magnitude bits. Negative values use a ones-complement
style mapping: category 3 maps `-7…-4` to bit patterns `000…011`.

AC symbols combine a four-bit zero run with a four-bit category. `00` is EOB
(all remaining coefficients are zero); `F0` is ZRL (exactly 16 zeros). See
[T.81 F.1.2](https://www.w3.org/Graphics/JPEG/itu-t81.pdf#page=92).

Finally, any `FF` produced inside entropy-coded data is followed by `00`. Without
this byte stuffing a parser would mistake image data for a marker.
