# Defensive decoding: every byte is untrusted

A JPEG decoder reads lengths, dimensions, table sizes, sampling factors, and bit
codes chosen by the file. Successful decoding is only half its contract. Invalid
input must fail predictably before causing accidental allocation or runtime errors.

![Validation layers in the defensive decoder](/diagrams/defensive-decoder.svg)

## Layer 1: bound the input

`DecoderOptions.maxInputBytes` stops a stream before parsing. `maxPixels` is
checked immediately after SOF dimensions, before component planes are allocated.
Both use wider arithmetic when multiplying values to avoid integer wraparound.

## Layer 2: child cursors enforce segment lengths

Every length-bearing marker creates a cursor whose limit is the declared segment
end. DQT, DHT, SOF, SOS, Exif, and metadata code cannot accidentally consume the
next marker. A length below two or beyond the source fails immediately.

## Layer 3: validate baseline invariants

The decoder rejects:

- zero frame width or height;
- sample precision other than eight;
- quantization or Huffman table identifiers outside 0–3;
- zero quantizer entries;
- sampling factors outside 1–4;
- more than ten data units in one MCU;
- duplicate component identifiers;
- DC categories above 11 and AC categories above 10;
- illegal baseline AC symbols.

These checks are not arbitrary policy. They are bounds of the baseline process
described in T.81 Annex F and its marker syntax.

## Canonical Huffman validation

A DHT can claim too many codes at a given length. `HuffmanTable` tracks the next
canonical code and rejects a count that exceeds the `2^length` code space. It also
rejects duplicate symbols and the JPEG-forbidden all-one code.

Without this validation, map construction can silently overwrite entries or
create a table no conforming encoder could emit.

## Domain errors, not incidental exceptions

Malformed input is reported as `JpegError`. `IndexOutOfBoundsException`,
`NegativeArraySizeException`, and constructor `require` failures expose internal
implementation details and make callers unable to distinguish corrupt media from
a library bug.

Programmer errors in output-only APIs can still use ordinary argument validation;
the promise applies specifically to bytes crossing the decoder boundary.

## Deterministic mutation regression

`RobustnessSuite` starts with valid grayscale and color streams. At evenly spaced
positions it XORs four masks, then decodes with a strict pixel limit. Each case
must either decode or throw `JpegError`; any other throwable fails with source,
position, and mask.

This is not a replacement for coverage-guided native fuzzing. It is a fast,
reproducible CI net that catches newly exposed exception paths. Dedicated fixtures
also target zero DQT entries, zero dimensions, oversized MCU layouts, invalid DC
symbols, oversubscribed Huffman alphabets, duplicates, and all-one codes.

## Remaining hardening work

The support matrix still lists corpus fuzzing, CPU budgets, and incremental stream
decoding. Those require test infrastructure beyond local byte mutation, but the
parser is structured so failures converge on bounded cursors and `JpegError`.
