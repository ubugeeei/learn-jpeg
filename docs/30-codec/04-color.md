# Color JPEG: RGB is not stored directly

JPEG encodes abstract components. JFIF gives the common interpretation: component
1 is luma `Y`, components 2 and 3 are blue- and red-difference chroma `Cb` and
`Cr`. The conversion equations live in JFIF 1.02 section 3, not T.81.

```text
Y  =  0.299000 R + 0.587000 G + 0.114000 B
Cb = -0.168736 R - 0.331264 G + 0.500000 B + 128
Cr =  0.500000 R - 0.418688 G - 0.081312 B + 128
```

## Why separate luma and chroma?

Human vision is generally more sensitive to fine luminance detail than fine
chrominance detail. Once separated, JPEG can quantize chroma differently and
store fewer chroma samples. Our first color milestone deliberately does neither:
it writes all three components at full resolution (4:4:4). This isolates color
conversion and MCU ordering before resampling enters the picture.

## Scala domain types

`Rgb` and `YCbCr` validate the `0..255` boundary when values enter the system.
Their constructors prevent a later DCT from receiving an accidental value such
as 300. Conversion clamps after rounding because extreme floating-point results
can lie just outside the legal interval.

```scala
val red = Rgb(255, 0, 0)
val encoded = YCbCr.fromRgb(red) // approximately (76, 85, 255)
val reconstructed = encoded.toRgb
```

## Interleaved MCU order for 4:4:4

With sampling factors `1×1` for all three components, one minimum coded unit is:

```text
MCU 0: Y block 0, Cb block 0, Cr block 0
MCU 1: Y block 1, Cb block 1, Cr block 1
...
```

DC prediction is independent per component. The encoder therefore holds three
previous-DC values even though all components currently share Huffman table 0.
Sharing is legal; common encoders use separate luminance and chrominance tables
for better compression.

## What 4:2:0 will change

For sampling factors `Y=2×2`, `Cb=1×1`, `Cr=1×1`, an MCU contains six blocks:
four Y, one Cb, and one Cr. Chroma planes must be filtered and downsampled, frame
edge extension happens in MCU geometry, and decoding must upsample chroma. That
is a real architectural step, not a different constant in the current loop.

## Executable checkpoint

`ColorAndOptionsSuite` verifies reference colors, bounded RGB↔YCbCr rounding, and
opens our three-component stream with ImageIO. Interoperability matters here:
round-tripping through the same mistaken conversion would let symmetric bugs pass.
