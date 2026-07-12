# Glossary

**AC coefficient** — Any DCT coefficient other than `(0,0)`. Describes spatial
variation at a particular horizontal and vertical frequency.

**baseline sequential** — The widely supported 8-bit DCT process identified by
SOF0. Each coefficient is transmitted once in one sequential scan.

**byte stuffing** — In entropy-coded data, insertion of `00` after a data byte
`FF` so it cannot be mistaken for a marker.

**component** — One rectangular sample plane. Grayscale has one; JFIF color
normally uses Y, Cb, and Cr.

**DC coefficient** — DCT coefficient `(0,0)`, proportional to a block's average.
JPEG entropy-codes its difference from the previous block of the same component.

**DHT** — Define Huffman Table marker segment.

**DQT** — Define Quantization Table marker segment.

**EOB** — End of Block AC symbol `00`; every remaining coefficient is zero.

**entropy-coded segment** — Scan data between SOS and the next non-stuffed marker.

**JFIF** — A common interchange convention layered on JPEG, identified by APP0.

**marker** — `FF` followed by a non-zero code byte. Most markers introduce a
length-delimited segment.

**MCU** — Minimum Coded Unit. The smallest interleaved group containing blocks
according to component sampling factors.

**quality** — Encoder policy for deriving quantization tables; not a stored JPEG
metadata field.

**restart marker** — One of RST0–RST7, periodically resetting entropy state and
allowing a decoder to regain synchronization.

**SOF0** — Start of Frame marker selecting baseline sequential DCT.

**SOS** — Start of Scan marker. Selects components and entropy tables, then begins
entropy-coded data.

**ZRL** — Zero Run Length AC symbol `F0`, representing exactly sixteen zeros.
