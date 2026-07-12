# Metadata: APP segments around the compressed image

JPEG reserves sixteen application markers, APP0 through APP15. Their payloads do
not change the DCT coefficients. Conventions layered on JPEG assign meanings to
some of them:

```text
SOI
 ├─ APP0  JFIF version and pixel density
 ├─ APP1  Exif TIFF structure, often including orientation
 ├─ APP2  ICC_PROFILE chunks
 ├─ APPn  application-specific bytes
 ├─ COM   ISO-8859-1 comment bytes
 └─ frame, tables, scan, EOI
```

The coding standard defines how to skip a segment by length. JFIF, Exif, and ICC
define what selected payload bytes mean.

## Inspecting without decoding pixels

`JpegMetadata.inspect` stops after the SOS header. It can therefore inspect a
progressive or otherwise unsupported JPEG process even when this project's pixel
decoder would reject its SOF marker.

```scala
val metadata = JpegMetadata.inspect(bytes)
metadata.exifOrientation // Option[Int]
metadata.iccProfile      // Option[IArray[Byte]]
```

The same bounded-length rules used by the main decoder apply: a segment cannot
read beyond the source array, and malformed lengths become `JpegError`.

## JFIF APP0

An APP0 payload beginning with `JFIF\0` carries version, density unit, X density,
and Y density. Density describes intended physical display or print size; it does
not change pixel dimensions.

## Exif orientation and TIFF endianness

Exif APP1 begins with `Exif\0\0`, followed by a TIFF structure. TIFF may be little
endian (`II`) or big endian (`MM`). The inspector validates byte order, TIFF magic
42, IFD offsets, entry bounds, field type, and count before accepting orientation
tag `0x0112`.

Orientation values 1 through 8 describe flips and quarter-turns. The inspector
reports the value but does not silently rotate pixels. Applying orientation changes
dimensions and coordinate semantics, so it belongs in an explicit image operation.

## ICC profiles span multiple APP2 segments

One JPEG segment has a 16-bit length and cannot contain a large color profile.
ICC uses numbered chunks:

```text
ICC_PROFILE\0 | sequence | total | chunk bytes
```

Markers may appear in any order. The inspector sorts by sequence, requires one of
every number from 1 through total, rejects duplicates, and concatenates only after
the set is complete. Returning partial profile bytes would be worse than returning
no profile because a color-management system could misinterpret them.

## Unknown application data is preserved

`ApplicationSegment` retains marker code and immutable payload for every APPn
segment, including conventions this project does not interpret. This makes the
metadata model forward-compatible and allows future rewrite support without
discarding vendor information.

## Reading a document

```scala
val document = Jpeg.readDocument(path)
document.image    // DecodedImage
document.metadata // JpegMetadata
```

The path or stream is read once under `DecoderOptions.maxInputBytes`; pixel and
metadata parsers consume the same immutable bytes. Caller-owned streams remain
open, while path methods close streams they create.

## Tests as contracts

`MetadataSuite` uses synthetic segments to state behavior directly: both TIFF byte
orders, unknown payload preservation, comments, out-of-order ICC assembly, missing
chunks, duplicate chunks, and combined pixel/metadata reading.
