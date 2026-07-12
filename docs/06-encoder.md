# 6. Putting the encoder together

The encoder is a pipeline. Each stage consumes one well-defined representation
and produces the next:

```text
RGB pixels
  ↓ convert color
Y, Cb, Cr sample planes
  ↓ optionally average chroma
8×8 component blocks grouped into MCUs
  ↓ level shift and DCT
frequency coefficients
  ↓ quantize and zig-zag
mostly-zero integer sequences
  ↓ DC difference + AC run-length + Huffman
entropy bits
  ↓ byte stuffing and marker segments
JPEG bytes
```

## Grayscale is the first complete path

One-component encoding writes SOI, APP0, DQT, SOF0, DHT, SOS, entropy data, and
EOI. There is one DC predictor and one 8×8 block per MCU because the component's
sampling factors are 1×1.

## Color conversion comes before compression

JFIF interprets three components as Y, Cb, and Cr. Y resembles brightness; Cb and
Cr describe color difference. `YCbCr.fromRgb` applies the JFIF equations before
any block transform.

This separation allows chroma subsampling. Human vision usually tolerates lower
spatial color resolution better than lower brightness resolution.

## 4:4:4 versus 4:2:0

`FullResolution` (4:4:4) stores one block from each component per 8×8 MCU:

```text
MCU: [ Y ][ Cb ][ Cr ]
```

`HalfHorizontal` (4:2:2) averages horizontal pairs. A 16×8 MCU contains two Y
blocks, one Cb block, and one Cr block. `HalfBothAxes` (4:2:0) averages each 2×2
chroma neighborhood. One 16×16 MCU then
contains six blocks in this exact order:

```text
Y top-left     Y top-right
Y bottom-left  Y bottom-right   Cb   Cr
```

The names 4:4:4 and 4:2:0 are conventional ratios, not literal block counts.
In SOF0, Y carries sampling factor 2×2 while Cb and Cr carry 1×1.

## Edge extension

Image dimensions rarely divide evenly by 8 or 16. JPEG still encodes complete
blocks. The encoder repeats the nearest right or bottom sample before transforming
the padded area. SOF0 retains the original dimensions, so the decoder crops the
reconstructed planes.

For 4:2:0, chroma downsampling also handles odd dimensions. A missing neighbor in
the final 2×2 averaging window is replaced by the closest real sample. Tests cover
1×1, 7×5, exact 16×16, and 17×19 images.

## Marker construction

`segment` receives payload bytes, writes `FF` plus the marker code, and writes a
big-endian length equal to `payload size + 2`. SOI and EOI are standalone and do
not use it.

Centralizing this rule prevents the most common container bug: confusing whether
the length includes the marker, the length field, or neither.

## Entropy state

DC prediction is stored per component. Blocks are written in MCU order, not by
finishing the whole Y plane first. For each block:

1. FDCT and quantize;
2. write DC difference category and bits;
3. count AC zero runs;
4. emit ZRL while a run is at least 16;
5. emit run/category plus magnitude for non-zero values;
6. emit EOB if the tail is zero.

## Public policy

`EncoderOptions` currently exposes quality and chroma subsampling. The default is
4:2:0 because it is the practical photographic choice. 4:2:2 preserves vertical
chroma resolution; 4:4:4 preserves color edges in diagrams and screenshots.

## Known encoder limitations

The encoder writes optional restart intervals for grayscale and every color
sampling mode. It does not yet optimize Huffman tables, embed Exif/ICC metadata,
or emit progressive scans. These are recorded explicitly in
the [support matrix](reference/support-matrix.md), not hidden behind a generic
“JPEG supported” claim.
