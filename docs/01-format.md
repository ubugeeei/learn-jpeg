# 1. The JPEG interchange format

JPEG is both a family of compression processes and a marker-based interchange
format. This project implements the **baseline sequential DCT** process described
by [T.81, Annex F.1](https://www.w3.org/Graphics/JPEG/itu-t81.pdf#page=91).

A codestream begins with `SOI` (`FF D8`) and ends with `EOI` (`FF D9`). Between
them, marker segments define quantization tables (`DQT`), the frame (`SOF0`),
Huffman tables (`DHT`), and a scan (`SOS`). Marker syntax is specified in
T.81 Annex B.

For one-component grayscale data the useful order is:

```text
SOI → APP0 → DQT → SOF0 → DHT(DC) → DHT(AC) → SOS → entropy data → EOI
```

Lengths are unsigned, big-endian 16-bit integers and include the two length
bytes, but never the marker itself. Entropy data is exceptional: an emitted
`FF` byte must be followed by `00` (byte stuffing; T.81 B.1.1.5).
