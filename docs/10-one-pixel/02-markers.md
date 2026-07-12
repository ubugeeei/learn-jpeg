# Markers and segments

A JPEG stream is not a flat struct. It is a sequence of markers, some carrying
length-delimited payloads and one (`SOS`) introducing entropy-coded bytes whose
end is detected by marker rules.

## Minimum useful sequence

```text
SOI → APP0 → DQT → SOF0 → DHT(DC) → DHT(AC) → SOS → data → EOI
```

- **APP0** identifies JFIF conventions. Decoders may skip unknown APP segments.
- **DQT** defines quantization table values in zig-zag order.
- **SOF0** selects baseline sequential DCT and defines the frame/components.
- **DHT** defines canonical Huffman alphabets. DC and AC have distinct classes.
- **SOS** selects components and tables for one scan.

T.81 Annex B defines syntax; Annex F defines the baseline process. The distinction
matters: a syntactically valid SOF2 stream is progressive and cannot be decoded by
pretending its marker was SOF0.

## Why marker writing is centralized

The encoder accepts payload bytes and owns the length calculation:

```scala
private def segment(out: ByteArrayOutputStream, code: Int, payload: Seq[Int]): Unit =
  marker(out, code)
  u16(payload.size + 2).foreach(out.write)
  payload.foreach(out.write)
```

This tiny function embodies three invariants: marker prefix `FF`, big-endian
length, and length including its own two bytes. Repeating those details in five
call sites would create five opportunities for an off-by-two error.

## Entropy data is a different language

Inside a scan, an `FF` data byte is written as `FF 00`. This is byte stuffing.
The zero is not part of the Huffman bitstream; a decoder removes it. An `FF`
followed by a non-zero byte introduces a real marker.

That makes the parser context-sensitive:

```text
outside scan: FF 00 is invalid
inside scan:  FF 00 means data byte FF
inside scan:  FF D9 means EOI
```

## Failure modes worth testing

- length smaller than two;
- length extending beyond the input;
- `00` stuffed outside entropy data;
- unsupported SOF marker;
- SOS before tables or frame metadata;
- EOI before a scan.

Our `Cursor.segment()` creates a child cursor capped at the declared boundary.
This is more robust than checking the parent position after every parser function.
