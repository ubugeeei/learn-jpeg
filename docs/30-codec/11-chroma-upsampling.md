# Chroma upsampling: rebuilding missing color positions

4:2:2 and 4:2:0 store fewer Cb and Cr samples than Y samples. Decoding must
estimate chroma at every output pixel before converting YCbCr to RGB.

![Nearest and centered bilinear chroma reconstruction](/diagrams/chroma-upsampling.svg)

## Nearest neighbor

Nearest neighbor copies one stored chroma value across its covered pixels. It is
fast and useful when inspecting exact sample ownership, but smooth color gradients
become visible steps.

```text
stored:   40          80
nearest:  40  40      80  80
```

## Centered bilinear interpolation

A downsampled value represents the center of the source pixels that were averaged.
For an output coordinate `x`, the decoder maps back into component space:

```text
componentX = (x + 0.5) × componentScale - 0.5
```

It then blends left/right and top/bottom neighbors according to the fractional
distance. 4:2:2 interpolates horizontally; 4:2:0 interpolates both axes. At image
edges, missing neighbors clamp to the nearest valid component sample.

## Why upsampling cannot restore the original

The encoder averaged several chroma samples into one number. Information was
discarded, so no filter can reconstruct the exact originals. Bilinear interpolation
chooses a smoother plausible transition. More advanced codecs may use fancy
edge-aware filters, but those are quality policy rather than JPEG syntax.

## Public policy

```scala
val options = DecoderOptions(
  chromaUpsampling = ChromaUpsampling.Bilinear
)
```

`Bilinear` is the default. `Nearest` remains available for teaching, debugging,
and applications prioritizing the cheapest reconstruction. Direct byte-array
decoding exposes the same choice through `JpegDecoder.decodeRgb`.

Full-resolution 4:4:4 components have scale one. Bilinear coordinates land exactly
on stored samples, so both policies produce identical pixels.

## Executable evidence

`ColorAndOptionsSuite` encodes a smooth 64×48 gradient as 4:2:0, measures absolute
RGB channel error against the source, and requires centered bilinear to beat
nearest neighbor. A separate 4:4:4 test requires exact equality between policies.
