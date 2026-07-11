# 3. The discrete cosine transform

The DCT expresses a spatial 8×8 block as 64 frequencies. Coefficient `(0,0)` is
the average-like **DC** term; the remaining **AC** terms describe progressively
finer changes. The exact forward and inverse equations are in
[T.81 Annex F](https://www.w3.org/Graphics/JPEG/itu-t81.pdf#page=90).

Our reference transform precomputes cosine terms and implements the equation
directly. This is easier to audit than a fast integer DCT and gives tests a clear
oracle. Production codecs commonly replace it with an AAN or SIMD transform.

Loss does not come fundamentally from the DCT. With enough numeric precision the
forward and inverse transforms recover the block; quantization is the deliberate
lossy step.
