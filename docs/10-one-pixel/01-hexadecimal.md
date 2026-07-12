# Read hexadecimal without fear

JPEG syntax is specified byte by byte, and hexadecimal keeps bit boundaries
visible. `255` in decimal, `11111111` in binary, and `FF` in hexadecimal are the
same value. Two hexadecimal digits describe exactly one byte.

## Signed Scala bytes

Scala's `Byte` is signed (`-128..127`), but JPEG bytes are unsigned (`0..255`).
Never widen a byte directly when interpreting file syntax:

```scala
val signed: Byte = 0xff.toByte // -1
val unsigned: Int = signed & 0xff // 255
```

The mask retains the low eight bits and clears sign extension. `Cursor.u8()`
centralizes this operation so marker parsing cannot forget it.

## Big-endian 16-bit values

JPEG marker lengths and dimensions use most-significant byte first order:

```scala
def u16(high: Int, low: Int): Int = (high << 8) | low

u16(0x01, 0x2c) // 300
```

A segment length includes its own two length bytes but excludes the two marker
bytes. Therefore a payload of 14 bytes is preceded by length `00 10`, not `00 0E`
and not `00 12`.

## A first byte trace

```text
FF D8             SOI: start of image, no length
FF E0 00 10 ...   APP0: length 16, therefore 14 payload bytes
FF DB 00 43 ...   DQT: length 67, one info byte + 64 entries
...
FF D9             EOI: end of image, no length
```

Standalone markers are exceptions to the length rule. SOI, EOI, restart markers,
and the reserved `01` marker do not carry a length field; see T.81 B.1.1.3.

## Checkpoint

Given `FF C0 00 0B 08 00 08 00 08 01 01 11 00`, identify the marker, segment
length, precision, height, width, component count, and remaining component bytes.
Do this before reading the next chapter; marker parsing is mostly disciplined
accounting.
