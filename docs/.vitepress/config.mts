import { defineConfig } from "vitepress";

export default defineConfig({
  title: "learn-jpeg",
  description: "Build a baseline JPEG codec from one pixel in Scala 3",
  cleanUrls: true,
  themeConfig: {
    nav: [
      { text: "Book", link: "/00-introduction/01-why-jpeg" },
      { text: "Specification map", link: "/reference/specification-map" },
      { text: "GitHub", link: "https://github.com/ubugeeei/learn-jpeg" }
    ],
    sidebar: [
      {
        text: "0. Introduction",
        items: [
          { text: "No prerequisites", link: "/00-introduction/00-no-prerequisites" },
          { text: "Why build JPEG?", link: "/00-introduction/01-why-jpeg" },
          { text: "Run the project", link: "/00-introduction/02-setup" },
          { text: "Learning roadmap", link: "/00-introduction/03-roadmap" }
        ]
      },
      {
        text: "1. One-pixel JPEG",
        items: [
          { text: "Read hexadecimal", link: "/10-one-pixel/01-hexadecimal" },
          { text: "Markers and segments", link: "/10-one-pixel/02-markers" },
          { text: "Trace a file", link: "/10-one-pixel/03-trace" }
        ]
      },
      {
        text: "2. Compression pipeline",
        items: [
          { text: "Blocks", link: "/02-blocks" },
          { text: "DCT", link: "/03-dct" },
          { text: "Quantization", link: "/04-quantization" },
          { text: "Huffman coding", link: "/05-huffman" }
        ]
      },
      {
        text: "3. Complete codec",
        items: [
          { text: "Encoder", link: "/06-encoder" },
          { text: "Decoder", link: "/07-decoder" },
          { text: "Testing", link: "/08-testing" },
          { text: "Color JPEG", link: "/30-codec/04-color" },
          { text: "Quality", link: "/30-codec/05-quality" },
          { text: "Production I/O", link: "/30-codec/06-production-io" },
          { text: "Restart intervals", link: "/30-codec/07-restart-intervals" },
          { text: "Huffman optimization", link: "/30-codec/08-huffman-optimization" },
          { text: "Metadata", link: "/30-codec/09-metadata" },
          { text: "Defensive decoding", link: "/30-codec/10-defensive-decoding" }
        ]
      },
      {
        text: "Reference",
        items: [
          { text: "Specification map", link: "/reference/specification-map" },
          { text: "Support matrix", link: "/reference/support-matrix" },
          { text: "Glossary", link: "/reference/glossary" }
        ]
      }
    ],
    search: { provider: "local" },
    outline: { level: [2, 3] }
  }
});
