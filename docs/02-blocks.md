# 2. Samples, level shifting, and 8×8 blocks

Baseline JPEG accepts 8-bit samples. Before transformation each sample is level
shifted from `0…255` to `-128…127` ([T.81 A.3.1](https://www.w3.org/Graphics/JPEG/itu-t81.pdf#page=33)).
The codec tiles a component into 8×8 data units. Partial units at the right and
bottom are completed by repeating the nearest edge sample.

```scala
val shifted = sample - 128
val blockColumns = (width + 7) / 8
```

`Block` is an opaque type: callers get a compact immutable array without being
able to manufacture a 63-element “block”. `GrayImage` validates dimensions,
sample count, and range once at construction, keeping inner loops honest.
