# Quality is a policy, not a JPEG field

A JPEG file never stores “quality 75.” It stores quantization tables. User-facing
quality is an encoder convention that produces those tables, and different
libraries may produce different files for the same number.

## The conventional scaling curve

This project uses the curve popularized by the Independent JPEG Group:

```scala
val factor = if quality < 50 then 5000 / quality else 200 - quality * 2
val scaled = clamp((base * factor + 50) / 100, 1, 255)
```

At quality 50 the T.81 Annex K example table is unchanged. Below 50 the curve
grows rapidly; above 50 it approaches a table of ones. Quality 100 means minimal
quantization with this 8-bit table, not lossless JPEG.

## Why `Quality` is opaque

```scala
opaque type Quality = Int
```

At runtime it is an unboxed integer. At the API boundary it is not interchangeable
with width, height, a coefficient, or an arbitrary `Int`. Construction validates
`1..100` once, while the hot scaling path operates without wrapper allocation.
This is a useful Scala 3 compromise between domain modeling and codec performance.

## File size is emergent

Higher quality usually means more non-zero coefficients, fewer long zero runs,
and therefore more entropy bits. It does not guarantee a strictly larger file for
every tiny input: marker overhead is fixed and Huffman code lengths are discrete.
The test uses a 64×64 textured gradient so the expected trend is observable.

## Design experiment

For an image and qualities `10, 25, 50, 75, 90, 100`, record:

- encoded byte length;
- mean absolute pixel error;
- maximum pixel error;
- number of non-zero quantized AC coefficients.

Plotting these values reveals why a single quality number is an application policy.
A future chapter will add this experiment as an executable command-line tool.
