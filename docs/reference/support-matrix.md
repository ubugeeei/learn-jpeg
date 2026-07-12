# Support matrix: what “complete” means here

JPEG is a family of coding processes, not one algorithm. Claiming to “support
JPEG” without naming the process is misleading. This project targets the common
8-bit, Huffman-coded, sequential DCT interchange profile used by JFIF files.

## Legend

- ✅ implemented and covered by executable tests;
- 🚫 recognized and rejected with `JpegError`;
- ◻ outside the current baseline profile.

## Coding processes

| T.81 process | Marker | Status | Why |
|---|---:|:---:|---|
| Baseline sequential DCT | SOF0 | ✅ | primary project scope |
| Extended sequential DCT | SOF1 | 🚫 | includes precision/features beyond baseline |
| Progressive DCT | SOF2 | 🚫 | requires coefficient storage across scans |
| Lossless sequential | SOF3 | 🚫 | predictive process; no DCT |
| Differential processes | SOF5–7 | 🚫 | hierarchical/differential frames |
| Arithmetic-coded DCT | SOF9–11 | 🚫 | patent-era alternative entropy process |
| Arithmetic-coded lossless | SOF11 | 🚫 | different prediction and entropy coding |
| Differential arithmetic | SOF13–15 | 🚫 | outside baseline profile |

Rejecting a marker is supported behavior: the decoder identifies the process and
returns a domain error instead of producing corrupt pixels.

## Baseline frame and scan features

| Feature | Encode | Decode | Notes |
|---|:---:|:---:|---|
| 8-bit samples | ✅ | ✅ | T.81 baseline precision |
| grayscale, one component | ✅ | ✅ | arbitrary dimensions |
| JFIF YCbCr, three components | ✅ | ✅ | component identifiers 1, 2, 3 |
| 4:4:4 sampling | ✅ | ✅ | full-resolution chroma |
| 4:2:0 sampling | ✅ | ✅ | box-filter encode, centered bilinear decode |
| 4:2:2 sampling | ✅ | ✅ | horizontal box-filter encode |
| chroma upsampling | — | ✅ | centered bilinear default; nearest optional |
| odd and partial MCU edges | ✅ | ✅ | nearest-edge extension |
| one interleaved scan | ✅ | ✅ | common JFIF layout |
| non-interleaved component scans | ◻ | ◻ | legal sequential JPEG, uncommon for JFIF |
| multiple sequential scans | ◻ | ◻ | needs scan-state continuation |
| restart intervals / RST0–7 | ✅ | ✅ | all sampling modes; cyclic marker validation |
| abbreviated table streams | ◻ | ◻ | tables must be present in each file |

## Tables and entropy coding

| Feature | Encode | Decode | Notes |
|---|:---:|:---:|---|
| 8-bit DQT | ✅ | ✅ | Y uses table 0; Cb/Cr use table 1; arbitrary identifiers decode |
| 16-bit DQT | — | 🚫 | not used by baseline 8-bit encoder |
| canonical DHT construction | ✅ | ✅ | T.81 Annex C |
| independent DC/AC selectors | ✅ | ✅ | selectors read from SOS |
| custom input Huffman tables | — | ✅ | DHT payload drives decoder |
| length-limited Huffman optimizer | ✅ | — | Annex K.2 redistribution tested |
| optimized output table integration | ✅ | — | default; Annex K fallback is configurable |
| byte stuffing | ✅ | ✅ | `FF` ↔ `FF 00` within entropy data |
| EOB and ZRL | ✅ | ✅ | zero tail and 16-zero run symbols |
| DC prediction per component | ✅ | ✅ | independent state for Y, Cb, Cr |

## Container and application data

| Feature | Status | Behavior |
|---|:---:|---|
| SOI / EOI | ✅ | required and validated |
| JFIF APP0 | ✅ | encoder emits version 1.01 density metadata |
| unknown APPn | ✅ | safely skipped by bounded length |
| COM | ✅ | safely skipped |
| Exif APP1 metadata | ✅ | raw APP1 rewritten; orientation parsed in II/MM order |
| ICC APP2 profiles | ✅ | chunks validated, assembled, and preserved on rewrite |
| unknown APPn | ✅ | payload bytes and relative metadata order preserved |
| COM comments | ✅ | raw bytes preserved; ISO-8859-1 string view exposed |
| Adobe APP14 / CMYK | ◻ | four-component frames rejected |
| orientation | ✅ | typed 1–8 transforms; explicit `orientedImage` application |

## API and robustness

| Contract | Status | Test |
|---|:---:|---|
| immutable validated raster input | ✅ | model suites |
| filesystem read/write | ✅ | temporary-file round trip |
| caller-owned streams remain open | ✅ | output reuse test |
| maximum compressed bytes | ✅ | bounded stream test |
| maximum decoded pixels | ✅ | forged SOF dimensions test |
| malformed segment lengths | ✅ | bounded cursor behavior |
| external grayscale interoperability | ✅ | JDK ImageIO both directions |
| external subsampled color decode | ✅ | JDK ImageIO 4:2:0 fixture |
| deterministic mutation CI | ✅ | grayscale/color bit flips reject via `JpegError` |
| coverage-guided corpus fuzzing | ◻ | planned hardening infrastructure |
| streaming entropy decode | ◻ | bounded full-input buffering today |

## Honest conclusion

The baseline JFIF path is coherent and interoperable, but the whole T.81 family is
not implemented. The remaining items are not minor flags: progressive decoding,
restart recovery, metadata/color management, and optimized entropy tables each
deserve their own implementation milestones. This table is updated whenever a
feature moves from planned to tested.
