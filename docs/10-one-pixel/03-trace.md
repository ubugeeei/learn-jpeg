# Trace an encoded 8×8 gray image

Create the simplest non-empty source:

```scala
val image = GrayImage(8, 8, Seq.fill(64)(128))
val jpeg = JpegEncoder.encode(image, EncoderOptions(Quality(50)))
```

## What happens to the pixels?

1. Every sample is level-shifted: `128 - 128 = 0`.
2. The FDCT of 64 zeros is 64 zeros.
3. Quantization keeps all coefficients zero.
4. The first DC value is zero, so its difference from the predictor is zero.
5. DC category zero is Huffman-coded.
6. The 63 zero AC coefficients collapse into a single EOB symbol.
7. The last partial entropy byte is padded with one-bits.

The image is visually boring, but it executes every container and entropy layer.

## Inspect the structure

```scala
val hex = jpeg.map(b => f"${b & 0xff}%02X").mkString(" ")
println(hex)
```

Find these boundaries in the output:

| Bytes | Meaning | What to verify |
|---|---|---|
| `FF D8` | SOI | first two bytes |
| `FF E0 00 10` | APP0 | payload begins with `JFIF\0` |
| `FF DB 00 43` | DQT | 65-byte payload |
| `FF C0 00 0B` | SOF0 | 8-bit, 8×8, one component |
| `FF C4` twice | DHT | DC class then AC class |
| `FF DA 00 08` | SOS | one component, baseline spectral fields |
| `FF D9` | EOI | final two bytes |

## Perturb one value

Change one sample from 128 to 129. Many DCT coefficients become small non-zero
values before quantization, but at ordinary quality most round back to zero. This
is the first direct observation of lossy compression: the encoded streams may be
identical even though the source images differ.

At quality 100 every quantizer entry is one, so more of the perturbation survives.
Quality is still not losslessness: floating-point rounding and the 8-bit output
boundary remain.

## Exercise

Encode constant values 0, 32, 128, 200, and 255. Predict the sign and magnitude of
the DC coefficient before looking at the bytes. Then run `CodecSuite`, whose
constant-image test verifies that this special case reconstructs exactly.
