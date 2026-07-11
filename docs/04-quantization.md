# 4. Quantization and zig-zag order

Quantization divides every DCT coefficient by a table entry and rounds it. Large
divisors suppress detail the eye tends to notice less. The default luminance
table is the example from [T.81 Annex K.1](https://www.w3.org/Graphics/JPEG/itu-t81.pdf#page=143),
not a mandatory table.

```scala
quantized(i) = round(coefficient(i) / table(i))
```

Coefficients are then visited diagonally in the zig-zag order of T.81 Figure A.6.
This clusters zeros near the end, where run-length coding is effective. DQT table
bytes are also serialized in this zig-zag order—an easy interoperability bug.
