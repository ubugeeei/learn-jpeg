# Restart intervals: limiting the damage of corrupted bits

Entropy codes have no fixed boundary between symbols. If one bit is lost, a
decoder may misread everything that follows. JPEG restart markers add periodic,
byte-aligned synchronization points.

## The three moving parts

1. **DRI** (Define Restart Interval) stores a number of MCUs.
2. After that many MCUs, entropy bits are padded to a byte boundary.
3. A marker from RST0 through RST7 is written, then entropy state resets.

```text
DRI = 2 MCUs

SOS → MCU 0 → MCU 1 → pad → RST0 → MCU 2 → MCU 3 → pad → RST1 → MCU 4 → EOI
          predictor state A                 predictor state B
```

The marker sequence wraps after eight:

```text
RST0, RST1, RST2, RST3, RST4, RST5, RST6, RST7, RST0, ...
```

See [T.81 B.2.4.4 and F.2.1.3](https://www.w3.org/Graphics/JPEG/itu-t81.pdf)
for DRI syntax and restart processing.

## Why DC prediction resets

DC coefficients are differences from the previous block. A decoder entering at a
restart marker does not know the previous DC value, so every component predictor
becomes zero. Forgetting this reset keeps the file structurally readable but
shifts the brightness of later MCU groups.

## Byte alignment is part of the contract

RST markers are real markers, not entropy bytes. Before writing one, `BitWriter`
pads its partial byte with one-bits and returns a complete byte sequence. A fresh
writer starts after the marker. `FF` inside entropy remains stuffed as `FF 00`,
while the marker itself is the unstuffed pair `FF D0` through `FF D7`.

## Decoder architecture

`Cursor.entropyUntilEoi` splits the scan into immutable chunks at restart markers.
It validates marker order while parsing. `decodeScan` checks that chunk count
matches the DRI value and MCU count, then creates a fresh `BitReader` and clears
all component predictors at each interval.

This separation prevents marker bytes from leaking into Huffman decoding and
makes an invalid interval fail before an out-of-bounds chunk access.

## Encoder API

```scala
val options = EncoderOptions(
  quality = Quality(85),
  restartInterval = 4
)
val bytes = JpegEncoder.encode(grayImage, options)
```

Zero disables restart markers. Values `1..65535` are legal. Grayscale and all
three color sampling modes share the same MCU-boundary restart writer. The decoder
supports restart intervals for both grayscale and color scans.

## Declarative tests

The grayscale suite checks intervals 1, 2, 4, and 7. The color suite checks
intervals 1, 2, and 5 across 4:4:4, 4:2:2, and 4:2:0. It asserts:

- at least one marker is present;
- marker codes cycle from RST0;
- decoded dimensions and pixels remain valid;
- changing RST0 to RST3 produces a specific `JpegError`.

Color output is also opened by ImageIO so a symmetric restart bug cannot pass only
because our encoder and decoder made the same mistake.
